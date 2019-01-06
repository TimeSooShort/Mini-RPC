package com.miao.rpc.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rpc")
@Data
public class RpcServerProperties {
    private String registryAddress;
    private String serviceBaseAddress;
}
