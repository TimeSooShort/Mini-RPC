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
        super.connectServer(registryAddress);
    }

    public String discover(String clientAddress) {
        log.info("discovering...");
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
                        watchNode();
                    }
                }
            });
            List<String> dataList = new ArrayList<>();
            for (String node : nodeList) {
                byte[] bytes = zooKeeper.getData(ZookeeperConstant.ZK_REGISTRY_PATH + "/" + node, false, null);
                dataList.add(new String(bytes, Constant.UTF_8));
            }
            loadBalance.update(dataList);
            log.info("node data:{}", dataList);
        } catch (InterruptedException | KeeperException e) {
            log.error("", e);
        }
    }
}
