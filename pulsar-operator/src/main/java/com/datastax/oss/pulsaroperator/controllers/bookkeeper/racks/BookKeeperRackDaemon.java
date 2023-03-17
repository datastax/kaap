/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks;

import com.datastax.oss.pulsaroperator.NamespacedDaemonThread;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks.client.ZkClientRackClient;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoRackConfig;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.LaunchMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
                } catch (InterruptedException interruptedException2) {
                    Thread.currentThread().interrupt();
                    return;
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
                zkClients.computeIfAbsent(zkConnectString,
                        (k) -> newZkRackClient(k, newSpec.getGlobalSpec(), namespace));
        return zkClient;
    }

    private ZkClientRackClient newZkRackClient(String zkConnectString, GlobalSpec globalSpec, String namespace) {
        final boolean tlsEnabledOnZooKeeper = BaseResourcesFactory.isTlsEnabledOnZooKeeper(globalSpec);
        if (!tlsEnabledOnZooKeeper) {
            return ZkClientRackClient.plainClient(zkConnectString);
        }
        final String tlsSecretNameForZookeeper = BaseResourcesFactory.getTlsSecretNameForZookeeper(globalSpec);
        final Secret secret = client.secrets()
                .inNamespace(namespace)
                .withName(tlsSecretNameForZookeeper)
                .get();
        if (secret == null) {
            throw new IllegalStateException(
                    "Cannot create ssl client for Zookeeper, secret '" + tlsSecretNameForZookeeper + "' not found");
        }

        final String privateKey =
                new String(Base64.getDecoder().decode(secret.getData().get("tls.key")), StandardCharsets.UTF_8);
        final String serverCert =
                new String(Base64.getDecoder().decode(secret.getData().get("tls.crt")), StandardCharsets.UTF_8);
        String caCert = secret.getData().get("ca.crt");
        if (caCert != null) {
            caCert = new String(Base64.getDecoder().decode(caCert), StandardCharsets.UTF_8);
        }
        return ZkClientRackClient.sslClient(zkConnectString, privateKey, serverCert, caCert);
    }

    private String getZkServers(String namespace, BookKeeperFullSpec newSpec) {
        if (LaunchMode.current() == LaunchMode.DEVELOPMENT) {
            // tls can't work in dev mode because in order to perform hostname validation you have to connect to the
            // real hostname
            return "localhost:2181";
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
