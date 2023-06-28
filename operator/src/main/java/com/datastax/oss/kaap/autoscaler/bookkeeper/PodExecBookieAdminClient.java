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
package com.datastax.oss.kaap.autoscaler.bookkeeper;

import com.datastax.oss.kaap.autoscaler.AutoscalerUtils;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSetSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class PodExecBookieAdminClient implements BookieAdminClient {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private final KubernetesClient client;
    private final String namespace;
    private final GlobalSpec globalSpec;
    private final String bookkeeperSetName;
    private final BookKeeperSetSpec currentBookKeeperSetSpec;

    private final String bookieAdminUrl;
    private final Map<String, String> podSelector;
    private List<BookieInfo> bookieInfos;


    public PodExecBookieAdminClient(KubernetesClient client, String namespace,
                                    GlobalSpec globalSpec, String bookkeeperSetName,
                                    BookKeeperSetSpec currentBookKeeperSetSpec) {
        this.client = client;
        this.namespace = namespace;
        this.globalSpec = globalSpec;
        this.bookkeeperSetName = bookkeeperSetName;
        this.currentBookKeeperSetSpec = currentBookKeeperSetSpec;

        this.bookieAdminUrl = computeBookieUrl();
        if (log.isDebugEnabled()) {
            log.debugf("Using bookie url %s to access REST API", bookieAdminUrl);
        }
        this.podSelector = new TreeMap<>(Map.of(
                CRDConstants.LABEL_CLUSTER, globalSpec.getName(),
                CRDConstants.LABEL_COMPONENT, BookKeeperResourcesFactory
                        .getComponentBaseName(globalSpec),
                CRDConstants.LABEL_RESOURCESET, bookkeeperSetName));
    }

    @Override
    public List<BookieInfo> collectBookieInfos() {
        this.bookieInfos = client.pods().inNamespace(namespace).withLabels(podSelector).resources()
                .map(pod -> getBookieInfo(pod))
                .sorted(Comparator.comparing(b -> b.podResource.get().getMetadata().getName())).toList();
        return bookieInfos;
    }

    private List<BookieInfo> getBookieInfos() {
        if (bookieInfos == null) {
            collectBookieInfos();
        }
        return bookieInfos;
    }

    @SneakyThrows
    protected BookieInfo getBookieInfo(PodResource pod) {
        if (log.isDebugEnabled()) {
            log.debugf("getting BookieInfo for pod %s", pod.get().getMetadata().getName());
        }


        return BookieInfo.builder()
                .podResource(pod)
                .bookieId(getBookieId(pod))
                .build();
    }

    @Override
    @SneakyThrows
    public BookieStats collectBookieStats(BookieInfo bookieInfo) {
        final Pod pod = bookieInfo.getPodResource().get();

        CompletableFuture<String> bkStateOut =
                AutoscalerUtils.execInPod(client, namespace, pod.getMetadata().getName(),
                        BookKeeperResourcesFactory.getBookKeeperContainerName(globalSpec),
                        "curl -s " + bookieAdminUrl + "/api/v1/bookie/state");

        CompletableFuture<String> bkInfoOut =
                AutoscalerUtils.execInPod(client, namespace, pod.getMetadata().getName(),
                        BookKeeperResourcesFactory.getBookKeeperContainerName(globalSpec),
                        "curl -s " + bookieAdminUrl + "/api/v1/bookie/info");

        List<BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
        final BookieLedgerDiskInfo diskInfo = parseAndFillDiskUsage(bkInfoOut.get(), pod);
        if (diskInfo != null) {
            ledgerDiskInfos.add(diskInfo);
        }

        boolean writable = parseIsWritable(bkStateOut.get());
        return BookieStats.builder()
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
    private BookieLedgerDiskInfo parseAndFillDiskUsage(String bkStateOutput, Pod pod) {
        /*
        $ curl -s localhost:8000/api/v1/bookie/info
        {
          "freeSpace" : 49769177088,
          "totalSpace" : 101129359360
        }
        */
        JsonNode node = MAPPER.readTree(bkStateOutput);
        if (!node.has("totalSpace") || !node.has("freeSpace")) {
            throw new IllegalStateException(
                    "invalid bookie info for bookie pod %s, got: %s".formatted(pod.getMetadata().getName(),
                            bkStateOutput));
        }
        long total = node.get("totalSpace").asLong(0);
        long free = node.get("freeSpace").asLong(Long.MAX_VALUE);

        return BookieLedgerDiskInfo.builder()
                .maxBytes(total)
                .usedBytes(total - free)
                .build();
    }


    @Override
    @SneakyThrows
    public void setReadOnly(BookieInfo bookieInfo, boolean readonly) {
        CompletableFuture<String> curlOut = AutoscalerUtils.execInPod(client, namespace,
                bookieInfo.getPodResource().get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(globalSpec),
                "curl -s -X PUT -H \"Content-Type: application/json\" "
                        + "-d '{\"readOnly\":" + readonly + "}' "
                        + bookieAdminUrl + "/api/v1/bookie/state/readonly");
        curlOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error making bookie read-only %s",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            } else {
                log.infof("Bookie %s is set to read-only=%b",
                        bookieInfo.getPodResource().get().getMetadata().getName(),
                        readonly);
            }
        });
        curlOut.get();
    }

    @Override
    @SneakyThrows
    public void recoverAndDeleteCookieInZk(BookieInfo bookieInfo, boolean deleteCookie) {
        final String podName = bookieInfo.getPodResource().get().getMetadata().getName();
        String res = internalRecoverAndDeleteCookieInZk(bookieInfo, deleteCookie);
        log.debugf("Recover output: %s", res);
        if (!deleteCookie) {
            if (!res.contains(
                    "Recover bookie operation completed with rc: OK: No problem")) {
                log.warnf("Recovery failed for bookie %s \n %s",
                        podName, res);
                throw new IllegalStateException("Recovery failed for bookie " + podName);
            }
        } else {
            // todo: figure out better way to check if cookie got deleted or change recover command
            res = internalRecoverAndDeleteCookieInZk(bookieInfo, true);
            if (res.contains("cookie is deleted") || res.contains("No cookie to remove")) {
                return;
            }
            throw new IllegalStateException("Error while deleting cookie for bookie " + podName);
        }
    }

    @SneakyThrows
    private String internalRecoverAndDeleteCookieInZk(BookieInfo bookieInfo, boolean deleteCookie) {
        final String podName = bookieInfo.getPodResource().get().getMetadata().getName();
        final long start = System.nanoTime();
        log.info("Starting bookie recovery for bookie " + podName);
        CompletableFuture<String> recoverOut = AutoscalerUtils.execInPod(client, namespace,
                podName,
                BookKeeperResourcesFactory.getBookKeeperContainerName(globalSpec),
                "bin/bookkeeper shell recover -f " + (deleteCookie ? "-d " : "")
                        + getBookieId(bookieInfo.getPodResource()));
        recoverOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error recovering bookie %s",
                        podName);
            } else {
                log.infof("Bookie %s recovered in %d ms",
                        podName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            }
        });
        return recoverOut.get();
    }

    @Override
    @SneakyThrows
    public boolean existsLedger(BookieInfo bookieInfo) {
        final String podName = bookieInfo.getPodResource().get().getMetadata().getName();
        CompletableFuture<String> out = AutoscalerUtils.execInPod(client, namespace,
                podName,
                BookKeeperResourcesFactory.getBookKeeperContainerName(globalSpec),
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
        String res = out.get(1, TimeUnit.MINUTES);
        log.debugf("listledgers output: %s", res);
        // error getting the info, err on the safe side
        if (res.contains("Unable to read the ledger")
                || res.contains("Received error return value while processing ledgers")
                || res.contains("Received Exception while processing ledgers")) {
            return true;
        }
        return res.contains("ledgerID: ");
    }

    @Override
    @SneakyThrows
    public boolean doesNotHaveUnderReplicatedLedgers() {
        //TODO: improve BK API, add an option for true/false response if any UR ledger exists
        // to avoid long wait for the full list, return json
        /*
        $ curl -s localhost:8000/api/v1/autorecovery/list_under_replicated_ledger/
        No under replicated ledgers found
        */
        final PodResource pod = getBookieInfos().get(0).getPodResource();
        CompletableFuture<String> urLedgersOut = AutoscalerUtils.execInPod(client, namespace,
                pod.get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(globalSpec),
                "curl -s " + bookieAdminUrl + "/api/v1/autorecovery/list_under_replicated_ledger/");

        final String s = urLedgersOut.get(1, TimeUnit.MINUTES);
        return s.contains("No under replicated ledgers found");
    }


    private String computeBookieUrl() {
        final String configKey = "%s%s".formatted(BaseResourcesFactory.CONFIG_PULSAR_PREFIX, "httpServerPort");
        final Map<String, Object> config = currentBookKeeperSetSpec.getConfig();
        final Object port;
        if (config == null || !config.containsKey(configKey)) {
            port = BookKeeperResourcesFactory.DEFAULT_HTTP_PORT;
        } else {
            port = config.getOrDefault(configKey, BookKeeperResourcesFactory.DEFAULT_HTTP_PORT);
        }
        return "%s://%s:%s".formatted(
                BaseResourcesFactory.isTlsEnabledOnBookKeeper(globalSpec) ? "https" : "http",
                "localhost",
                String.valueOf(port)
        );
    }

    @Override
    @SneakyThrows
    public void triggerAudit() {
        final BookieInfo bookieInfo = getBookieInfos().get(0);
        CompletableFuture<String> curlOut = AutoscalerUtils.execInPod(client, namespace,
                bookieInfo.getPodResource().get().getMetadata().getName(),
                BookKeeperResourcesFactory.getBookKeeperContainerName(globalSpec),
                "curl -s -X PUT " + bookieAdminUrl + "/api/v1/autorecovery/trigger_audit");
        curlOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error triggering audit %s",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            } else {
                log.infof("Triggered audit",
                        bookieInfo.getPodResource().get().getMetadata().getName());
            }
        });
        curlOut.get(1, TimeUnit.MINUTES);
    }


    protected String getBookieId(PodResource podResource) {
        Pod pod = podResource.get();
        return getBookieId(pod, bookkeeperSetName, currentBookKeeperSetSpec, globalSpec, namespace);
    }

    public static String getBookieId(Pod pod, String bookieSet,
                                     BookKeeperSetSpec setSpec,
                                     GlobalSpec globalSpec,
                                     String namespace) {
        return getBookieId(pod.getSpec().getHostname(), bookieSet, setSpec, globalSpec, namespace);
    }

    public static String getBookieId(String podHostname, String bookieSet,
                                     BookKeeperSetSpec setSpec,
                                     GlobalSpec globalSpec,
                                     String namespace) {
        // https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#a-aaaa-records-1
        // <pod-hostname>.<service-name>.<namespace>.svc.<cluster-domain>

        // note that this might depend on bk configs:
        // - 'useHostNameAsBookieID'
        // - 'useShortHostName'
        // - 'advertisedAddress'

        final String svcName = BookKeeperResourcesFactory.getResourceName(globalSpec.getName(),
                globalSpec.getComponents().getBookkeeperBaseName(), bookieSet, setSpec.getOverrideResourceName());
        return String.format("%s.%s.%s.svc.%s:%d",
                podHostname,
                svcName,
                namespace,
                globalSpec.getKubernetesClusterDomain(),
                BookKeeperResourcesFactory.DEFAULT_BK_PORT);
    }

    @Override
    @SneakyThrows
    public void deleteCookieOnDisk(BookieInfo bookieInfo) {
        // moving rather than deleting, into a random name
        final String podName = bookieInfo.getPodResource().get().getMetadata().getName();
        CompletableFuture<String> cookieOut = AutoscalerUtils.execInPod(client, namespace,
                podName,
                BookKeeperResourcesFactory.getBookKeeperContainerName(globalSpec),
                "mv /pulsar/data/bookkeeper/journal/current/VERSION "
                        + "/pulsar/data/bookkeeper/journal/current/VERSION.old.$(head /dev/urandom | tr -dc a-z0-9 | "
                        + "head -c 8)");
        cookieOut.whenComplete((s, e) -> {
            if (e != null) {
                log.errorf(e, "Error deleting cookie at %s", podName);
            } else {
                log.infof("Bookie's %s cookie is deleted",  podName);
            }
        });
        String res = cookieOut.get();
        log.debugf("Cookie delete output: %s", res);
    }
}
