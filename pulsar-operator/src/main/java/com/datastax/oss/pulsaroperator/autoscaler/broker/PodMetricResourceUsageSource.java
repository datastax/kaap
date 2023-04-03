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
package com.datastax.oss.pulsaroperator.autoscaler.broker;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class PodMetricResourceUsageSource implements BrokerResourceUsageSource {

    private final KubernetesClient client;
    private final String namespace;
    private final Map<String, String> podSelector;

    public PodMetricResourceUsageSource(KubernetesClient client, String namespace,
                                        Map<String, String> podSelector) {
        this.client = client;
        this.namespace = namespace;
        this.podSelector = podSelector;
    }

    @Override
    public List<ResourceUsage> getBrokersResourceUsages() {
        final PodMetricsList metrics =
                client.top()
                        .pods()
                        .withLabels(podSelector)
                        .inNamespace(namespace)
                        .metrics();

        log.infof("Got %d broker pod metrics", metrics.getItems().size());


        List<ResourceUsage> result = new ArrayList<>();


        for (PodMetrics item : metrics.getItems()) {
            final String podName = item.getMetadata().getName();

            float cpuUsage;
            float requestedCpu;

            Quantity cpuUsageQuantity = item.getContainers().get(0)
                    .getUsage().get("cpu");

            if (cpuUsageQuantity == null) {
                log.warnf("Broker pod %s didn't exposed CPU usage", podName);
                continue;
            } else {
                cpuUsage = quantityToBytes(cpuUsageQuantity);
            }

            final Quantity requestedCpuQuantity = client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .get().getSpec()
                    .getContainers()
                    .get(0)
                    .getResources()
                    .getRequests()
                    .get("cpu");
            if (requestedCpuQuantity == null) {
                log.warnf("Broker pod %s CPU requests not set", podName);
                continue;
            } else {
                requestedCpu = quantityToBytes(requestedCpuQuantity);

            }
            float percentage = cpuUsage / requestedCpu;

            log.infof("Broker pod %s CPU used/requested: %f/%f, rate %f",
                    podName,
                    new BigDecimal(cpuUsage).setScale(2, RoundingMode.HALF_EVEN),
                    new BigDecimal(requestedCpu).setScale(2, RoundingMode.HALF_EVEN),
                    new BigDecimal(percentage).setScale(2, RoundingMode.HALF_EVEN));

            result.add(new ResourceUsage(podName, percentage));
        }
        return result;
    }

    private static float quantityToBytes(Quantity quantity) {
        return Quantity.getAmountInBytes(quantity)
                .setScale(2, RoundingMode.HALF_EVEN)
                .floatValue();
    }
}

