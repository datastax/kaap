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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
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

            canScaleDown = canScaleDown && doesNotHaveUnderReplicatedLedgers();

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
        long used = node.get("freeSpace").asLong(Long.MAX_VALUE);
        diskInfo.setMaxBytes(total);
        diskInfo.setUsedBytes(used);
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
