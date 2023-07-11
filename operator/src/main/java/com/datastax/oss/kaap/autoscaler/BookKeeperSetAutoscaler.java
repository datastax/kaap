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
package com.datastax.oss.kaap.autoscaler;

import com.datastax.oss.kaap.autoscaler.bookkeeper.BookieAdminClient;
import com.datastax.oss.kaap.autoscaler.bookkeeper.PodExecBookieAdminClient;
import com.datastax.oss.kaap.controllers.PulsarClusterController;
import com.datastax.oss.kaap.controllers.bookkeeper.BookKeeperController;
import com.datastax.oss.kaap.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeper;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperAutoscalerSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;

@JBossLog
public class BookKeeperSetAutoscaler implements Runnable {


    @Data
    public static class ClusterStats {
        int writableBookiesTotal = 0;
        int atRiskWritableBookies = 0;
        int readOnlyBookiesTotal = 0;
    }

    private final KubernetesClient client;
    private final String namespace;
    private final PulsarClusterSpec clusterSpec;
    private final String bookkeeperSetName;
    private final BookKeeperSetSpec desiredBookKeeperSetSpec;
    private BookieAdminClient bookieAdminClient;

    public BookKeeperSetAutoscaler(KubernetesClient client, String namespace,
                                   String bookkeeperSetName,
                                   PulsarClusterSpec clusterSpec) {
        this.client = client;
        this.namespace = namespace;
        this.clusterSpec = clusterSpec;
        this.bookkeeperSetName = bookkeeperSetName;
        this.desiredBookKeeperSetSpec = BookKeeperController.getBookKeeperSetSpecs(
                        new BookKeeperFullSpec(clusterSpec.getGlobal(), clusterSpec.getBookkeeper()))
                .get(bookkeeperSetName);
    }

    @Override
    public void run() {
        try {
            log.infof("Bookkeeper autoscaler starting for bookkeeper set %s", bookkeeperSetName);
            internalRun();
        } catch (Throwable tt) {
            if (ExceptionUtils.indexOfThrowable(tt, RejectedExecutionException.class) >= 0) {
                return;
            }
            log.errorf("Bookkeeper (bookkeeper set %s) autoscaler error", bookkeeperSetName, tt);
        }
    }

    protected BookieAdminClient newBookieAdminClient(GlobalSpec currentGlobalSpec,
                                                     BookKeeperSetSpec currentBookKeeperSetSpec) {
        return new PodExecBookieAdminClient(client, namespace, currentGlobalSpec, bookkeeperSetName,
                currentBookKeeperSetSpec);
    }

    @SneakyThrows
    void internalRun() {
        final BookKeeperAutoscalerSpec autoscalerSpec = desiredBookKeeperSetSpec.getAutoscaler();
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
        final int scaleUpMaxLimit = bkScalerSpec.getScaleUpMaxLimit();

        if (scaleUpMaxLimit < targetWritableBookiesCount) {
            throw new IllegalArgumentException("scaleUpMaxLimit must be >= to minWritableBookies, "
                    + "got scaleUpMaxLimit " + scaleUpMaxLimit + "and minWritableBookies" + targetWritableBookiesCount);
        }

        final String bkCustomResourceName = PulsarClusterController.computeCustomResourceName(clusterSpec,
                PulsarClusterController.CUSTOM_RESOURCE_BOOKKEEPER);
        final BookKeeper bkCr = client.resources(BookKeeper.class)
                .inNamespace(namespace)
                .withName(bkCustomResourceName)
                .get();
        if (bkCr == null) {
            log.warnf("BookKeeper custom resource not found in namespace %s", namespace);
            return;
        }

        final GlobalSpec currentGlobalSpec = bkCr.getSpec().getGlobal();
        final BookKeeperSetSpec currentBkSetSpec = BookKeeperController.getBookKeeperSetSpecs(
                new BookKeeperFullSpec(currentGlobalSpec, bkCr.getSpec().getBookkeeper())
        ).get(bookkeeperSetName);

        bookieAdminClient = newBookieAdminClient(currentGlobalSpec, currentBkSetSpec);

        final int currentExpectedReplicas = currentBkSetSpec.getReplicas();

        final String statefulsetName = BookKeeperResourcesFactory.getResourceName(clusterName,
                currentGlobalSpec.getComponents().getBookkeeperBaseName(), bookkeeperSetName,
                currentBkSetSpec.getOverrideResourceName());
        final String componentLabelValue = BookKeeperResourcesFactory.getComponentBaseName(currentGlobalSpec);

        final Map<String, String> podSelector = new TreeMap<>(Map.of(
                CRDConstants.LABEL_CLUSTER, clusterName,
                CRDConstants.LABEL_COMPONENT, componentLabelValue,
                CRDConstants.LABEL_RESOURCESET, bookkeeperSetName));


        // I assume after this point we don't have bookies down.
        // Bookies are either writable or read-only, isBkReadyToScale confirms all pods are up and running.
        if (!AutoscalerUtils.isStsReadyToScale(client,
                autoscalerSpec.getStabilizationWindowMs(),
                namespace, statefulsetName, podSelector, currentExpectedReplicas)) {
            log.infof("BookKeeper cluster %s %s is not ready to scale, expect replicas: %d",
                    clusterName, bkName, currentExpectedReplicas);
            return;
        }

        List<Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfos =
                this.bookieAdminClient.collectBookieInfos()
                        .stream()
                        .map(bookieInfo -> Pair.of(bookieInfo, this.bookieAdminClient.collectBookieStats(bookieInfo)))
                        .collect(Collectors.toList());

        ClusterStats clusterStats = collectClusterStats(diskUsageHwm, bookieInfos);

        int desiredScaleChange = 0;

        if (clusterStats.writableBookiesTotal < targetWritableBookiesCount) {
            desiredScaleChange += targetWritableBookiesCount - clusterStats.writableBookiesTotal;
            log.infof("Not enough writable bookies, need to add %d", desiredScaleChange);
        }

        if (clusterStats.atRiskWritableBookies > 0
                && (clusterStats.writableBookiesTotal - clusterStats.atRiskWritableBookies) < (
                targetWritableBookiesCount - desiredScaleChange)) {
            desiredScaleChange += bookieSafeStepUp;
            log.infof("Some writable bookies are at risk of running out of disk space, need to add extra %d",
                    bookieSafeStepUp);
        }

        if (desiredScaleChange == 0 && clusterStats.writableBookiesTotal > targetWritableBookiesCount) {
            boolean canScaleDown = checkIfCanScaleDown(diskUsageLwm, bookieInfos);
            if (canScaleDown) {
                desiredScaleChange -= Math.min(bookieSafeStepDown,
                        clusterStats.writableBookiesTotal - targetWritableBookiesCount);
                log.infof("Some writable bookies can be released, removing %d",
                        Math.abs(desiredScaleChange));
            } else {
                log.infof("Cannot scale down");
                return;
            }
        }

        if (desiredScaleChange == 0) {
            log.infof("System is stable, no scaling needed");
            return;
        }

        int scaleTo = currentExpectedReplicas + desiredScaleChange;
        scaleTo = Math.max(scaleTo, targetWritableBookiesCount);
        scaleTo = Math.min(scaleTo, scaleUpMaxLimit);

        if (currentExpectedReplicas == scaleTo) {
            log.infof("Hit scale limits, won't scale. Current expected replicas: %d, desired scale change: %d",
                    currentExpectedReplicas, desiredScaleChange);
            return;
        }

        applyScaleTo(bkCr, scaleTo);

        client.resources(BookKeeper.class)
                .inNamespace(namespace)
                .withName(bkCustomResourceName)
                .patch(bkCr);

        log.infof("Bookies scaled up/down from %d to %d", currentExpectedReplicas, scaleTo);
    }

    private void applyScaleTo(BookKeeper bookKeeperCr, int scaleTo) {
        bookKeeperCr.getSpec().getBookkeeper().getBookKeeperSetSpecRef(bookkeeperSetName).setReplicas(scaleTo);
    }


    private boolean checkIfCanScaleDown(double diskUsageLwm,
                                        List<Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfos) {
        boolean canScaleDown = true;
        for (Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats> info : bookieInfos) {
            if (info.getRight().isWritable()) {
                long notReadyDiskCount = info.getRight().getLedgerDiskInfos().stream()
                        .filter(d -> {
                            boolean res = isDiskUsageAboveTolerance(d, diskUsageLwm);
                            log.infof("isDiskUsageAboveTolerance: %s for %s (%s)", res,
                                    info.getLeft().getPodResource().get().getMetadata().getName(),
                                    d);
                            return res;
                        })
                        .count();
                if (notReadyDiskCount != 0) {
                    // cannot estimate data distribution after removal of the bookie,
                    // don't want to go back and forth if bookies disk usage may result in
                    // switch to read-only/scale up soon
                    log.infof("Not all disks are ready for %s",
                            info.getLeft().getPodResource().get().getMetadata().getName());
                    return false;
                }
            }
        }

        boolean doesNotHaveUnderReplicatedLedgers = bookieAdminClient.doesNotHaveUnderReplicatedLedgers();
        if (!doesNotHaveUnderReplicatedLedgers) {
            log.infof("Found underreplicated ledgers, can't scale down");
            canScaleDown = false;
        }

        return canScaleDown;
    }

    private ClusterStats collectClusterStats(double diskUsageHwm,
                                             List<Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>>
                                                     bookieInfos) {
        ClusterStats clusterStats = new ClusterStats();
        // ignoring racks for now
        for (Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats> info : bookieInfos) {
            if (info.getRight().isWritable()) {
                clusterStats.writableBookiesTotal++;

                long disksNotAtRisk = info.getRight().getLedgerDiskInfos().stream()
                        .filter(d -> isDiskUsageBelowTolerance(d, diskUsageHwm))
                        .count();
                if (disksNotAtRisk == 0) {
                    clusterStats.atRiskWritableBookies++;
                }
            } else {
                clusterStats.readOnlyBookiesTotal++;
            }
        }

        log.infof("Found %d writable bookies (%d at risk) and %d read-only",
                clusterStats.writableBookiesTotal,
                clusterStats.atRiskWritableBookies,
                clusterStats.readOnlyBookiesTotal);
        return clusterStats;
    }


    protected boolean isDiskUsageAboveTolerance(BookieAdminClient.BookieLedgerDiskInfo diskInfo, double tolerance) {
        return !isDiskUsageBelowTolerance(diskInfo, tolerance);
    }

    protected boolean isDiskUsageBelowTolerance(BookieAdminClient.BookieLedgerDiskInfo diskInfo, double tolerance) {
        return diskInfo.getMaxBytes() > 0
                && ((double) diskInfo.getUsedBytes() / diskInfo.getMaxBytes()) < tolerance;
    }
}
