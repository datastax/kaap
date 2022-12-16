package com.datastax.oss.pulsaroperator.autoscaler;

import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import javax.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.exception.ExceptionUtils;

@JBossLog
public class BookKeeperAutoscaler implements Runnable {

    @Data
    @Builder
    public static class BookieLedgerDiskInfo {
        @Builder.Default
        long maxBytes = 0L;
        @Builder.Default
        long usedBytes = 0L;
    }

    @Data
    @Builder
    public static class BookieInfo {
        @Builder.Default
        boolean isWritable = false;
        @Builder.Default
        String rackInfo = "/default-region/default-rack";

        List<BookieLedgerDiskInfo> ledgerDiskInfos;
    }

    private final KubernetesClient client;
    private final String namespace;
    private final PulsarClusterSpec clusterSpec;

    public BookKeeperAutoscaler(KubernetesClient client, String namespace,
                            PulsarClusterSpec clusterSpec) {
        this.client = client;
        this.namespace = namespace;
        this.clusterSpec = clusterSpec;
    }

    @Override
    public void run() {
        try {
            log.infof("Broker autoscaler starting");
            internalRun();
        } catch (Throwable tt) {
            if (ExceptionUtils.indexOfThrowable(tt, RejectedExecutionException.class) >= 0) {
                return;
            }
            log.error("Broker autoscaler error", tt);
        }
    }

    @SneakyThrows
    void internalRun() {
        final BookKeeperAutoscalerSpec autoscalerSpec = clusterSpec.getBookkeeper().getAutoscaler();
        Objects.requireNonNull(autoscalerSpec);

        final String clusterName = clusterSpec.getGlobal().getName();
        final String bkBaseName = clusterSpec.getGlobal()
                .getComponents().getBookkeeperBaseName();
        final String bkName = "%s-%s".formatted(clusterName, bkBaseName);
        @Valid BookKeeperAutoscalerSpec bkScalerSpec = clusterSpec.getBookkeeper().getAutoscaler();
        final double diskUsageHwm = bkScalerSpec.getDiskUsageToleranceHwm();
        final double diskUsageLwm = bkScalerSpec.getDiskUsageToleranceLwm();
        final int targetWritableBookiesCount = bkScalerSpec.getMinWritableBookies();
        final int bookieSafeStepUp = bkScalerSpec.getScaleUpBy();
        final int bookieSafeStepDown = bkScalerSpec.getScaleDownBy();

        final BookKeeper bkCr = client.resources(BookKeeper.class)
                .inNamespace(namespace)
                .withName(bkName)
                .get();
        if (bkCr == null) {
            log.warnf("BookKeeper custom resource not found in namespace %s", namespace);
            return;
        }

        final int currentExpectedReplicas = bkCr.getSpec().getBookkeeper().getReplicas().intValue();

        // I assume after this point we don't have bookies down / under-replicated ledgers
        // Bookies are either writable or read-only, isBkReadyToScale conforms all pods are up and running.
        if (!isBkReadyToScale(clusterName, bkBaseName, bkName, currentExpectedReplicas)) {
            return;
        }

        final LinkedHashMap<String, String> withLabels = new LinkedHashMap<>();
        withLabels.put(CRDConstants.LABEL_CLUSTER, clusterName);
        withLabels.put(CRDConstants.LABEL_COMPONENT, bkBaseName);

        List<BookieInfo> bookieInfos = client.pods().inNamespace(namespace).withLabels(withLabels).resources()
                .map(pod -> getBoookieInfo(namespace, pod)).toList();

        int writableBookiesTotal = 0;
        int atRiskWritableBookies = 0;
        int readOnlyBookiesTotal = 0;
        // ignoring racks for now
        for (BookieInfo info: bookieInfos) {
            if (info.isWritable()) {
                writableBookiesTotal++;

                long disksNotAtRisk = info.ledgerDiskInfos.stream()
                        .filter(d -> isDiskUsageBelowTolerance(d, diskUsageHwm))
                        .count();
                if (disksNotAtRisk == 0) {
                    atRiskWritableBookies++;
                }
            } else {
                readOnlyBookiesTotal++;
            }
        }

        log.infof("Found %d writable bookies (%d at risk) and %d read-only",
                writableBookiesTotal, atRiskWritableBookies, readOnlyBookiesTotal);

        int desiredScaleChange = 0;

        if (writableBookiesTotal < targetWritableBookiesCount) {
            desiredScaleChange += targetWritableBookiesCount - writableBookiesTotal;
            log.infof("Not enough writable bookies, need to add %d", desiredScaleChange);
        }

        if (atRiskWritableBookies > 0
                && (writableBookiesTotal - atRiskWritableBookies) < (targetWritableBookiesCount - desiredScaleChange)) {
            desiredScaleChange += bookieSafeStepUp;
            log.infof("Some writable bookies are at risk of running out of disk space, need to add extra %d",
                    bookieSafeStepUp);
        }

        if (desiredScaleChange == 0 && writableBookiesTotal > targetWritableBookiesCount) {

            boolean canScaleDown = true;
            for (BookieInfo info: bookieInfos) {
                if (info.isWritable()) {
                    long notReadyDiskCount = info.ledgerDiskInfos.stream()
                            .filter(d -> isDiskUsageAboveTolerance(d, diskUsageLwm))
                            .count();
                    if (notReadyDiskCount != 0) {
                        canScaleDown = false;
                        break;
                    }
                }
            }

            if (canScaleDown) {
                desiredScaleChange -= bookieSafeStepDown;
                log.infof("Some writable bookies can be released, removing %d",
                        bookieSafeStepDown);
            }
        }

        if (desiredScaleChange == 0) {
            log.infof("System is stable, no scaling needed");
            return;
        } else {
            int scaleTo = currentExpectedReplicas + desiredScaleChange;
            final BookKeeperSpec bkSpec = bkCr.getSpec().getBookkeeper();
            bkSpec.setReplicas(scaleTo);

            client.resources(BookKeeper.class)
                            .inNamespace(namespace)
                            .withName(clusterName + "-" + bkBaseName)
                            .patch(bkCr);

            log.infof("Bookies scaled up/down from %d to %d", currentExpectedReplicas, scaleTo);
        }
    }

    protected boolean isDiskUsageAboveTolerance(BookieLedgerDiskInfo diskInfo, double tolerance) {
        return !isDiskUsageBelowTolerance(diskInfo, tolerance);
    }

    protected boolean isDiskUsageBelowTolerance(BookieLedgerDiskInfo diskInfo, double tolerance) {
        return diskInfo.getMaxBytes() > 0
                && ((double) diskInfo.getUsedBytes() / diskInfo.getMaxBytes()) < tolerance;
    }

    @SneakyThrows
    protected BookieInfo getBoookieInfo(String namespace, PodResource pod) {
        // TODO get the info
        // generate something for now

        log.infof("got pod ()", pod.get().getMetadata().getName());

        CompletableFuture<String> dfOut = AutoscalerUtils.execInPod(client, namespace, pod.get().getMetadata().getName(),
                clusterSpec.getGlobal().getComponents().getBookkeeperBaseName(),
                "df", "-k",
                // TODO: get this form config somehow
                "/pulsar/data/bookkeeper/" + clusterSpec.getBookkeeper().getVolumes().getLedgers().getName());

        log.infof("df out: %s", dfOut.get());

        List<BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
        BookieLedgerDiskInfo diskInfo = BookieLedgerDiskInfo.builder()
                .build();
        parseDiskUsage(diskInfo, dfOut.get());
        ledgerDiskInfos.add(diskInfo);

        // TODO: get writable status
        return BookieInfo.builder()
                .isWritable(true)
                .ledgerDiskInfos(ledgerDiskInfos)
                .build();
    }

    private void parseDiskUsage(BookieLedgerDiskInfo diskInfo, String dfOutput) {
        String secondLine = dfOutput.split("\\R")[1];
        String[] chunks = secondLine.split("\s+");
        long total = Long.parseLong(chunks[1]) * 1024;
        long used = Long.parseLong(chunks[2]) * 1024;
        diskInfo.setMaxBytes(total);
        diskInfo.setUsedBytes(used);
    }

    private boolean isBkReadyToScale(String clusterName, String brokerBaseName, String brokerName,
                                         int currentExpectedReplicas) {
        return AutoscalerUtils.isStsReadyToScale(client,
                clusterSpec.getBookkeeper().getAutoscaler().getStabilizationWindowMs(),
                clusterName, namespace, brokerBaseName, brokerName, currentExpectedReplicas);
    }

}
