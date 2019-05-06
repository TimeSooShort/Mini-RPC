package com.miao.rpc.core.registry;

import com.miao.rpc.core.constant.Constant;
import com.miao.rpc.core.constant.Constant.ZookeeperConstant;
import com.miao.rpc.core.loadBalance.LoadBalance;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ServiceDiscovery extends ZookeeperClient {
    private LoadBalance loadBalance;
    private Thread discoveringThread;

    public ServiceDiscovery(String registryAddress, LoadBalance loadBalance) {
        this.loadBalance = loadBalance;
        super.connectServer(registryAddress);//连接zookeeper
    }

    public String discover(String clientAddress) {
        log.info("discovering...");
        // 线程第一次请求，给/registry节点设置watch触发器，填充本地loadBalance中的地址信息
        if (discoveringThread == null) {
            this.discoveringThread = Thread.currentThread();
            watchNode();
        }
        return loadBalance.get(clientAddress);
    }

    private void watchNode() {
        try {
            List<String> nodeList = zooKeeper.getChildren(ZookeeperConstant.ZK_REGISTRY_PATH, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getType() == Event.EventType.NodeChildrenChanged) {
                        watchNode(); // 一旦服务器地址发生改变，更新客户端本地的地址集合
                    }
                }
            });
            List<String> dataList = new ArrayList<>();// 存储全部的地址
            for (String node : nodeList) {
                // 获取地址
                byte[] bytes = zooKeeper.getData(ZookeeperConstant.ZK_REGISTRY_PATH + "/" + node, false, null);
                dataList.add(new String(bytes, Constant.UTF_8));
            }
            loadBalance.update(dataList);//更新本地地址
            log.info("node data:{}", dataList);
        } catch (InterruptedException | KeeperException e) {
            log.error("", e);
        }
    }
}
