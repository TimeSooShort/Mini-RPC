package com.miao.rpc.core.registry;

import com.miao.rpc.core.constant.Constant;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import static com.miao.rpc.core.constant.Constant.ZookeeperConstant.ZK_SESSION_TIMEOUT;

@Slf4j
public class ZookeeperClient {

    protected ZooKeeper zooKeeper;

    // 阻塞直到连接成功
    private CountDownLatch latch = new CountDownLatch(1);

    /**
     * 连接ZK服务器
     * @param address 地址
     */
    protected void connectServer(String address) {
        try {
            this.zooKeeper = new ZooKeeper(address, ZK_SESSION_TIMEOUT, event -> {
                if (event.getState() == Watcher.Event.KeeperState.SyncConnected) {
                    if (Watcher.Event.EventType.None == event.getType()) {
                        latch.countDown();
                        log.info("ZK连接成功");
                    }
                }
            });
            log.info("开始连接ZK服务器");
            latch.await();
        } catch (IOException | InterruptedException e) {
            log.error("", e);
        }
    }

    /**
     * 创建节点
     * @param data 数据
     * @param path 路径
     */
    protected void createNode(String data, String path) {
        try {
            byte[] bytes = data.getBytes(Constant.UTF_8);
            zooKeeper.create(path, bytes, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL_SEQUENTIAL);
            log.debug("成功建立数据节点（{} =》{}）", path, data);
        } catch (KeeperException | InterruptedException e) {
            log.error("createNode放生错误",e);
        }
    }

    /**
     * 判断节点是否存在
     * @param path 路劲
     * @return
     */
    protected boolean exist(String path) {
        Stat stat = null;
        try {
            stat = zooKeeper.exists(path, false);
        } catch (KeeperException | InterruptedException e) {
            log.error("", e);
        }
        return stat != null;
    }

    /**
     * 关闭ZK连接
     */
    public void close() {
        try {
            this.zooKeeper.close();
        } catch (InterruptedException e) {
            log.error("ZK close", e);
        }
    }
}
