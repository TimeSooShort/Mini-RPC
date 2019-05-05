package com.miao.rpc.core.client;

import com.miao.rpc.core.domain.RpcResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 异步通知结果类
 */
@Slf4j
public class RpcResponseFuture {

    private RpcResponse response;

    public synchronized RpcResponse getResponse() {
        while (response == null) {
            try {
                wait();
            } catch (InterruptedException e) {
                log.info("客户端请求线程被中断，"+e.toString());
            }
        }
        return response;
    }

    public synchronized void setResponse(RpcResponse response) {
        this.response = response;
        notifyAll();
    }
}
