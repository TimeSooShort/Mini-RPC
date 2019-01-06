package com.miao.rpc.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rpc")
@Data
public class RpcClientProperties {
    private String registryAddress;
    private String serverBasePackage;
    private String loadBalanceStrategy;
}
