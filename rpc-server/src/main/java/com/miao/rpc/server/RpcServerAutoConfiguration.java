package com.miao.rpc.server;

import com.miao.rpc.core.registry.ServiceRegistry;
import com.miao.rpc.core.server.RpcServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RpcServerProperties.class)
@ConditionalOnMissingBean(RpcServer.class)
@Slf4j
public class RpcServerAutoConfiguration {
    @Autowired
    private RpcServerProperties properties;

    @Bean
    public RpcServer rpcServer() {
        log.info("开始初始化RpcServer");
        log.info("properties：{}", properties);
        ServiceRegistry registry = new ServiceRegistry(properties.getRegistryAddress());//连接Zookeeper
        return new RpcServer(properties.getServiceBaseAddress(), registry);//传入实现类路径
    }
}
