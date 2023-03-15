package com.datastax.oss.pulsaroperator.autoscaler;

import com.datastax.oss.pulsaroperator.NamespacedDaemonThread;
import com.datastax.oss.pulsaroperator.controllers.broker.BrokerController;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
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

