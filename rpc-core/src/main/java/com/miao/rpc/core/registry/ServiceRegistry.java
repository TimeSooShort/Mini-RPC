package com.miao.rpc.core.registry;

import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import static com.miao.rpc.core.constant.Constant.ZookeeperConstant.ZK_REGISTRY_PATH;
import static com.miao.rpc.core.constant.Constant.ZookeeperConstant.ZK_DATA_PATH;

@Slf4j
public class ServiceRegistry extends ZookeeperClient {

    public ServiceRegistry(String registryAddress) {
        super.connectServer(registryAddress);
    }

    /**
     * 向中心注册自己的地址信息，第一次会先创建/registry节点，地址都注册在该节点下
     */
    public void registry(String data) {
        // 创建永久节点/registry
        if (!exist(ZK_REGISTRY_PATH)) {
            try {
                zooKeeper.create(ZK_REGISTRY_PATH, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);
            } catch (KeeperException | InterruptedException e) {
                log.error("创建/registry节点失败", e);
            }
        }
        createNode(data, ZK_DATA_PATH); // 向中心注册自己的地址信息
    }
}
