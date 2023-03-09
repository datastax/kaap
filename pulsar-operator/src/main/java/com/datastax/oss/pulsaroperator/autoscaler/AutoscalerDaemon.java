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
package com.datastax.oss.pulsaroperator.autoscaler;

import com.datastax.oss.pulsaroperator.controllers.broker.BrokerController;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class AutoscalerDaemon implements AutoCloseable {

    private final KubernetesClient client;
    private final ScheduledExecutorService executorService;
    private final List<ScheduledFuture<?>> brokerSetAutoscalerTasks = new ArrayList<>();
    private ScheduledFuture<?> bkAutoscaler;
    private final Map<String, NamespaceContext> namespaces = new HashMap<>();

    @Data
    private static class NamespaceContext {
        private Map<String, BrokerAutoscalerSpec> currentBrokerAutoscalerSpecs;
        private BookKeeperAutoscalerSpec currentBkSpec;

        private boolean brokerSpecChanged(Map<String, BrokerAutoscalerSpec> spec) {
            if (currentBrokerAutoscalerSpecs != null
                    && spec != null
                    && Objects.equals(spec, currentBrokerAutoscalerSpecs)) {
                return false;
            }
            return true;
        }

        private boolean bkSpecChanged(BookKeeperAutoscalerSpec spec) {
            if (currentBkSpec != null
                    && spec != null
                    && Objects.equals(spec, currentBkSpec)) {
                return false;
            }
            return true;
        }
    }

    public AutoscalerDaemon(KubernetesClient client) {
        this.client = client;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void onSpecChange(PulsarClusterSpec clusterSpec, String namespace) {
        final NamespaceContext namespaceContext = namespaces.getOrDefault(namespace,
                new NamespaceContext());
        Map<String, BrokerAutoscalerSpec> allBrokerAutoscalerSpecs = null;
        if (clusterSpec.getBroker() != null) {
            allBrokerAutoscalerSpecs = getBrokerAutoscalerSpecs(clusterSpec);
            if (namespaceContext.brokerSpecChanged(allBrokerAutoscalerSpecs)) {
                cancelBrokerAutoscalerTasks();
                for (Map.Entry<String, BrokerAutoscalerSpec> brokerSetAutoscalers :
                        allBrokerAutoscalerSpecs.entrySet()) {
                    final BrokerAutoscalerSpec spec = brokerSetAutoscalers.getValue();
                    if (spec.getEnabled()) {
                        final String brokerSetName = brokerSetAutoscalers.getKey();
                        log.infof("Scheduling broker autoscaler every %d ms for broker set %s",
                                spec.getPeriodMs(), brokerSetName);
                        brokerSetAutoscalerTasks.add(executorService.scheduleWithFixedDelay(
                                new BrokerSetAutoscaler(client, namespace, brokerSetName, clusterSpec),
                                spec.getPeriodMs(), spec.getPeriodMs(), TimeUnit.MILLISECONDS));
                    }

                }
            } else {
                log.debug("Broker autoscaler not changed");
            }
        }

        if (clusterSpec.getBookkeeper() != null) {
            final BookKeeperAutoscalerSpec spec = clusterSpec.getBookkeeper().getAutoscaler();
            if (namespaceContext.bkSpecChanged(spec)) {
                cancelCurrentBkTask();
                boolean enabled = spec != null && spec.getEnabled();
                if (enabled) {
                    log.infof("Scheduling BK autoscaler every %d ms", spec.getPeriodMs());
                    bkAutoscaler = executorService.scheduleWithFixedDelay(
                            new BookKeeperAutoscaler(client, namespace, clusterSpec),
                            spec.getPeriodMs(), spec.getPeriodMs(), TimeUnit.MILLISECONDS);
                } else {
                    log.debug("BK autoscaler is disabled");
                }
            } else {
                log.debug("BK autoscaler not changed");
            }
        }

        namespaceContext.setCurrentBrokerAutoscalerSpecs(allBrokerAutoscalerSpecs);
        namespaceContext.setCurrentBkSpec(clusterSpec.getBookkeeper() == null
                ? null
                : clusterSpec.getBookkeeper().getAutoscaler());
        namespaces.put(namespace, namespaceContext);
    }

    private Map<String, BrokerAutoscalerSpec> getBrokerAutoscalerSpecs(PulsarClusterSpec clusterSpec) {
        final BrokerSpec broker = clusterSpec.getBroker();
        final LinkedHashMap<String, BrokerSetSpec> brokerSetSpecs =
                BrokerController.getBrokerSetSpecs(broker);
        return brokerSetSpecs.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getAutoscaler()));
    }

    @Override
    public void close() {
        cancelBrokerAutoscalerTasks();
        cancelCurrentBkTask();
        executorService.shutdownNow();
    }

    private void cancelBrokerAutoscalerTasks() {
        brokerSetAutoscalerTasks.forEach(f -> {
            f.cancel(true);
            try {
                f.get();
            } catch (Throwable ignore) {
            }
        });
        brokerSetAutoscalerTasks.clear();
    }

    private void cancelCurrentBkTask() {
        if (bkAutoscaler != null) {
            bkAutoscaler.cancel(true);
            try {
                bkAutoscaler.get();
            } catch (Throwable ignore) {
            }
        }
    }
}
