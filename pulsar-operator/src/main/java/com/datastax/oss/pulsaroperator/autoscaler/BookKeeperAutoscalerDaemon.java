package com.datastax.oss.pulsaroperator.autoscaler;

import com.datastax.oss.pulsaroperator.NamespacedDaemonThread;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.BookKeeperController;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
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
public class BookKeeperAutoscalerDaemon extends NamespacedDaemonThread<Map<String, BookKeeperAutoscalerSpec>> {

    private final KubernetesClient client;
    private final ScheduledExecutorService executorService;

    public BookKeeperAutoscalerDaemon(KubernetesClient client, ScheduledExecutorService executorService) {
        this.client = client;
        this.executorService = executorService;
    }

    @Override
    protected Map<String, BookKeeperAutoscalerSpec> getSpec(PulsarClusterSpec clusterSpec) {
        final BookKeeperSpec bk = clusterSpec.getBookkeeper();
        final LinkedHashMap<String, BookKeeperSetSpec> sets =
                BookKeeperController.getBookKeeperSetSpecs(bk);
        return sets.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getAutoscaler()));
    }

    @Override
    protected List<ScheduledFuture<?>> specChanged(String namespace, Map<String, BookKeeperAutoscalerSpec> newSpec,
                                                   PulsarClusterSpec clusterSpec) {
        List<ScheduledFuture<?>> newTasks = new ArrayList<>();
        for (Map.Entry<String, BookKeeperAutoscalerSpec> autoscaler :
                newSpec.entrySet()) {
            final BookKeeperAutoscalerSpec spec = autoscaler.getValue();
            if (spec.getEnabled()) {
                final String bkSetName = autoscaler.getKey();
                log.infof("Scheduling bookkeeper autoscaler every %d ms for bookkeeper set %s",
                        spec.getPeriodMs(), bkSetName);
                newTasks.add(executorService.scheduleWithFixedDelay(
                        new BookKeeperSetAutoscaler(client, namespace, bkSetName, clusterSpec),
                        spec.getPeriodMs(), spec.getPeriodMs(), TimeUnit.MILLISECONDS));
            }

        }
        return newTasks;
    }
}

