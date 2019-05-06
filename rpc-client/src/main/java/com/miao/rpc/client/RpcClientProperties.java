package com.miao.rpc.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rpc")
@Data
public class RpcClientProperties {
    private String registryAddress; // 注册中心地址
    private String clientBasePackage; // 请求发起类的包路径，扫描类需要该路径
    private String loadBalanceStrategy; // 负载均衡策略
}
