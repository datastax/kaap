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

import com.datastax.oss.k8saap.NamespacedDaemonThread;
import com.datastax.oss.k8saap.controllers.broker.BrokerController;
import com.datastax.oss.k8saap.crds.broker.BrokerAutoscalerSpec;
import com.datastax.oss.k8saap.crds.broker.BrokerSetSpec;
import com.datastax.oss.k8saap.crds.broker.BrokerSpec;
import com.datastax.oss.k8saap.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class BrokerAutoscalerDaemon extends NamespacedDaemonThread<Map<String, BrokerAutoscalerSpec>> {

    private final KubernetesClient client;
    private final ScheduledExecutorService executorService;

    public BrokerAutoscalerDaemon(KubernetesClient client, ScheduledExecutorService executorService) {
        this.client = client;
        this.executorService = executorService;
    }

    @Override
    protected Map<String, BrokerAutoscalerSpec> getSpec(PulsarClusterSpec clusterSpec) {
        final BrokerSpec broker = clusterSpec.getBroker();
        final LinkedHashMap<String, BrokerSetSpec> brokerSetSpecs =
                BrokerController.getBrokerSetSpecs(broker);
        return brokerSetSpecs.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getAutoscaler()));
    }

    @Override
    protected List<ScheduledFuture<?>> specChanged(String namespace, Map<String, BrokerAutoscalerSpec> newSpec,
                                                   PulsarClusterSpec clusterSpec) {
        List<ScheduledFuture<?>> newTasks = new ArrayList<>();
        for (Map.Entry<String, BrokerAutoscalerSpec> brokerSetAutoscalers :
                newSpec.entrySet()) {
            final BrokerAutoscalerSpec spec = brokerSetAutoscalers.getValue();
            if (spec.getEnabled()) {
                final String brokerSetName = brokerSetAutoscalers.getKey();
                log.infof("Scheduling broker autoscaler every %d ms for broker set %s",
                        spec.getPeriodMs(), brokerSetName);
                newTasks.add(executorService.scheduleWithFixedDelay(
                        new BrokerSetAutoscaler(client, namespace, brokerSetName, clusterSpec),
                        spec.getPeriodMs(), spec.getPeriodMs(), TimeUnit.MILLISECONDS));
            }
        }
        return newTasks;
    }
}

