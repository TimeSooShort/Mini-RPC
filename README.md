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

## 运行环境
1，安装Zookeeper。IDE需安装lombok插件
2，修改sample下client与server模块中zookeeper地址的配置信息，默认为127.0.0.1:2181
3，sample-server的启动类ServerApplication配置启动参数，即服务器地址，比如本地的8787端口-127.0.0.1:8787

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

## Client/Server端相关流程
**服务端启动流程**：连接注册中心 -> 初始化：包括配置netty绑定端口，注册服务端地址，得到服务接口与其实现类对象的映射关系。

服务端初始化时利用Spring的扫描工具ClassPathScanningCandidateComponentProvider，扫描指点路径下被@RpcServer注解的类，
它们是服务接口的实现类，存储接口到对象的映射关系，用于服务的发现。

服务发现：我们将服务发现与执行封装成Runnable交给线程池来执行，RpcServerHandler在得到RpcRequest后，连同存储映射关系的map和
ChannelHandlerContext一起构建一个Worker对象，Worker就是一个Runnable，其run方法通过RpcRequest获得请求的服务接口名，
请求方法等信息，由接口名通过map获得其实现类对象，之后利用CGLib反射执行请求方法，最后将结果写到响应对象RpcResponse中。

**客户端启动流程**：代理类对象的创建与注入，连接注册中心 -> 初始化：包括netty的配置，获取服务器地址，连接服务器 -> 发起请求。

客户端请求线程发起请求后会立刻得到一个RpcResponseFuture对象，这利用的是future模式，为了不阻塞客户端线程
```java
public class RpcResponseFuture {

    private RpcResponse response;

    public synchronized RpcResponse getResponse() {
        while (response == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                log.info("客户端请求线程被中断，"+e.toString());
            }
        }
        return response;
    }

    public synchronized void setResponse(RpcResponse response) {
        this.response = response;
        notifyAll();
    }
}
```
客户端解析响应数据得到RpcResponse，再调用该请求的RpcResponseFuture#setResponse存储结果并唤醒阻塞的请求线程。


## 使用Netty通信
### 信息
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
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private byte type;
    private RpcRequest request;
    private RpcResponse response;

    public Message(byte type) {
        this.type = type;
    }

    public static Message buildRequest(RpcRequest request) {
        return new Message(Message.REQUEST, request, null);
    }

    public static Message buildResponse(RpcResponse response) {
        return new Message(Message.RESPONSE, null, response);
    }

    public static final byte PING = 1;
    public static final byte PONG = 1 << 1;
    public static final byte REQUEST = 1 << 2;
    public static final byte RESPONSE = 1 << 3;
    public static final Message PING_MSG = new Message(Message.PING);
    public static final Message PONG_MSG = new Message(Message.PONG);
}
```
### 客户端与服务端中的handler链
**客户端handler链**：

![Image text](https://github.com/TimeSooShort/Mini-RPC/blob/master/img-folder/client.JPG?raw=true)

IdleStateHandler：心跳机制，解析文章：[Netty心跳机制的使用实例](https://blog.csdn.net/sinat_34976604/article/details/88790643)

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
### 客户端的失败重连机制
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
注册中心使用Zookeeper。

zookeeper中会创建一个永久节点/registry，服务器地址都注册在该节点下
子节点的创建
```java
            zooKeeper.create(path, bytes, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL_SEQUENTIAL); // 顺序节点
```
path 是/registry/data，采用顺序节点让zookeeper自己在该path后添加计数。

项目中涉及zookeeper的类有三个，关系如下图

ZookeeperClient：有 连接，创建节点，判断路径是否存在，关闭，四个方法

ServiceRegistry：构造器中连接zookeeper，服务端容器启动时创建该对象，完成注册中心的链接，
之后初始化时调用registry向中心注册自己的地址。

ServiceDiscovery：构造器中连接zookeeper，目的是客户端容器启动时完成注册中心的连接，之后客户端调用discover获取服务器地址，
初次调用该方法会从中心获取全部的服务器地址存储在本地(这与loadBalance有关)，给/registry节点设置watch触发器，监听/registry
子节点的增删情况，服务器增减后通知客户端，更新客户端本地存储的地址信息。

## 负载均衡
首先关于loadBalance：从zookeeper中获得地址列表，构成节点储存在map中，客户端就是从map中获取服务器地址的，
也就是说我们将地址存储在了本地客户端，这种方式可能导致客户端获得的地址是无效的，因为相应地址的服务器已下线，
而地址信息还未及时更新，这里我采取的方式是在客户端连接远程时失败重连，利用guava retry，当连接出错旧多尝试几次。

这里来说说com.miao.rpc.core.loadBalance.impl.ConsistentHashLoadBalance类，这是参考Dubbo的负载均衡算法实现的一致性hash，
一个地址构成20个节点分散在circle圆上，这里circle用map来实现，节点插入如下
```java
        for (int i = 0; i < 20; i++) {
            // 5轮循环，每次产生一个新的digest数组，一个数组产生4个hash，存储 hash->address 映射
            // 这样就将一个地址尽量均匀的分散在circle的20处位置上
            byte[] digest = md5(address + i);//经MD5加密得到一个byte[]数组
            for (int k = 0; k < 4; k++) {
                long m = hash(digest, k);
                hashCircle.put(m, address);//插入circle圆中
            }
        }
```
hash算法如下
```java
    private long hash(byte[] digest, int number) {
        return (((long)(digest[3 + number * 4] & 0xFF) << 24)
                | ((long) (digest[2 + number*4] & 0xFF) << 16)
                | ((long) (digest[1 + number*4] & 0xFF) << 8)
                | (digest[number*4] & 0xFF))
                & 0xFFFFFFFFL;
    }
```

**关于线程安全**
给/registry节点设置watch触发器，监听子节点的增/删事件，当事件发生也就是有服务器增加或删除，触发器会调用loadBalance的update
方法，更新客户端本地存储的地址，当然这样仍然存在客户端获得失效地址的情况，该情况由客户端程序来处理，这里采用连接失败重试。
所以update方法不存在竞争，也就不需要用锁保护，不过需要确保更新后地址的可见性，所以map使用并发容器ConcurrentSkipListMap。

关于更新操作：多数情况可能是部分服务器的增加或下线，所以在更新本地的地址时应确保不影响不变部分。这里我们使用一个列表
oldList来存储上次更新时的全部地址，在本次更新中与新的地址列表进行比较，删除失效的地址添加新地址。
```java
    public void update(List<String> addresses) {
        if (oldAddress.size() == 0) {
            oldAddress = addresses;
            for (String address : addresses) {
                add(address);
            }
        } else {
            // 新旧地址集合取交集，这部分不用删除
            Set<String> intersect = new HashSet<>(addresses);
            intersect.retainAll(oldAddress); // 取交集
            for (String address : oldAddress) {
                if (!intersect.contains(address)) {
                    remove(address);
                }
            }
            // 将新地址集合中剩余部分添加进circle
            for (String address : addresses) {
                if (!intersect.contains(address)) {
                    add(address);
                }
            }
            oldAddress = addresses;
        }
    }
```
这里oldAddress是被volatile修饰的，由于我们并不会对列表中的元素进行操作，每次直接替换列表对象，所以使用volatile即可保证安全。
## 序列化
使用Protostuff。[Protostuff序列化框架的使用及Objenesis的使用](https://blog.csdn.net/sinat_34976604/article/details/88789283)