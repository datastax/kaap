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

import com.datastax.oss.pulsaroperator.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.exception.ExceptionUtils;

@JBossLog
public class BookKeeperAutoscaler implements Runnable {

    static final ObjectMapper MAPPER = new ObjectMapper();

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

        PodResource podResource;
        List<BookieLedgerDiskInfo> ledgerDiskInfos;
    }

    @Data
    public static class ClusterStats {
        int writableBookiesTotal = 0;
        int atRiskWritableBookies = 0;
        int readOnlyBookiesTotal = 0;
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
            log.infof("Bookkeeper autoscaler starting");
            internalRun();
        } catch (Throwable tt) {
            if (ExceptionUtils.indexOfThrowable(tt, RejectedExecutionException.class) >= 0) {
                return;
            }
            log.error("Bookkeeper autoscaler error", tt);
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
        final boolean cleanUpPvcs = bkScalerSpec.getCleanUpPvcs();

        final BookKeeper bkCr = client.resources(BookKeeper.class)
                .inNamespace(namespace)
                .withName(bkName)
                .get();
        if (bkCr == null) {
            log.warnf("BookKeeper custom resource not found in namespace %s", namespace);
            return;
        }

        final int currentExpectedReplicas = bkCr.getSpec().getBookkeeper().getReplicas();

        // I assume after this point we don't have bookies down.
        // Bookies are either writable or read-only, isBkReadyToScale confirms all pods are up and running.
        if (!isBkReadyToScale(clusterName, bkBaseName, bkName, currentExpectedReplicas)) {
            log.infof("BookKeeper cluster %s %s is not ready to scale, expect replicas: %d",
                    clusterName, bkName, currentExpectedReplicas);
            return;
        }

        final LinkedHashMap<String, String> withLabels = new LinkedHashMap<>();
        withLabels.put(CRDConstants.LABEL_CLUSTER, clusterName);
        withLabels.put(CRDConstants.LABEL_COMPONENT, bkBaseName);

        List<BookieInfo> bookieInfos = collectBookieInfos(withLabels);

        if (cleanUpPvcs) {
            int cleanedUpCount = cleanupPvcs(bkBaseName, currentExpectedReplicas);
            if (cleanedUpCount > 0 && bookieInfos.size() > 0) {
                // Trigger audit earlier.
                // There is no point in skipping PVC deletion as the cookie is already deleted.
                log.infof("Cleaned up %d PVCs for bookkeeper cluster %s, will trigger audit",
                        cleanedUpCount, clusterName);
                triggerAudit(bookieInfos.get(0));
            }
        } else {
            log.debugf("PVC cleanup is disabled for bookkeeper cluster %s", clusterName);
        }

        ClusterStats clusterStats = collectClusterStats(diskUsageHwm, bookieInfos);

        int desiredScaleChange = 0;

        if (clusterStats.writableBookiesTotal < targetWritableBookiesCount) {
            desiredScaleChange += targetWritableBookiesCount - clusterStats.writableBookiesTotal;
            log.infof("Not enough writable bookies, need to add %d", desiredScaleChange);
        }

        if (clusterStats.atRiskWritableBookies > 0
                && (clusterStats.writableBookiesTotal - clusterStats.atRiskWritableBookies) < (targetWritableBookiesCount - desiredScaleChange)) {
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
            }
        }

        if (desiredScaleChange == 0) {
            log.infof("System is stable, no scaling needed");
            return;
        }

        if (desiredScaleChange < 0) {
            log.infof("Downscaling is needed");
            int sz = bookieInfos.size();
            for (int i = sz - 1; i >= sz + desiredScaleChange; i--) {
                makeReadOnly(bookieInfos.get(i));
            }
            for (int i = sz - 1; i >= sz + desiredScaleChange; i--) {
                String bookieName = bookieInfos.get(i).getPodResource().get().getMetadata().getName();

                if (!runBookieRecoveryAndRemoveCookie(bookieInfos.get(i))) {
                    log.warnf("Can't scale down, failed to recover %s", bookieName);
                    return;
                }

                if (doesNotHaveUnderReplicatedLedgers()) {
                    log.infof("ledgers recovered and cookie is deleted for %s, proceeding with downscale",
                            bookieName);
                } else {
                    log.warnf("Can't scale down, there are under replicated ledgers after recovering of %s",
                            bookieName);
                    return;
                }
            }
        }

        int scaleTo = currentExpectedReplicas + desiredScaleChange;
        final BookKeeperSpec bkSpec = bkCr.getSpec().getBookkeeper();
        bkSpec.setReplicas(scaleTo);

        client.resources(BookKeeper.class)
                        .inNamespace(namespace)
                        .withName(clusterName + "-" + bkBaseName)
                        .patch(bkCr);

        log.infof("Bookies scaled up/down from %d to %d", currentExpectedReplicas, scaleTo);
    }

    protected boolean runBookieRecoveryAndRemoveCookie(BookieInfo bookieInfo) {
        boolean success = false;
        try {
            recoverAndDeleteCookieInZk(bookieInfo);
            // todo: figure out better way to check if cookie got deleted or change recover command
            if (recoverAndDeleteCookieInZk(bookieInfo).contains("No cookie to remove")) {
                // have to do that, otherwise init bookie will fail if PVC survives
                deleteCookieOnDisk(bookieInfo);
                success = true;
            }
        } catch (Exception e) {
            log.errorf(e, "Error while recovering bookie %s",
                    bookieInfo.getPodResource().get().getMetadata().getName(), e);
        }
        return success;
    }

    private boolean checkIfCanScaleDown(double diskUsageLwm, List<BookieInfo> bookieInfos) {
        boolean canScaleDown = true;
        for (BookieInfo info: bookieInfos) {
            if (info.isWritable()) {
                long notReadyDiskCount = info.ledgerDiskInfos.stream()
                        .filter(d -> {
                            boolean res = isDiskUsageAboveTolerance(d, diskUsageLwm);
                            log.infof("isDiskUsageAboveTolerance: %s for %s (%s)", res,
                                    info.getPodResource().get().getMetadata().getName(),
                                    d);
                            return res;
                        })
                        .count();
                if (notReadyDiskCount != 0) {
                    // cannot estimate data distribution after removal of the bookie,
                    // don't want to go back and forth if bookies disk usage may result in
                    // switch to read-only/scale up soon
                    log.infof("Not all disks are ready for %s, can't scale down",
                            info.getPodResource().get().getMetadata().getName());
                    return false;
                }
            }
        }

        boolean doesNotHaveUnderReplicatedLedgers = doesNotHaveUnderReplicatedLedgers();
        canScaleDown = canScaleDown && doesNotHaveUnderReplicatedLedgers;

        return canScaleDown;
    }

    private ClusterStats collectClusterStats(double diskUsageHwm, List<BookieInfo> bookieInfos) {
        ClusterStats clusterStats = new ClusterStats();
        // ignoring racks for now
        for (BookieInfo info: bookieInfos) {
            if (info.isWritable()) {
                clusterStats.writableBookiesTotal++;

                long disksNotAtRisk = info.ledgerDiskInfos.stream()
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

    private List<BookieInfo> collectBookieInfos(LinkedHashMap<String, String> withLabels) {
        List<BookieInfo> bookieInfos = client.pods().inNamespace(namespace).withLabels(withLabels).resources()
                .map(pod -> getBoookieInfo(namespace, pod))
                .sorted(Comparator.comparing(b -> b.podResource.get().getMetadata().getName())).toList();
        return bookieInfos;
    }

    private int cleanupPvcs(String bkBaseName, int currentExpectedReplicas) {
        log.infof("Checking PVCs for bookies %s = %s", CRDConstants.LABEL_COMPONENT, bkBaseName);
        final AtomicInteger pvcCount = new AtomicInteger(0);
        client.persistentVolumeClaims()
                .inNamespace(namespace)
                .withLabel(CRDConstants.LABEL_COMPONENT, bkBaseName)
                .list().getItems().forEach(pvc -> {
            String name = pvc.getMetadata().getName();
            if (name.contains("-ledgers-") || name.contains("-journal-")) {
                int idx = Integer.parseInt(name.substring(name.lastIndexOf('-') + 1));
                if (idx >= currentExpectedReplicas) {
                    log.infof("Deleting PVC %s", name);
                    client.resource(pvc).delete();
                    pvcCount.incrementAndGet();
                } else {
                    log.debugf("Keeping PVC %s", name);
                }
            }
        });
        return pvcCount.get();
    }

    @SneakyThrows
    private String recoverAndDeleteCookieInZk(BookieInfo bookieInfo) {
        CompletableFuture<String> recoverOut = AutoscalerUtils.execInPod(client, namespace,
                bookieInfo.getPodResource().get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "bin/bookkeeper shell recover -f -d "
                        + getBookieId(bookieInfo.getPodResource()));
        recoverOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error recovering bookie %s",
                        bookieInfo.getPodResource().get().getMetadata().getName(), e);
            } else {
                log.infof("Bookie %s recovered",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            }
        });
        String res = recoverOut.get();
        log.infof("Recover output: %s", res);
        return res;
    }

    @SneakyThrows
    private void deleteCookieOnDisk(BookieInfo bookieInfo) {
        // moving rather than deleting, into a random name
        CompletableFuture<String> cookieOut = AutoscalerUtils.execInPod(client, namespace,
                bookieInfo.getPodResource().get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "mv /pulsar/data/bookkeeper/journal/current/VERSION "
                + "/pulsar/data/bookkeeper/journal/current/VERSION.old.$(head /dev/urandom | tr -dc a-z0-9 | head -c 8)");
        cookieOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error deleting cookie at %s",
                        bookieInfo.getPodResource().get().getMetadata().getName(), e);
            } else {
                log.infof("Bookie/s %s cookie is deleted",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            }
        });
        String res = cookieOut.get();
        log.infof("Cookie delete output: %s", res);
    }

    private String getBookieId(PodResource podResource) {
        Pod pod = podResource.get();
        return pod.getSpec().getHostname() + ":3181";
    }

    @SneakyThrows
    private void triggerAudit(BookieInfo bookieInfo) {
        CompletableFuture<String> curlOut = AutoscalerUtils.execInPod(client, namespace,
                bookieInfo.getPodResource().get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "curl -s -X PUT localhost:8000/api/v1/autorecovery/trigger_audit");
        curlOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error making bookie read-only %s",
                        bookieInfo.getPodResource().get().getMetadata().getName(), e);
            } else {
                log.infof("Bookie %s is set to read-only",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            }
        });
        curlOut.get();
    }

    @SneakyThrows
    private void makeReadOnly(BookieInfo bookieInfo) {
        CompletableFuture<String> curlOut = AutoscalerUtils.execInPod(client, namespace,
                bookieInfo.getPodResource().get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "curl -s -X PUT -H \"Content-Type: application/json\" "
                        + "-d '{\"readOnly\":true}' "
                        + "localhost:8000/api/v1/bookie/state/readonly");
        curlOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error making bookie read-only %s",
                        bookieInfo.getPodResource().get().getMetadata().getName(), e);
            } else {
                log.infof("Bookie %s is set to read-only",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            }
        });

        curlOut.get();
    }

    @SneakyThrows
    private boolean doesNotHaveUnderReplicatedLedgers() {
        //TODO: improve BK API, add an option for true/false response if any UR ledger exists
        // to avoid long wait for the full list, return json
        /*
        $ curl -s localhost:8000/api/v1/autorecovery/list_under_replicated_ledger/
        No under replicated ledgers found
        */
        final String clusterName = clusterSpec.getGlobal().getName();
        final LinkedHashMap<String, String> withLabels = new LinkedHashMap<>();
        withLabels.put(CRDConstants.LABEL_CLUSTER, clusterName);
        withLabels.put(CRDConstants.LABEL_COMPONENT, clusterSpec.getGlobal().getComponents().getBookkeeperBaseName());

        Optional<PodResource> pod = client.pods().inNamespace(namespace).withLabels(withLabels).resources().findFirst();
        if (pod.isEmpty()) {
            log.info("Could not get PodResource, assume something is underreplicated");
            return true;
        }
        CompletableFuture<String> urLedgersOut = AutoscalerUtils.execInPod(client, namespace,
                pod.get().get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "curl -s localhost:8000/api/v1/autorecovery/list_under_replicated_ledger/");

        return urLedgersOut.get().contains("No under replicated ledgers found");
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
        if (log.isDebugEnabled()) {
            log.debugf("getting BookieInfo for pod ()", pod.get().getMetadata().getName());
        }

        final String bookkeeperContainer = "%s-%s".formatted(
                clusterSpec.getGlobal().getName(),
                clusterSpec.getGlobal().getComponents().getBookkeeperBaseName()
        );
        CompletableFuture<String> bkStateOut = AutoscalerUtils.execInPod(client, namespace, pod.get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "curl -s localhost:8000/api/v1/bookie/state");

        CompletableFuture<String> bkInfoOut = AutoscalerUtils.execInPod(client, namespace, pod.get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "curl -s localhost:8000/api/v1/bookie/info");

        List<BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
        BookieLedgerDiskInfo diskInfo = BookieLedgerDiskInfo.builder()
                .build();
        parseAndFillDiskUsage(diskInfo, bkInfoOut.get());
        ledgerDiskInfos.add(diskInfo);

        boolean writable = parseIsWritable(bkStateOut.get());

        return BookieInfo.builder()
                .podResource(pod)
                .isWritable(writable)
                .ledgerDiskInfos(ledgerDiskInfos)
                .build();
    }

    @SneakyThrows
    private boolean parseIsWritable(String bkStateOutput) throws JsonProcessingException, InterruptedException, ExecutionException {
        /*
        $ curl -s localhost:8000/api/v1/bookie/state
        {
          "running" : true,
          "readOnly" : false,
          "shuttingDown" : false,
          "availableForHighPriorityWrites" : true
        }
        */
        JsonNode node = MAPPER.readTree(bkStateOutput);
        boolean writable = node.get("running").asBoolean()
                && !node.get("readOnly").asBoolean()
                && !node.get("shuttingDown").asBoolean();
        return writable;
    }

    @SneakyThrows
    private void parseAndFillDiskUsage(BookieLedgerDiskInfo diskInfo, String bkStateOutput) {
        /*
        $ curl -s localhost:8000/api/v1/bookie/info
        {
          "freeSpace" : 49769177088,
          "totalSpace" : 101129359360
        }
        */
        JsonNode node = MAPPER.readTree(bkStateOutput);
        long total = node.get("totalSpace").asLong(0);
        long free = node.get("freeSpace").asLong(Long.MAX_VALUE);
        diskInfo.setMaxBytes(total);
        diskInfo.setUsedBytes(total - free);
    }

    private boolean isBkReadyToScale(String clusterName, String bkBaseName, String bkName,
                                         int currentExpectedReplicas) {
        final Map<String, String> podSelector = new TreeMap<>(Map.of(
                CRDConstants.LABEL_CLUSTER, clusterName,
                CRDConstants.LABEL_COMPONENT, bkBaseName
        ));
        return AutoscalerUtils.isStsReadyToScale(client,
                clusterSpec.getBookkeeper().getAutoscaler().getStabilizationWindowMs(),
                namespace, bkName, podSelector, currentExpectedReplicas);
    }

}
