package com.miao.rpc.core.loadBalance.impl;

import com.miao.rpc.core.loadBalance.LoadBalance;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RandomLoadBalance implements LoadBalance {

    private volatile List<String> addresses;

    @Override
    public String get(String clientAddress) {
        if (addresses == null || addresses.size() == 0) return null;
        return addresses.get(ThreadLocalRandom.current().nextInt(addresses.size()));
    }

    @Override
    public void update(List<String> addresses) {
        this.addresses = addresses;
    }
}
