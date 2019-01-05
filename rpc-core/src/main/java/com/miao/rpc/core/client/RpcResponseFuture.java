package com.miao.rpc.core.client;

import com.miao.rpc.core.domain.RpcResponse;

/**
 * 异步通知结果类
 */
public class RpcResponseFuture {

    private RpcResponse response;

    public synchronized RpcResponse getResponse() {
        while (response == null) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        return response;
    }

    public synchronized void setResponse(RpcResponse response) {
        this.response = response;
        notifyAll();
    }
}
