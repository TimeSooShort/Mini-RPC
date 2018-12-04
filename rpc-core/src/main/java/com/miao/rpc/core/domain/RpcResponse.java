package com.miao.rpc.core.domain;

import lombok.Data;

@Data
public class RpcResponse {

    private String requestId;
    private Throwable error;
    private Object result;
}
