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
 *
 * 首先关于loadBalance：从zookeeper中获得地址列表，构成节点储存在map中，客户端就是从map中获取服务器地址的，
 * 也就是说我们将地址存储在了本地客户端，这种方式可能导致客户端获得的地址是无效的，因为相应地址的服务器已下线，
 * 而地址信息还未及时更新，这里我采取的方式是在客户端连接远程时失败重连，利用guava retry，当连接出错旧多尝试几次。
 *
 * 关于线程安全：
 * 1，update方法是watch机制触发的，不存在竞争所以没有用锁保护。
 * 给/registry节点设置watch触发器，监控子节点增加或删除(对应服务器地址增加/删除)，
 * 事件的处理方式：获取最新地址列表，调用loadBalance的update更新地址节点。
 * 因此我用ConcurrentSkipListMap，确保更新后地址的及时获取。
 * 2，oldAddress 是volatile的，这就足够了因为我们不会对该列表中的元素进行操作，而是直接替换对象。
 */
@Slf4j
public class ConsistentHashLoadBalance implements LoadBalance {

    private static final ConcurrentSkipListMap<Long, String> hashCircle = new ConcurrentSkipListMap<>();//顺序+安全的需求
    private volatile List<String> oldAddress = new ArrayList<>();//上次更新的地址列表
    private static final int REPLICA_NUMBER = 20; // 每个地址有20个点分散在circle上

    /**
     * 根据每个客户端的ID计算hash来获取服务器地址，
     * 如果正好有匹配的节点hash，则返回该节点存储的服务器地址，
     * 若没则返回第一个大于给hash的节点值
     */
    @Override
    public String get(String clientAddress) {
        byte[] digest = md5(clientAddress);
        long hash = hash(digest, 0);
        Map.Entry<Long, String> ceil = hashCircle.ceilingEntry(hash);
        return ceil == null ? hashCircle.firstEntry().getValue() : ceil.getValue();
    }

    /**
     * 更新地址，oldAddress是上次更新的地址节点，本次更新时先取
     */
    @Override
    public void update(List<String> addresses) {
        if (oldAddress.size() == 0) {
            oldAddress = addresses;
            for (String address : addresses) {
                add(address);
            }
        } else {
            // 新旧地址集合取交集，这部分不用删除
            Set<String> intersect = new HashSet<>(addresses);
            intersect.retainAll(oldAddress); // 取交集
            for (String address : oldAddress) {
                if (!intersect.contains(address)) {
                    remove(address);
                }
            }
            // 将新地址集合中剩余部分添加进circle
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
            // 5轮循环，每次产生一个新的digest数组，一个数组产生4个hash，存储 hash->address 映射
            // 这样就将一个地址尽量均匀的分散在circle的20处位置上
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

    // digest数组按顺序每四位组成一个hash值
    private long hash(byte[] digest, int number) {
        return (((long)(digest[3 + number * 4] & 0xFF) << 24)
                | ((long) (digest[2 + number*4] & 0xFF) << 16)
                | ((long) (digest[1 + number*4] & 0xFF) << 8)
                | (digest[number*4] & 0xFF))
                & 0xFFFFFFFFL;
    }
}
