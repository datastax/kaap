package com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks;

import com.datastax.oss.pulsaroperator.NamespacedDaemonThread;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks.client.ZkClientRackClient;
import com.datastax.oss.pulsaroperator.controllers.zookeeper.ZooKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoRackConfig;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.LaunchMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class BookKeeperRackDaemon extends NamespacedDaemonThread<BookKeeperFullSpec> {

    private final KubernetesClient client;
    private final ScheduledExecutorService executorService;
    private final Map<String, ZkClientRackClient> zkClients = new ConcurrentHashMap<>();


    public BookKeeperRackDaemon(KubernetesClient client) {
        this.client = client;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    protected BookKeeperFullSpec getSpec(PulsarClusterSpec clusterSpec) {
        return new BookKeeperFullSpec(clusterSpec.getGlobal(), clusterSpec.getBookkeeper());
    }

    public void triggerSync(String namespace, BookKeeperFullSpec newSpec) {
        final BookKeeperAutoRackConfig autoRackConfig = newSpec.getBookkeeper().getAutoRackConfig();
        final ZkClientRackClient zkClient =
                getZkClientRackClient(namespace, newSpec, autoRackConfig);
        while (true) {
            try {
                new BookKeeperRackMonitor(client, namespace, newSpec, zkClient).internalRun();
                return;
            } catch (Throwable e) {
                log.error("Error while running BookKeeperRackMonitor, retrying", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private ZkClientRackClient getZkClientRackClient(String namespace, BookKeeperFullSpec newSpec,
                                                     BookKeeperAutoRackConfig autoRackConfig) {
        final String zkConnectString = getZkServers(namespace, newSpec);

        if (!autoRackConfig.getEnabled()) {
            final ZkClientRackClient zkClientRackClient = zkClients.remove(zkConnectString);
            if (zkClientRackClient != null) {
                zkClientRackClient.close();
            }
        }

        final ZkClientRackClient zkClient =
                zkClients.computeIfAbsent(zkConnectString, (k) -> new ZkClientRackClient(k));
        return zkClient;
    }

    private String getZkServers(String namespace, BookKeeperFullSpec newSpec) {
        if (LaunchMode.current() == LaunchMode.DEVELOPMENT) {
            return "localhost:%s".formatted(BaseResourcesFactory.isTlsEnabledOnZooKeeper(newSpec.getGlobal()) ?
                    ZooKeeperResourcesFactory.DEFAULT_CLIENT_TLS_PORT :
                    ZooKeeperResourcesFactory.DEFAULT_CLIENT_PORT);
        }
        return BaseResourcesFactory.getZkServers(newSpec.getGlobal(), namespace);
    }


    @Override
    protected List<ScheduledFuture<?>> specChanged(String namespace, BookKeeperFullSpec newSpec,
                                                   PulsarClusterSpec clusterSpec) {

        final BookKeeperAutoRackConfig autoRackConfig = newSpec.getBookkeeper().getAutoRackConfig();
        final ZkClientRackClient zkClient =
                getZkClientRackClient(namespace, newSpec, autoRackConfig);
        if (zkClient == null) {
            return Collections.emptyList();
        }

        return List.of(executorService.scheduleWithFixedDelay(
                new BookKeeperRackMonitor(client, namespace, newSpec, zkClient),
                autoRackConfig.getPeriodMs(), autoRackConfig.getPeriodMs(), TimeUnit.MILLISECONDS));
    }

    @Override
    public void close() {
        super.close();
        executorService.shutdown();
        zkClients.values().forEach(ZkClientRackClient::close);
    }
}
