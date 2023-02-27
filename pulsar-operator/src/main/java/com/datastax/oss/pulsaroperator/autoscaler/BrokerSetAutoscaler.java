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

import com.datastax.oss.pulsaroperator.controllers.PulsarClusterController;
import com.datastax.oss.pulsaroperator.controllers.broker.BrokerController;
import com.datastax.oss.pulsaroperator.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerFullSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.RejectedExecutionException;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.exception.ExceptionUtils;

@JBossLog
public class BrokerSetAutoscaler implements Runnable {

    private final KubernetesClient client;
    private final String namespace;
    private final PulsarClusterSpec clusterSpec;
    private final String brokerSetName;
    private final BrokerSetSpec desiredBrokerSetSpec;

    public BrokerSetAutoscaler(KubernetesClient client, String namespace,
                               String brokerSetName, PulsarClusterSpec clusterSpec) {
        this.client = client;
        this.namespace = namespace;
        this.brokerSetName = brokerSetName;
        this.clusterSpec = clusterSpec;
        this.desiredBrokerSetSpec = BrokerController.getBrokerSetSpecs(
                        new BrokerFullSpec(clusterSpec.getGlobal(), clusterSpec.getBroker()))
                .get(brokerSetName);
    }

    @Override
    public void run() {
        try {
            log.infof("Broker autoscaler starting for broker set %s", brokerSetName);
            internalRun();
            log.infof("Broker autoscaler finished for broker set %s", brokerSetName);
        } catch (Throwable tt) {
            if (ExceptionUtils.indexOfThrowable(tt, RejectedExecutionException.class) >= 0) {
                return;
            }
            log.errorf("Broker (broker set %s) autoscaler error", brokerSetName, tt);
        }
    }

    @SneakyThrows
    void internalRun() {
        final BrokerAutoscalerSpec autoscalerSpec = desiredBrokerSetSpec.getAutoscaler();
        Objects.requireNonNull(autoscalerSpec);

        final String clusterName = clusterSpec.getGlobal().getName();
        final String brokerCustomResourceName = PulsarClusterController.computeCustomResourceName(clusterSpec,
                PulsarClusterController.CUSTOM_RESOURCE_BROKER);
        final Broker brokerCr = client.resources(Broker.class)
                .inNamespace(namespace)
                .withName(brokerCustomResourceName)
                .get();
        if (brokerCr == null) {
            log.warnf("Broker custom resource not found in namespace %s", namespace);
            return;
        }

        final GlobalSpec currentGlobalSpec = brokerCr.getSpec().getGlobal();
        final BrokerSetSpec currentBrokerSetSpec = BrokerController.getBrokerSetSpecs(
                new BrokerFullSpec(currentGlobalSpec, brokerCr.getSpec().getBroker())
        ).get(brokerSetName);

        final int currentExpectedReplicas = currentBrokerSetSpec.getReplicas().intValue();


        final String statefulsetName = BrokerResourcesFactory.getResourceName(clusterName,
                currentGlobalSpec.getComponents().getBrokerBaseName(), brokerSetName,
                currentBrokerSetSpec.getOverrideResourceName());
        final String componentLabelValue = BrokerResourcesFactory.getComponentBaseName(currentGlobalSpec);

        final Map<String, String> podSelector = new TreeMap<>(Map.of(
                CRDConstants.LABEL_CLUSTER, clusterName,
                CRDConstants.LABEL_COMPONENT, componentLabelValue,
                CRDConstants.LABEL_RESOURCESET, brokerSetName));

        if (!AutoscalerUtils.isStsReadyToScale(client,
                autoscalerSpec.getStabilizationWindowMs(),
                namespace, statefulsetName, podSelector, currentExpectedReplicas)) {
            return;
        }

        final PodMetricsList metrics =
                client.top()
                        .pods()
                        .withLabels(podSelector)
                        .inNamespace(namespace)
                        .metrics();

        log.infof("Got %d broker pod metrics", metrics.getItems().size());

        Boolean scaleUpOrDown = decideScaleUpOrDown(autoscalerSpec, metrics);

        if (scaleUpOrDown != null) {
            int scaleTo = scaleUpOrDown
                    ? currentExpectedReplicas + autoscalerSpec.getScaleUpBy()
                    : currentExpectedReplicas - autoscalerSpec.getScaleDownBy();

            final Integer min = autoscalerSpec.getMin();
            if (scaleTo <= 0 || (min != null && scaleTo < min)) {
                log.debugf("Can't scale down, "
                                + "replicas is already the min. Current %d, min %d, scaleDownBy %d",
                        currentExpectedReplicas,
                        min,
                        autoscalerSpec.getScaleDownBy()
                );
                return;
            }
            final Integer max = autoscalerSpec.getMax();
            if (max != null && scaleTo > max) {
                log.debugf("Can't scale down, "
                                + "replicas is already the max. Current %d, max %d, scaleUpBy %d",
                        currentExpectedReplicas,
                        max,
                        autoscalerSpec.getScaleUpBy()
                );
                return;
            }


            applyScaleTo(brokerCr, scaleTo);
            client.resources(Broker.class)
                    .inNamespace(namespace)
                    .withName(brokerCustomResourceName)
                    .patch(brokerCr);
            log.infof("Scaled brokers for broker set %s from %d to %d",
                    brokerSetName, currentExpectedReplicas, scaleTo);
        } else {
            log.infof("System is stable, no scaling needed");
        }
    }

    private void applyScaleTo(Broker brokerCr, int scaleTo) {
        if (brokerSetName.equals(BrokerResourcesFactory.BROKER_DEFAULT_SET)) {
            brokerCr.getSpec().getBroker().setReplicas(scaleTo);
        } else {
            brokerCr.getSpec().getBroker().getSets().get(brokerSetName).setReplicas(scaleTo);
        }
    }

    private Boolean decideScaleUpOrDown(BrokerAutoscalerSpec autoscalerSpec, PodMetricsList metrics) {
        Boolean scaleUpOrDown = null;

        float cpuLowerThreshold = autoscalerSpec.getLowerCpuThreshold().floatValue();
        float cpuHigherThreshold = autoscalerSpec.getHigherCpuThreshold().floatValue();

        class BrokerStat {
            float usedCpu;
            float requestedCpu;
        }
        List<BrokerStat> brokerStats = new ArrayList<>();

        for (PodMetrics item : metrics.getItems()) {
            final String podName = item.getMetadata().getName();

            float cpuUsage;
            float requestedCpu;

            Quantity cpuUsageQuantity = item.getContainers().get(0)
                    .getUsage().get("cpu");

            if (cpuUsageQuantity == null) {
                log.infof("Broker pod %s doesn't exposed CPU usage", podName);
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
                log.infof("Broker pod %s CPU requests not set", podName);
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

            final BrokerStat stat = new BrokerStat();
            stat.requestedCpu = requestedCpu;
            stat.usedCpu = cpuUsage;
            brokerStats.add(stat);
        }

        for (BrokerStat brokerStat : brokerStats) {
            float percentage = brokerStat.usedCpu / brokerStat.requestedCpu;

            if (percentage < cpuLowerThreshold) {
                if (scaleUpOrDown != null && scaleUpOrDown) {
                    scaleUpOrDown = null;
                    break;
                }
                scaleUpOrDown = false;
            } else if (percentage > cpuHigherThreshold) {
                if (scaleUpOrDown != null && !scaleUpOrDown) {
                    scaleUpOrDown = null;
                    break;
                }
                scaleUpOrDown = true;
            } else {
                scaleUpOrDown = null;
                break;
            }
        }
        return scaleUpOrDown;
    }

    private float quantityToBytes(Quantity quantity) {
        return Quantity.getAmountInBytes(quantity)
                .setScale(2, RoundingMode.HALF_EVEN)
                .floatValue();
    }

}
