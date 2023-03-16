package com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks.client;

import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryForever;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.data.Stat;

@JBossLog
public class ZkClientRackClient implements BkRackClient {
    public static final String BOOKIES_PATH = "/bookies";
    private final CuratorFramework zkClient;

    public ZkClientRackClient(String zkConnectString) {
        log.infof("Creating new zookeeper client for %s", zkConnectString);
        final ZKClientConfig zkClientConfig = new ZKClientConfig();
        // TODO: ZK TLD
        // 1. load cert, ca from secrets
        // 2. create keystore and truststore programmatically: https://www.linkedin.com/pulse/creating-java-keystore-programmatically-bill-young/
        zkClientConfig.setProperty("zookeeper.sasl.client", "false");
        zkClientConfig.setProperty("zookeeper.clientCnxnSocket", "org.apache.zookeeper.ClientCnxnSocketNetty");
        zkClientConfig.setProperty("zookeeper.client.secure", "true");
        zkClientConfig.setProperty("zookeeper.ssl.keyStore.location", keyStoreLocation);
        zkClientConfig.setProperty("zookeeper.ssl.keyStore.passwordPath", "/pulsar/keystoreSecret.txt");
        zkClientConfig.setProperty("zookeeper.ssl.trustStore.location", trustStoreLocation);
        zkClientConfig.setProperty("zookeeper.ssl.trustStore.passwordPath", "/pulsar/keystoreSecret.txt");
        zkClientConfig.setProperty("zookeeper.ssl.hostnameVerification", "true");
        this.zkClient = CuratorFrameworkFactory
                .newClient(zkConnectString, 60_000, 15_000, new RetryForever(1000), );
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
                log.infof("No bookies rack configuration found");
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
