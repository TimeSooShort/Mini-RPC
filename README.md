## 简介
这是个简单的 rpc demo,它实现的是这样一种rpc：
服务端与客户端都知道有哪些服务接口，其实现类在服务端，
客户端对接口的调用实际上是向服务端发出请求，服务端分析请求，
调用该服务接口的具体实现，并将执行结果返回给客户端。
## 项目结构

**rpc-core 模块**：功能实现的代码都在该模块中。

**rpc-client模块**依赖rpc-core，包含rpc客户端功能的启动类及配置类，
为了在spring容器启动时完成代理类的注入及链接的建立等初始化工作，
这里启动靠的是SpringBoot 的 factories 机制。

**rpc-server 模块**：依赖rpc-core，rpc 服务端功能的启动类 及 配置类，完成向注册中心注册自己的地址以及监听客户端链接等初始化动作，
启动靠的是SpringBoot 的 factories 机制。

sample 包下是测试用的例子：

**sample/sample-api 模块**：服务接口的暴露

**sample/sample-client 模块**调用服务接口获取结果，依赖rpc-client

**sample/sample-server 模块**：服务接口实现类，依赖 rpc-server

![Image text](https://github.com/TimeSooShort/Mini-RPC/blob/master/img-folder/rpc.JPG?raw=true)

## 客户端如何发起 rpc 请求
客户端对服务接口的调用实际上是向服务端发送请求，将自己要调用的服务接口类型，方法名，参数信息发送到服务端，
服务端解析请求调用实现类，最后将结果返回给客户端。
为客户端接口创建代理类，客户端对接口的调用背后是其代理类发出请求。
这里我利用了Spring的后置处理器 BeanDefinitionRegistryPostProcessor 
来为服务接口类型在客户端创建代理对象。功能的实现涉及到两个类：

```
com.miao.rpc.core.proxy.RpcProxyFactoryBean
com.miao.rpc.core.proxy.RpcProxyFactoryBeanRegistry
```
RpcProxyFactoryBean 实现了 FactoryBean 和 InitializingBean，
我们在 afterPropertiesSet 方法中根据类型创建对应的代理类，
在其 invoke 方法中收集信息发起请求，最后获得结果返回，
getObject 方法返回的就是该代理类对象。

RpcProxyFactoryBeanRegistry 实现了 BeanDefinitionRegistryPostProcessor 接口，
在 postProcessBeanDefinitionRegistry 方法中：扫描指定路径下的所有类，得到其class对象，
查询class对象中是否有@RpcReference注解的字段，为该字段生成RpcProxyFactoryBean类型的beanDefinition，
使其与字段的beanName关联，这样在客户端代码中@Autowired便将代理类注入到了@RpcReference注解的字段，
客户端对服务接口方法的调用，实际上触发了代理类的invoke方法。

这样便实现了客户端 rpc 请求的发送。我在这一篇博客中做了详细分析
[BeanDefinitionRegistryPostProcessor与动态代理配合使用例子](https://blog.csdn.net/sinat_34976604/article/details/88785177)

## 使用Netty通信
#### 信息
客户端与服务端之间通信数据的JavaBean对象存放在 com.miao.rpc.core.domain 中，
该包中有三个类：Message，RpcRequest，RpcResponse。

RpcRequest 封装客户端的请求信息，内部有 6 个字段
```java
    private String requestId; // 请求ID
    private String className; // 请求的服务接口名
    private String methodName; // 请求的方法名
    private Class<?>[] parameterTypes; // 参数类型数组
    private Object[] parameters; // 参数数组
```

RpcResponse 封装任务执行结果，内部有 3 个字段
```java
    private String requestId; // 请求ID
    private Throwable cause; // 异常结果
    private Object result; // 任务结果
	
	// 判断是否出现异常
    public boolean hasError() {
        return cause != null;
    }
```
Message ：我们将 RpcRequest ，RpcResponse 以及 心跳机制要发送的 PING/PONG 信息统一封装成该类，使用 byte type 字段来区分。
#### 客户端与服务端中的handler链
**客户端handler链**：

![Image text](https://github.com/TimeSooShort/Mini-RPC/blob/master/img-folder/client.JPG?raw=true)

IdleStateHandler：心跳机制
LengthFieldPrepender，LengthFieldBasedFrameDecoder ：用于解决黏包和半包问题。
RpcEncoder extends MessageToByteEncoder ：编码器，对 请求/响应 对象进行编码。
RpcDecoder extends ByteToMessageDecoder：解码器，将数据解码后封装成Message对象。
RpcClientHandler extends SimpleChannelInboundHandler\<Message>：处理返回结果，向服务端发送心跳包，捕获异常。

**服务端handler链**与客户端链相同，只是最后的handler是
```java
RpcServerHandler extends SimpleChannelInboundHandler<Message>
```
该类会初始化一个线程池用于执行获取请求结果的任务，这样做是为了不长时间占用channel的线程。
在 channelRead0 方法中处理客户端发来的请求信息。对 IdleStateEvent 事件的处理是关闭该channel。
#### 客户端的失败重连机制
关于重连机制：RpcClientHandler是链中最后一个handler，由它来做异常的捕获，当解析结果时发生异常，
重新发起请求，尝试次数限制为2，超过该限制则重新与服务端建立连接。

**请求重试** 需要本次的请求信息，即RpcRequest对象，所以在请求发起时将信息保存在channel中，异常发生时从channel中
获取信息，调用RpcClientHandler#reExecute(RpcRequest)重新发起请求。同一请求id的异常次数统计，
采用ConcurrentHashMap<String, AtomicInteger>来存储，注意ConcurrentHashMap的contains不是用来判断键值对是否存在的，
应该使用containsKey，这个坑我掉过。

**客户端重连** 重连前先删除 requestID -> RpcResponseFuture 以及 requestID -> 异常次数 映射关系，正常完成时也需要删除
这两个记录还有channel附带的信息数据，注意客户端请求线程此时正在阻塞等待结果，由于我们要重新建立连接，
所以需要唤醒客户端的线程，就是创建一个RpcResponse封装异常信息，通过这样唤醒请求线程，之后关闭旧的channel，注意由于
客户端请求线程已唤醒，它们会不断发送消息，这就需要让它们在新连接未建立前先等待，即在RpcClient#execute方法中对channel的
存活进行判断，重连用到了 guava retryer

## 注册中心

## 负载均衡

## 序列化
