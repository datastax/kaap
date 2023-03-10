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

import com.datastax.oss.pulsaroperator.controllers.bookkeeper.BookKeeperController;
import com.datastax.oss.pulsaroperator.controllers.broker.BrokerController;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
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
    private final List<ScheduledFuture<?>> bookkeeperSetAutoscalerTasks = new ArrayList<>();
    private final Map<String, NamespaceContext> namespaces = new HashMap<>();

    @Data
    private static class NamespaceContext {
        private Map<String, BrokerAutoscalerSpec> currentBrokerAutoscalerSpecs;
        private Map<String, BookKeeperAutoscalerSpec> currentBkAutoscalerSpecs;

        private boolean brokerSpecChanged(Map<String, BrokerAutoscalerSpec> spec) {
            if (currentBrokerAutoscalerSpecs != null
                    && spec != null
                    && Objects.equals(spec, currentBrokerAutoscalerSpecs)) {
                return false;
            }
            return true;
        }

        private boolean bkSpecChanged(Map<String, BookKeeperAutoscalerSpec> spec) {
            if (currentBkAutoscalerSpecs != null
                    && spec != null
                    && Objects.equals(spec, currentBkAutoscalerSpecs)) {
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
        Map<String, BookKeeperAutoscalerSpec> allBookKeeperAutoscalerSpecs = null;
        if (clusterSpec.getBroker() != null) {
            allBrokerAutoscalerSpecs = getBrokerAutoscalerSpecs(clusterSpec);
            if (namespaceContext.brokerSpecChanged(allBrokerAutoscalerSpecs)) {
                cancelBrokerAutoscalerTasks();
                scheduleBrokerAutoscalerTasks(clusterSpec, namespace, allBrokerAutoscalerSpecs);
            } else {
                log.debug("Broker autoscaler not changed");
            }
        }

        if (clusterSpec.getBookkeeper() != null) {
            allBookKeeperAutoscalerSpecs = getBookKeeperAutoscalerSpecs(clusterSpec);

            if (namespaceContext.bkSpecChanged(allBookKeeperAutoscalerSpecs)) {
                cancelBookKeeperAutoscalerTasks();
                scheduleBookKeeperAutoscalerTasks(clusterSpec, namespace, allBookKeeperAutoscalerSpecs);
            } else {
                log.debug("Bookkeeper autoscaler not changed");
            }
        }

        namespaceContext.setCurrentBrokerAutoscalerSpecs(allBrokerAutoscalerSpecs);
        namespaceContext.setCurrentBkAutoscalerSpecs(allBookKeeperAutoscalerSpecs);
        namespaces.put(namespace, namespaceContext);
    }

    private void scheduleBrokerAutoscalerTasks(PulsarClusterSpec clusterSpec, String namespace,
                           Map<String, BrokerAutoscalerSpec> allBrokerAutoscalerSpecs) {
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
    }

    private void scheduleBookKeeperAutoscalerTasks(PulsarClusterSpec clusterSpec, String namespace,
                                               Map<String, BookKeeperAutoscalerSpec> specs) {
        for (Map.Entry<String, BookKeeperAutoscalerSpec> autoscaler:
                specs.entrySet()) {
            final BookKeeperAutoscalerSpec spec = autoscaler.getValue();
            if (spec.getEnabled()) {
                final String bkSetName = autoscaler.getKey();
                log.infof("Scheduling bookkeeper autoscaler every %d ms for bookkeeper set %s",
                        spec.getPeriodMs(), bkSetName);
                bookkeeperSetAutoscalerTasks.add(executorService.scheduleWithFixedDelay(
                        new BookKeeperSetAutoscaler(client, namespace, bkSetName, clusterSpec),
                        spec.getPeriodMs(), spec.getPeriodMs(), TimeUnit.MILLISECONDS));
            }

        }
    }

    private Map<String, BrokerAutoscalerSpec> getBrokerAutoscalerSpecs(PulsarClusterSpec clusterSpec) {
        final BrokerSpec broker = clusterSpec.getBroker();
        final LinkedHashMap<String, BrokerSetSpec> brokerSetSpecs =
                BrokerController.getBrokerSetSpecs(broker);
        return brokerSetSpecs.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getAutoscaler()));
    }

    private Map<String, BookKeeperAutoscalerSpec> getBookKeeperAutoscalerSpecs(PulsarClusterSpec clusterSpec) {
        final BookKeeperSpec bk = clusterSpec.getBookkeeper();
        final LinkedHashMap<String, BookKeeperSetSpec> sets =
                BookKeeperController.getBookKeeperSetSpecs(bk);
        return sets.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getAutoscaler()));
    }

    @Override
    public void close() {
        cancelBrokerAutoscalerTasks();
        cancelBookKeeperAutoscalerTasks();
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

    private void cancelBookKeeperAutoscalerTasks() {
        bookkeeperSetAutoscalerTasks.forEach(f -> {
            f.cancel(true);
            try {
                f.get();
            } catch (Throwable ignore) {
            }
        });
        bookkeeperSetAutoscalerTasks.clear();
    }
}
