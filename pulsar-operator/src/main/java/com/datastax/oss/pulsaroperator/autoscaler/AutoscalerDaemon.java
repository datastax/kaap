package com.datastax.oss.pulsaroperator.autoscaler;

import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
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
    private ScheduledFuture<?> bkAutoscaler;
    private final Map<String, NamespaceContext> namespaces = new HashMap<>();

    @Data
    private static class NamespaceContext {
        private BrokerAutoscalerSpec currentSpec;
        private BookKeeperAutoscalerSpec currentBkSpec;

        private boolean brokerSpecChanged(BrokerAutoscalerSpec spec) {
            if (currentSpec != null
                    && spec != null
                    && Objects.equals(spec, currentSpec)) {
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
        if (clusterSpec.getBroker() != null) {
            final BrokerAutoscalerSpec spec = clusterSpec.getBroker().getAutoscaler();
            if (namespaceContext.brokerSpecChanged(spec)) {
                cancelCurrentTask();
                boolean enabled = spec != null && spec.getEnabled();
                if (enabled) {
                    log.infof("Scheduling broker autoscaler every %d ms", spec.getPeriodMs());
                    brokerAutoscaler = executorService.scheduleWithFixedDelay(
                            new BrokerAutoscaler(client, namespace, clusterSpec),
                            spec.getPeriodMs(), spec.getPeriodMs(), TimeUnit.MILLISECONDS);
                } else {
                    log.info("Broker autoscaler is disabled");
                }
            } else {
                log.info("Broker autoscaler not changed");
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
                    log.info("BK autoscaler is disabled");
                }
            } else {
                log.info("BK autoscaler not changed");
            }
        }

        namespaceContext.setCurrentSpec(clusterSpec.getBroker() == null ? null : clusterSpec.getBroker().getAutoscaler());
        namespaceContext.setCurrentBkSpec(clusterSpec.getBookkeeper() == null
                ? null
                : clusterSpec.getBookkeeper().getAutoscaler());
        namespaces.put(namespace, namespaceContext);
    }

    @Override
    public void close() {
        cancelCurrentTask();
        executorService.shutdownNow();
    }

    private void cancelCurrentTask() {
        if (brokerAutoscaler != null) {
            brokerAutoscaler.cancel(true);
            try {
                brokerAutoscaler.get();
            } catch (Throwable ignore) {
            }
        }
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
