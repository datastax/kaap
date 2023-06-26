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
package com.datastax.oss.k8saap.controllers.bookkeeper.racks;

import com.datastax.oss.k8saap.NamespacedDaemonThread;
import com.datastax.oss.k8saap.controllers.bookkeeper.racks.client.BkRackClient;
import com.datastax.oss.k8saap.controllers.bookkeeper.racks.client.BkRackClientFactory;
import com.datastax.oss.k8saap.crds.bookkeeper.BookKeeperAutoRackConfig;
import com.datastax.oss.k8saap.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.k8saap.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class BookKeeperRackDaemon extends NamespacedDaemonThread<BookKeeperFullSpec> {

    private final KubernetesClient client;
    private final ScheduledExecutorService executorService;
    private final BkRackClientFactory bkRackClientFactory;

    public BookKeeperRackDaemon(KubernetesClient client, BkRackClientFactory bkRackClientFactory) {
        this.client = client;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.bkRackClientFactory = bkRackClientFactory;
    }

    @Override
    protected BookKeeperFullSpec getSpec(PulsarClusterSpec clusterSpec) {
        return new BookKeeperFullSpec(clusterSpec.getGlobal(), clusterSpec.getBookkeeper());
    }

    public void triggerSync(String namespace, BookKeeperFullSpec newSpec) {
        final BookKeeperAutoRackConfig autoRackConfig = newSpec.getBookkeeper().getAutoRackConfig();
        final BkRackClient zkClient =
                bkRackClientFactory.newBkRackClient(namespace, newSpec, autoRackConfig);
        if (zkClient == null) {
            return;
        }
        new BookKeeperRackMonitor(client, namespace, newSpec, zkClient).internalRun();
    }


    @Override
    protected List<ScheduledFuture<?>> specChanged(String namespace, BookKeeperFullSpec newSpec,
                                                   PulsarClusterSpec clusterSpec) {

        final BookKeeperAutoRackConfig autoRackConfig = newSpec.getBookkeeper().getAutoRackConfig();
        final BkRackClient bkRackClient =
                bkRackClientFactory.newBkRackClient(namespace, newSpec, autoRackConfig);
        if (bkRackClient == null) {
            return Collections.emptyList();
        }

        return List.of(executorService.scheduleWithFixedDelay(
                new BookKeeperRackMonitor(client, namespace, newSpec, bkRackClient),
                autoRackConfig.getPeriodMs(), autoRackConfig.getPeriodMs(), TimeUnit.MILLISECONDS));
    }

    @Override
    public void close() {
        super.close();
        executorService.shutdown();
    }
}
