package com.datastax.oss.pulsaroperator.controllers.autoscaler;

import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class AutoscalerDaemon implements AutoCloseable {

    private final KubernetesClient client;
    private final ScheduledExecutorService executorService;
    private ScheduledFuture<?> brokerAutoscaler;
    private final Map<String, NamespaceContext> namespaces = new HashMap<>();

    @Data
    private static class NamespaceContext {
        private AutoscalerSpec currentSpec;

        private boolean brokerSpecChanged(AutoscalerSpec spec) {
            if (currentSpec != null
                    && spec != null
                    && Objects.equals(spec.getBroker(), currentSpec.getBroker())) {
                return false;
            }
            return true;
        }
    }

    public AutoscalerDaemon(KubernetesClient client) {
        this.client = client;
        this.executorService = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat("pulsar-autoscaler-%d")
                        .build());
    }

    public void onSpecChange(PulsarClusterSpec clusterSpec, String namespace) {
        final NamespaceContext namespaceContext = namespaces.getOrDefault(namespace,
                new NamespaceContext());
        final AutoscalerSpec spec = clusterSpec.getAutoscaler();
        if (namespaceContext.brokerSpecChanged(spec)) {
            if (brokerAutoscaler != null) {
                brokerAutoscaler.cancel(true);
                try {
                    brokerAutoscaler.get();
                } catch (Throwable ignore) {
                }
            }
            boolean enabled = spec.getBroker() != null && spec.getBroker().getEnabled();
            if (enabled) {
                final AutoscalerSpec.BrokerConfig brokerConfig = spec.getBroker();
                log.infof("Scheduling broker autoscaler every %d ms", brokerConfig.getPeriodMs());
                brokerAutoscaler = executorService.scheduleWithFixedDelay(
                        new BrokerAutoscaler(client, namespace, clusterSpec),
                        brokerConfig.getPeriodMs(), brokerConfig.getPeriodMs(), TimeUnit.MILLISECONDS);
            } else {
                log.info("Broker autoscaler is disabled");
            }
        } else {
            log.info("Broker autoscaler not changed");
        }
        namespaceContext.setCurrentSpec(spec);
        namespaces.put(namespace, namespaceContext);
    }

    @Override
    public void close() {
        brokerAutoscaler.cancel(true);
        try {
            brokerAutoscaler.get();
        } catch (Throwable ignore) {
        }
        executorService.shutdownNow();
    }
}
