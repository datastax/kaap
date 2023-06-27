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
package com.datastax.oss.k8saap.autoscaler;

import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class AutoscalerDaemon implements AutoCloseable {

    private final KubernetesClient client;
    private final ScheduledExecutorService executorService;
    @Getter
    private final BrokerAutoscalerDaemon brokerAutoscalerDaemon;
    @Getter
    private final BookKeeperAutoscalerDaemon bookKeeperAutoscalerDaemon;

    public AutoscalerDaemon(KubernetesClient client) {
        this.client = client;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.brokerAutoscalerDaemon = new BrokerAutoscalerDaemon(client, executorService);
        this.bookKeeperAutoscalerDaemon = new BookKeeperAutoscalerDaemon(client, executorService);

    }

    @Override
    public void close() {
        brokerAutoscalerDaemon.close();
        bookKeeperAutoscalerDaemon.close();
        executorService.shutdownNow();
    }

}
