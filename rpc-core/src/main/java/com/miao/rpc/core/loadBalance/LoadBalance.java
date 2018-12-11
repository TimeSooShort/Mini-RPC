package com.miao.rpc.core.loadBalance;

import java.util.List;

/**
 * 负载均衡算法
 */
public interface LoadBalance {
    String get(String clientAddress);
    void update(List<String> addresses);
}
