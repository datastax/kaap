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

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.PulsarClusterController;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.BookKeeperController;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
public class BookKeeperSetAutoscaler implements Runnable {

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
    private final String bookkeeperSetName;
    private final BookKeeperSetSpec desiredBookKeeperSetSpec;

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
        final boolean cleanUpPvcs = bkScalerSpec.getCleanUpPvcs();
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
        final String bookieUrl = computeBookieUrl(currentBkSetSpec);
        if (log.isDebugEnabled()) {
            log.debugf("Using bookie url %s to access REST API", bookieUrl);
        }

        List<BookieInfo> bookieInfos = collectBookieInfos(bookieUrl, podSelector);

        if (cleanUpPvcs) {
            int cleanedUpCount = cleanupPvcs(podSelector, currentBkSetSpec);
            if (cleanedUpCount > 0 && bookieInfos.size() > 0) {
                // Trigger audit earlier.
                // There is no point in skipping PVC deletion as the cookie is already deleted.
                log.infof("Cleaned up %d PVCs for bookkeeper cluster %s, will trigger audit",
                        cleanedUpCount, clusterName);
                triggerAudit(bookieUrl, bookieInfos.get(0));
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
                && (clusterStats.writableBookiesTotal - clusterStats.atRiskWritableBookies) < (
                targetWritableBookiesCount - desiredScaleChange)) {
            desiredScaleChange += bookieSafeStepUp;
            log.infof("Some writable bookies are at risk of running out of disk space, need to add extra %d",
                    bookieSafeStepUp);
        }

        if (desiredScaleChange == 0 && clusterStats.writableBookiesTotal > targetWritableBookiesCount) {
            boolean canScaleDown = checkIfCanScaleDown(bookieUrl, diskUsageLwm, bookieInfos, podSelector);
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

        if (desiredScaleChange < 0) {
            log.infof("Downscaling is needed");
            int sz = bookieInfos.size();
            Set<BookieInfo> bookiesToUnset = new HashSet<>(sz);

            for (int i = sz - 1; i >= sz + desiredScaleChange; i--) {
                bookiesToUnset.add(bookieInfos.get(i));
                setReadOnly(bookieUrl, bookieInfos.get(i), true);
            }

            // wait for bookies to be read-only
            Thread.sleep(3000);

            boolean success = true;
            for (int i = sz - 1; i >= sz + desiredScaleChange; i--) {
                String bookieName = bookieInfos.get(i).getPodResource().get().getMetadata().getName();
                log.infof("Attempting downscale of bookie %s with bookieId = %s",
                        bookieName, getBookieId(bookieInfos.get(i).getPodResource()));

                if (!runBookieRecovery(bookieInfos.get(i))) {
                    log.warnf("Can't scale down, failed to recover %s with bookieId = %s",
                            bookieName,
                            getBookieId(bookieInfos.get(i).getPodResource()));
                    success = false;
                    break;
                }
            }

            if (success && doesNotHaveUnderReplicatedLedgers(bookieUrl, podSelector)) {
                log.infof("ledgers recovered successfully, proceeding with cookie removal");
            } else {
                log.warnf("Can't scale down, there are under replicated ledgers after recovery");
                success = false;
            }

            if (success) {
                for (int i = sz - 1; i >= sz + desiredScaleChange; i--) {
                    // todo: I think it is possible to get into a bad state here
                    // if the cookie delete passes but connection fails and k8s client returns error.
                    // or if the disk cookie deletion fails due to some k8s/network error
                    // Cookie on the disk will persist, PVC will be preserved,
                    // and on restart the bookie will fail
                    if (!deleteCookie(bookieInfos.get(i))) {
                        log.warnf("Can't scale down, failed to delete cookie for %s",
                                bookieInfos.get(i).getPodResource().get().getMetadata().getName());
                        success = false;
                        break;
                    }
                    bookiesToUnset.remove(bookieInfos.get(i));
                }
            }

            if (!success || bookiesToUnset.size() > 0) {

                int partiallySucceeded = sz - bookiesToUnset.size();

                if (partiallySucceeded > 0 && partiallySucceeded < (-1 * desiredScaleChange)) {
                    log.warnf("Downscale partially succeeded, %d bookies can be removed",
                            partiallySucceeded);
                    desiredScaleChange = -1 * partiallySucceeded;
                } else {
                    log.warnf("Downscale failed, will retry again later");
                    desiredScaleChange = 0;
                }

                for (BookieInfo bInfo : bookiesToUnset) {
                    setReadOnly(bookieUrl, bInfo, false);
                }
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
        if (bookkeeperSetName.equals(BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET)) {
            bookKeeperCr.getSpec().getBookkeeper().getDefaultBookKeeperSpecRef().setReplicas(scaleTo);
        } else {
            bookKeeperCr.getSpec().getBookkeeper().getSets().get(bookKeeperCr).setReplicas(scaleTo);
        }
    }

    private String computeBookieUrl(BookKeeperSetSpec currentSpec) {
        final String configKey = "%s%s".formatted(BaseResourcesFactory.CONFIG_PULSAR_PREFIX, "httpServerPort");
        final Map<String, Object> config = currentSpec.getConfig();
        final Object port;
        if (config == null || !config.containsKey(configKey)) {
            port = BookKeeperResourcesFactory.DEFAULT_HTTP_PORT;
        } else {
            port = config.getOrDefault(configKey, BookKeeperResourcesFactory.DEFAULT_HTTP_PORT);
        }
        return "%s://%s:%s".formatted(
                BaseResourcesFactory.isTlsEnabledOnBookKeeper(clusterSpec.getGlobal()) ? "https" : "http",
                "localhost",
                String.valueOf(port)
        );
    }

    protected boolean runBookieRecovery(BookieInfo bookieInfo) {
        boolean success = false;
        try {
            String res = recoverAndDeleteCookieInZk(bookieInfo, false);
            if (!res.contains("Recover bookie operation completed with rc: OK: No problem")) {
                log.warnf("Recovery failed for bookie %s \n %s",
                        bookieInfo.getPodResource().get().getMetadata().getName(), res);
                return false;
            }

            if (existLedgerOnBookie(bookieInfo)) {
                log.warnf("Bookie %s still has ledgers assigned to it, will not delete cookie",
                        bookieInfo.getPodResource().get().getMetadata().getName());
                return false;
            }

            success = true;
        } catch (Exception e) {
            log.errorf(e, "Error while recovering bookie %s",
                    bookieInfo.getPodResource().get().getMetadata().getName());
        }
        return success;
    }

    protected boolean deleteCookie(BookieInfo bookieInfo) {
        boolean success = false;
        try {
            if (existLedgerOnBookie(bookieInfo)) {
                log.warnf("Bookie %s has ledgers assigned to it, will not delete cookie",
                        bookieInfo.getPodResource().get().getMetadata().getName());
                return false;
            }

            for (int i = 0; i < 2; i++) {
                // todo: figure out better way to check if cookie got deleted or change recover command
                String res = recoverAndDeleteCookieInZk(bookieInfo, true);
                if (res.contains("cookie is deleted") || res.contains("No cookie to remove")) {
                    // have to do that, otherwise init bookie will fail if PVC survives
                    deleteCookieOnDisk(bookieInfo);
                    success = true;
                    break;
                }
            }
        } catch (Exception e) {
            log.errorf(e, "Error while deleting a cookie for bookie %s",
                    bookieInfo.getPodResource().get().getMetadata().getName());
        }
        return success;
    }

    @SneakyThrows
    private boolean existLedgerOnBookie(BookieInfo bookieInfo) {
        final String podName = bookieInfo.getPodResource().get().getMetadata().getName();
        CompletableFuture<String> out = AutoscalerUtils.execInPod(client, namespace,
                podName,
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "bin/bookkeeper shell listledgers -meta -bookieid "
                        + getBookieId(bookieInfo.getPodResource()));
        out.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error running listledgers for bookie %s",
                        podName);
            } else {
                log.infof("listledgers for %s succeeded",
                        podName);
            }
        });
        String res = out.get();
        log.infof("listledgers output: %s", res);
        // error getting the info, err on the safe side
        if (res.contains("Unable to read the ledger")
                || res.contains("Received error return value while processing ledgers")
                || res.contains("Received Exception while processing ledgers")) {
            return true;
        }
        return res.contains("ledgerID: ");
    }

    private boolean checkIfCanScaleDown(String bookieUrl, double diskUsageLwm,
                                        List<BookieInfo> bookieInfos,
                                        Map<String, String> podSelector) {
        boolean canScaleDown = true;
        for (BookieInfo info : bookieInfos) {
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
                    log.infof("Not all disks are ready for %s",
                            info.getPodResource().get().getMetadata().getName());
                    return false;
                }
            }
        }

        boolean doesNotHaveUnderReplicatedLedgers = doesNotHaveUnderReplicatedLedgers(bookieUrl, podSelector);
        if (!doesNotHaveUnderReplicatedLedgers) {
            log.infof("Found underreplicated ledgers, can't scale down");
            canScaleDown = false;
        }

        return canScaleDown;
    }

    private ClusterStats collectClusterStats(double diskUsageHwm, List<BookieInfo> bookieInfos) {
        ClusterStats clusterStats = new ClusterStats();
        // ignoring racks for now
        for (BookieInfo info : bookieInfos) {
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

    private List<BookieInfo> collectBookieInfos(String bookieUrl, Map<String, String> withLabels) {
        List<BookieInfo> bookieInfos = client.pods().inNamespace(namespace).withLabels(withLabels).resources()
                .map(pod -> getBoookieInfo(bookieUrl, namespace, pod))
                .sorted(Comparator.comparing(b -> b.podResource.get().getMetadata().getName())).toList();
        return bookieInfos;
    }

    private int cleanupPvcs(Map<String, String> pvcSelector, BookKeeperSetSpec currentSpec) {
        log.infof("Checking PVCs for bookies with %s", pvcSelector);
        final AtomicInteger pvcCount = new AtomicInteger(0);
        client.persistentVolumeClaims()
                .inNamespace(namespace)
                .withLabels(pvcSelector)
                .list().getItems().forEach(pvc -> {
                    String name = pvc.getMetadata().getName();
                    if (isLedgersPvc(name, currentSpec) || isJournalPvc(name, currentSpec)) {
                        int idx = Integer.parseInt(name.substring(name.lastIndexOf('-') + 1));
                        if (idx >= currentSpec.getReplicas()) {
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

    private boolean isJournalPvc(String name, BookKeeperSetSpec currentSetSpec) {
        GlobalSpec globalSpec = clusterSpec.getGlobalSpec();
        String resourceName = BookKeeperResourcesFactory.getResourceName(globalSpec.getName(),
                globalSpec.getComponents().getBookkeeperBaseName(), bookkeeperSetName,
                currentSetSpec.getOverrideResourceName());
        String ledgersVolumeName = BookKeeperResourcesFactory
                .getJournalPvPrefix(clusterSpec.getBookkeeper(), resourceName);
        return name.startsWith(ledgersVolumeName);
    }

    private boolean isLedgersPvc(String name, BookKeeperSetSpec currentSetSpec) {
        GlobalSpec globalSpec = clusterSpec.getGlobalSpec();
        String resourceName = BookKeeperResourcesFactory.getResourceName(globalSpec.getName(),
                globalSpec.getComponents().getBookkeeperBaseName(), bookkeeperSetName,
                currentSetSpec.getOverrideResourceName());
        String ledgersVolumeName = BookKeeperResourcesFactory
                .getLedgersPvPrefix(clusterSpec.getBookkeeper(), resourceName);
        return name.startsWith(ledgersVolumeName);
    }

    @SneakyThrows
    private String recoverAndDeleteCookieInZk(BookieInfo bookieInfo, boolean deleteCookie) {
        final String podName = bookieInfo.getPodResource().get().getMetadata().getName();
        CompletableFuture<String> recoverOut = AutoscalerUtils.execInPod(client, namespace,
                podName,
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "bin/bookkeeper shell recover -f " + (deleteCookie ? "-d " : "")
                        + getBookieId(bookieInfo.getPodResource()));
        recoverOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error recovering bookie %s",
                        podName);
            } else {
                log.infof("Bookie %s recovered",
                        podName);
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
                        + "/pulsar/data/bookkeeper/journal/current/VERSION.old.$(head /dev/urandom | tr -dc a-z0-9 | "
                        + "head -c 8)");
        cookieOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error deleting cookie at %s",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            } else {
                log.infof("Bookie's %s cookie is deleted",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            }
        });
        String res = cookieOut.get();
        log.infof("Cookie delete output: %s", res);
    }

    protected String getBookieId(PodResource podResource) {
        Pod pod = podResource.get();
        return String.format("%s.%s-%s.%s.svc.%s:%s",
                pod.getSpec().getHostname(),
                clusterSpec.getGlobalSpec().getName(),
                clusterSpec.getGlobalSpec().getComponents().getBookkeeperBaseName(),
                namespace,
                clusterSpec.getGlobalSpec().getKubernetesClusterDomain(),
                "3181");
    }

    @SneakyThrows
    private void triggerAudit(String bookieUrl, BookieInfo bookieInfo) {
        CompletableFuture<String> curlOut = AutoscalerUtils.execInPod(client, namespace,
                bookieInfo.getPodResource().get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "curl -s -X PUT " + bookieUrl + "/api/v1/autorecovery/trigger_audit");
        curlOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error triggering audit %s",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            } else {
                log.infof("Triggered audit",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            }
        });
        curlOut.get();
    }

    @SneakyThrows
    protected void setReadOnly(String bookieUrl, BookieInfo bookieInfo, boolean roStatus) {
        CompletableFuture<String> curlOut = AutoscalerUtils.execInPod(client, namespace,
                bookieInfo.getPodResource().get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "curl -s -X PUT -H \"Content-Type: application/json\" "
                        + "-d '{\"readOnly\":" + roStatus + "}' "
                        + bookieUrl + "/api/v1/bookie/state/readonly");
        curlOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error making bookie read-only %s",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            } else {
                log.infof("Bookie %s is set to read-only %b",
                        roStatus,
                        bookieInfo.getPodResource().get().getMetadata().getName());
            }
        });

        curlOut.get();
    }

    @SneakyThrows
    private boolean doesNotHaveUnderReplicatedLedgers(String bookieUrl, Map<String, String> podSelector) {
        //TODO: improve BK API, add an option for true/false response if any UR ledger exists
        // to avoid long wait for the full list, return json
        /*
        $ curl -s localhost:8000/api/v1/autorecovery/list_under_replicated_ledger/
        No under replicated ledgers found
        */
        Optional<PodResource> pod =
                client.pods().inNamespace(namespace).withLabels(podSelector).resources().findFirst();
        if (pod.isEmpty()) {
            log.info("Could not get PodResource, assume something is underreplicated");
            return true;
        }
        CompletableFuture<String> urLedgersOut = AutoscalerUtils.execInPod(client, namespace,
                pod.get().get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                "curl -s " + bookieUrl + "/api/v1/autorecovery/list_under_replicated_ledger/");

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
    protected BookieInfo getBoookieInfo(String bookieUrl, String namespace, PodResource pod) {
        if (log.isDebugEnabled()) {
            log.debugf("getting BookieInfo for pod ()", pod.get().getMetadata().getName());
        }

        CompletableFuture<String> bkStateOut =
                AutoscalerUtils.execInPod(client, namespace, pod.get().getMetadata().getName(),
                        BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                        "curl -s " + bookieUrl + "/api/v1/bookie/state");

        CompletableFuture<String> bkInfoOut =
                AutoscalerUtils.execInPod(client, namespace, pod.get().getMetadata().getName(),
                        BookKeeperResourcesFactory.getBookKeeperContainerName(clusterSpec.getGlobalSpec()),
                        "curl -s " + bookieUrl + "/api/v1/bookie/info");

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
    private boolean parseIsWritable(String bkStateOutput)
            throws JsonProcessingException, InterruptedException, ExecutionException {
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
}
