package com.miao.rpc.core.loadBalance.impl;

import com.miao.rpc.core.loadBalance.LoadBalance;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 参考Dubbo的负载均衡算法实现的一致性hash
 * Dubbo之——几种负载均衡算法：https://blog.csdn.net/l1028386804/article/details/72512794
 */
@Slf4j
public class ConsistentHashLoadBalance implements LoadBalance {

    private ConcurrentSkipListMap<Long, String> hashCircle = new ConcurrentSkipListMap<>();
    private List<String> oldAddress = new ArrayList<>();
    private static final int REPLICA_NUMBER = 160; // 每个地址有160个节点分散在circle上

    /**
     * 根据每个客户端随机产生的UUID，以此来获取服务器地址
     * 如果正好有匹配的节点hash，则返回该节点存储的服务器地址
     * 若没则返回第一个大于给hash的节点值
     * @param clientAddress UUID
     * @return
     */
    @Override
    public String get(String clientAddress) {
        if (hashCircle.size() == 0) return null;
        byte[] digest = md5(clientAddress);
        long hash = hash(digest, 0);
        if (!hashCircle.containsKey(hash)) {
             SortedMap<Long, String> tailMap = hashCircle.tailMap(hash);
             hash = tailMap.isEmpty() ? hashCircle.firstKey() : tailMap.firstKey();
        }
        return hashCircle.get(hash);
    }

    @Override
    public synchronized void update(List<String> addresses) {
        if (oldAddress.size() == 0) {
            oldAddress = addresses;
            for (String address : addresses) {
                add(address);
            }
        } else {
            Set<String> intersect = new HashSet<>(addresses);
            intersect.retainAll(oldAddress);
            for (String address : oldAddress) {
                if (!intersect.contains(address)) {
                    remove(address);
                }
            }
            for (String address : addresses) {
                if (!intersect.contains(address)) {
                    add(address);
                }
            }
            oldAddress = addresses;
        }
    }

    private void add(String address) {
        for (int i = 0; i < REPLICA_NUMBER / 4; i++) {
            //digest数组大小为16，这里每四个节点生成一个消息摘要
            // 也就是说一轮循环同一个address在hashCircle中产生四个节点
            // 40轮循环该address共产生160个节点
            byte[] digest = md5(address + i);
            for (int k = 0; k < 4; k++) {
                long m = hash(digest, k);
                hashCircle.put(m, address);
            }
        }
    }

    private void remove(String address) {
        for (int i = 0; i < REPLICA_NUMBER / 4; i++) {
            byte[] digest = md5(address + i);
            for (int j = 0; j < 4; j++) {
                long m = hash(digest, i);
                hashCircle.remove(m);
            }
        }
    }

    private byte[] md5(String value) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        md5.reset();
        byte[] bytes = null;
        try {
            bytes = value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        md5.update(bytes);
        return md5.digest();
    }

    private long hash(byte[] digest, int number) {
        return (((long)(digest[3 + number * 4] & 0xFF) << 24)
                | ((long) (digest[2 + number*4] & 0xFF) << 16)
                | ((long) (digest[2 + number*4] & 0xFF) << 8)
                | (digest[number*4] & 0xFF))
                & 0xFFFFFFFFL;
    }
}
