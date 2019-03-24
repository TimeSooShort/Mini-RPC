package com.miao.rpc.client;

import com.miao.rpc.core.client.RpcClient;
import com.miao.rpc.core.loadBalance.LoadBalance;
import com.miao.rpc.core.loadBalance.impl.ConsistentHashLoadBalance;
import com.miao.rpc.core.loadBalance.impl.RandomLoadBalance;
import com.miao.rpc.core.proxy.RpcProxyFactoryBeanRegistry;
import com.miao.rpc.core.registry.ServiceDiscovery;
import com.miao.rpc.core.util.PropertityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnMissingBean(RpcProxyFactoryBeanRegistry.class)
@EnableConfigurationProperties(RpcClientProperties.class)
@Slf4j
public class RpcClientAutoConfiguration {
    @Autowired
    private RpcClientProperties properties;
    @Autowired
    private ApplicationContext applicationContext;

    private static RpcClient client; // 为什么为static解释在下面

    @Bean(name = "CONSISTENT_HASH")
    public ConsistentHashLoadBalance consistentHashLoadBalance() {
        return new ConsistentHashLoadBalance();
    }

    @Bean(name = "RANDOM")
    public RandomLoadBalance randomLoadBalance() {
        return new RandomLoadBalance();
    }

    @Bean
    public RpcClient rpcClient() {
        log.info("初始化RpcClient设置discovery");
        LoadBalance loadBalance = applicationContext.getBean(properties.getLoadBalanceStrategy(), LoadBalance.class);
        ServiceDiscovery discovery = new ServiceDiscovery(properties.getRegistryAddress(), loadBalance);
        client.setDiscovery(discovery);
        client.init();
        return client;
    }

    /**
     * 因为RPCProxyFactoryBeanRegistry初始化是在常规bean还没有初始化之前进行的，这就造成连个问题
     * 1，必须去直接读配置文件才能得到basePackage
     * 2，client对象会被赋给本类的成员变量，但本类还未初始化，所以需将client申明为static
     * @return
     */
    @Bean
    public static RpcProxyFactoryBeanRegistry rpcProxyFactoryBeanRegistry(){
        log.info("创建RpcClient实例");
        client = new RpcClient();
        String basePackage = PropertityUtil.getProperty("rpc.serverBasePackage");
        return new RpcProxyFactoryBeanRegistry(basePackage, client);
    }
}