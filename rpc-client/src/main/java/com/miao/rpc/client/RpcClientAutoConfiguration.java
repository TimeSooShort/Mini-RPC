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

    // 为什么为static? 因为下面的rpcProxyFactoryBeanRegistry方法中要用到client
    // 而该方法必须是static的，因为在@Configuration的类里，若@Bean标注的方法的
    // 返回类型是BeanDefinitionRegistryPostProcessor，则该方法必须是static的
    // https://github.com/ulisesbocchio/jasypt-spring-boot/issues/45
    //https://stackoverflow.com/questions/41939494/springboot-cannot-enhance-configuration-bean-definition-beannameplaceholderreg
    private static RpcClient client = new RpcClient();

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
     * Cannot enhance @Configuration bean definition 'com.miao.rpc.client.RpcClientAutoConfiguration'
     * since its singleton instance has been created too early.
     * The typical cause is a non-static @Bean method with a BeanDefinitionRegistryPostProcessor return type:
     * Consider declaring such methods as 'static'.
     *
     * 在@Configuration的类里，若@Bean标注的方法的返回类型是BeanDefinitionRegistryPostProcessor，则该方法必须是static的
     */
    @Bean
    public static RpcProxyFactoryBeanRegistry rpcProxyFactoryBeanRegistry(){
        log.info("创建RpcClient实例");
        // 这里只能直接去property文件中获取，因为此时配置文件对象还未注入
        String basePackage = PropertityUtil.getProperty("rpc.serverBasePackage");
        return new RpcProxyFactoryBeanRegistry(basePackage, client);
    }
}
