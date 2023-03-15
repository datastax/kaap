package com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks.client;

import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryForever;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;

@JBossLog
public class ZkClientRackClient implements BkRackClient {
    public static final String BOOKIES_PATH = "/bookies";
    private final CuratorFramework zkClient;

    public ZkClientRackClient(String zkConnectString) {
        log.infof("Creating new zookeeper client for %s", zkConnectString);
        this.zkClient = CuratorFrameworkFactory
                .newClient(zkConnectString, new RetryForever(1000));
        zkClient.start();
    }

    private class ZkNodeOp implements BookiesRackOp {

        private Stat stat;

        @Override
        @SneakyThrows
        public synchronized BookiesRackConfiguration get() {
            if (stat != null) {
                throw new IllegalStateException();
            }
            stat = new Stat();
            final byte[] data = zkClient.getData().storingStatIn(stat).forPath(BOOKIES_PATH);
            if (data.length == 0) {
                return new BookiesRackConfiguration();
            } else {
                return SerializationUtil.readJson(new String(data, StandardCharsets.UTF_8),
                        BookiesRackConfiguration.class);
            }
        }

        @Override
        @SneakyThrows
        public void update(BookiesRackConfiguration newConfig) {
            try {
                zkClient.setData().withVersion(stat.getVersion())
                        .forPath(BOOKIES_PATH, SerializationUtil.writeAsJsonBytes(newConfig));
            } catch (KeeperException.BadVersionException e) {
                // hide stacktrace
                throw new RuntimeException(e.getMessage(), null, false, false){};
            }
        }
    }

    @Override
    public BookiesRackOp newBookiesRackOp() {
        return new ZkNodeOp();
    }

    @Override
    public void close() {
        zkClient.close();
    }
}
