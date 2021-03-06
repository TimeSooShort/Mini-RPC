package com.miao.rpc.core.proxy;

import com.miao.rpc.core.client.RpcClient;
import com.miao.rpc.core.client.RpcResponseFuture;
import com.miao.rpc.core.domain.RpcRequest;
import com.miao.rpc.core.domain.RpcResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * InitializingBean:初始化时afterPropertiesSet被调用，生成interfaceClass类型的代理类
 * 我们实现了一个后置处理器RpcProxyFactoryBeanRegistry，在初始化之前为client与interfaceClass赋值
 * 这里实现InitializingBean，便是在初始化时生成interfaceClass类型的proxy
 *
 * 实现FactoryBean，得到proxy的bean，用于配合后置处理器BeanDefinitionRegistryPostProcessor来实现自定义的bean
 *
 * 根据interfaceClass类型来生成相应的proxy，proxy主要就是发起请求并获得结果
 * 客户端要调用的类的实现不再本地，在服务端，那客户端调用的是什么？就是proxy
 * proxy就是rpc服务暴露接口在客户端的实现类
 */
@Slf4j
public class RpcProxyFactoryBean implements FactoryBean<Object>, InitializingBean {
    private RpcClient client;
    private Class<?> interfaceClass; // 要生成的代理的类型
    private Object proxy;

    @Override
    public Object getObject() throws Exception {
        return proxy;
    }

    @Override
    public Class<?> getObjectType() {
        return interfaceClass;
    }

    @Override
    public boolean isSingleton() {
        return true; // 单例
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.proxy = Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                (proxy, method, args) -> {
                    // 创建并初始化RpcRequest
                    RpcRequest request = new RpcRequest();
                    log.info("调用远程服务：{} {}", method.getDeclaringClass().getName(), method.getName());
                    request.setClassName(method.getDeclaringClass().getName());
                    request.setMethodName(method.getName());
                    // 该请求的标识，之后会根据该标识来获取结果
                    request.setRequestId(UUID.randomUUID().toString());
                    request.setParameters(args);
                    request.setParameterTypes(method.getParameterTypes());
                    // 发送请求，并获得响应
                    RpcResponseFuture responseFuture = client.execute(request);
                    RpcResponse response = responseFuture.getResponse(); // 阻塞
                    log.info("客户端读到响应");
                    // 本次请求在结果分析中产生异常，重新请求后仍未解决，返回异常信息，客户端重新建立连接
                    if (response.hasError()) {
                        return response.getCause().toString();
                    } else {
                        return response.getResult();
                    }
                });
    }

    // 下面两个setxxx方法用于容器的注入使用
    public void setClient(RpcClient client) {
        this.client = client;
    }

    public void setInterfaceClass(Class<?> interfaceClass) {
        this.interfaceClass = interfaceClass;
    }
}
