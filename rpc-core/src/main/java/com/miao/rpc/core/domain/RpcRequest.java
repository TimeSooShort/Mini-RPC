package com.miao.rpc.core.domain;

import lombok.Data;

@Data
public class RpcRequest {

    private String requestId;
    private String className; // 接口名
    private String methodName;
    private Class<?>[] parameterTypes;
    private Object[] parameters;
}
