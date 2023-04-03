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

import com.datastax.oss.pulsaroperator.autoscaler.broker.BrokerResourceUsageSource;
import com.datastax.oss.pulsaroperator.autoscaler.broker.LoadReportResourceUsageSource;
import com.datastax.oss.pulsaroperator.autoscaler.broker.PodMetricResourceUsageSource;
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
import io.fabric8.kubernetes.client.KubernetesClient;
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
            internalRun();
        } catch (Throwable tt) {
            if (ExceptionUtils.indexOfThrowable(tt, RejectedExecutionException.class) >= 0) {
                return;
            }
            log.errorf(tt, "Broker (broker set %s) autoscaler error", brokerSetName);
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
        BrokerResourceUsageSource brokerResourceUsageSource =
                newBrokerResourceUsageSource(autoscalerSpec, podSelector);
        Boolean scaleUpOrDown = decideScaleUpOrDown(autoscalerSpec, brokerResourceUsageSource);

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
            brokerCr.getSpec().getBroker().getDefaultBrokerSpecRef().setReplicas(scaleTo);
        } else {
            brokerCr.getSpec().getBroker().getSets().get(brokerSetName).setReplicas(scaleTo);
        }
    }

    private Boolean decideScaleUpOrDown(BrokerAutoscalerSpec autoscalerSpec,
                                        BrokerResourceUsageSource brokerResourceUsageSource) {
        float cpuLowerThreshold = autoscalerSpec.getLowerCpuThreshold().floatValue();
        float cpuHigherThreshold = autoscalerSpec.getHigherCpuThreshold().floatValue();


        final List<BrokerResourceUsageSource.ResourceUsage> brokersResourceUsages =
                brokerResourceUsageSource.getBrokersResourceUsages();

        boolean scaleUp = false;
        boolean scaleDown = false;
        for (BrokerResourceUsageSource.ResourceUsage brokerUsage : brokersResourceUsages) {
            final double cpuPercentage = brokerUsage.getPercentCpu();
            if (cpuPercentage < cpuLowerThreshold) {
                if (scaleUp) {
                    return null;
                }
                scaleDown = true;
            } else if (cpuPercentage > cpuHigherThreshold) {
                if (scaleDown) {
                    return null;
                }
                scaleUp = true;
            } else {
                return null;
            }
        }
        if (scaleUp && scaleDown) {
            throw new IllegalStateException();
        }
        if (scaleUp) {
            return true;
        }
        if (scaleDown) {
            return false;
        }
        throw new IllegalStateException();
    }

    private BrokerResourceUsageSource newBrokerResourceUsageSource(BrokerAutoscalerSpec brokerAutoscalerSpec,
                                                                   Map<String, String> podSelector) {
        switch (brokerAutoscalerSpec.getResourcesUsageSource()) {
            case BrokerAutoscalerSpec.RESOURCE_USAGE_SOURCE_LOAD_BALANCER:
                return new LoadReportResourceUsageSource(client, namespace, podSelector, brokerSetName,
                        desiredBrokerSetSpec, clusterSpec.getGlobalSpec());
            case BrokerAutoscalerSpec.RESOURCE_USAGE_SOURCE_K8S_METRICS:
                return new PodMetricResourceUsageSource(client, namespace, podSelector);
            default:
                throw new IllegalArgumentException(
                        "Unknown resource usage source: " + brokerAutoscalerSpec.getResourcesUsageSource());
        }
    }
}
