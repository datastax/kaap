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
import com.datastax.oss.kaap.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeper;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatusBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.ContainerMetricsBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsBuilder;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsList;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.PodMetricsListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.fabric8.kubernetes.client.server.mock.OutputStreamMessage;
import io.fabric8.mockwebserver.utils.BodyProvider;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.lang3.tuple.Pair;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BookKeeperAutoscalerTest {

    private static final String NAMESPACE = "ns";

    @Builder(setterPrefix = "with")
    public static class MockServer implements AutoCloseable {

        @FunctionalInterface
        public interface PodConsumer {

            void accept(Pod pod, PodMetrics metrics, int index);
        }

        private PulsarClusterSpec pulsarClusterSpec;
        private PodConsumer podConsumer;
        private Consumer<StatefulSet> stsConsumer;
        KubernetesServer server;

        PatchOp patchOp;

        @Data
        public static class PatchOp {
            String op;
            String path;
            Object value;
        }

        @SneakyThrows
        void start() {
            pulsarClusterSpec.getGlobal().applyDefaults(null);
            pulsarClusterSpec.getBookkeeper().applyDefaults(pulsarClusterSpec.getGlobalSpec());

            final BookKeeper bkCr = new BookKeeper();
            bkCr.setSpec(BookKeeperFullSpec.builder()
                    .global(pulsarClusterSpec.getGlobal())
                    .bookkeeper(pulsarClusterSpec.getBookkeeper())
                    .build());

            final String clusterName = pulsarClusterSpec.getGlobal().getName();

            server = new KubernetesServer(false);
            server.before();

            final int replicas = pulsarClusterSpec.getBookkeeper().getReplicas();

            final BookKeeperResourcesFactory bkResourcesFactory =
                    new BookKeeperResourcesFactory(null, NAMESPACE, BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET,
                            pulsarClusterSpec.getBookkeeper(),
                            pulsarClusterSpec.getGlobal(), null);

            final Field field = bkResourcesFactory.getClass().getDeclaredField("configMap");
            field.setAccessible(true);
            field.set(bkResourcesFactory, new ConfigMapBuilder().build());
            final StatefulSet sts = bkResourcesFactory.generateStatefulSet();
            sts.setStatus(new StatefulSetStatusBuilder()
                    .withReplicas(replicas)
                    .withReadyReplicas(replicas)
                    .withUpdatedReplicas(replicas)
                    .withCurrentRevision("rev")
                    .withUpdateRevision("rev")
                    .build());

            stsConsumer.accept(sts);

            server.expect()
                    .get()
                    .withPath("/apis/apps/v1/namespaces/ns/statefulsets/%s-bookkeeper".formatted(clusterName))
                    .andReturn(HttpURLConnection.HTTP_OK, sts)
                    .once();

            server.expect()
                    .get()
                    .withPath(
                            "/apis/kaap.oss.datastax.com/v1alpha1/namespaces/ns/bookkeepers/%s-bookkeeper".formatted(
                                    clusterName))
                    .andReturn(HttpURLConnection.HTTP_OK, bkCr)
                    .times(2);


            List<Pod> pods = new ArrayList<>();
            List<PodMetrics> podsMetrics = new ArrayList<>();

            for (int i = 0; i < replicas; i++) {
                final String podName = "%s-bookkeeper-%d".formatted(clusterName, i);
                final Pod pod = new PodBuilder()
                        .withNewMetadata()
                        .withName(podName)
                        .endMetadata()
                        .withSpec(sts.getSpec().getTemplate().getSpec())
                        .withStatus(
                                new PodStatusBuilder()
                                        .withContainerStatuses(
                                                new ContainerStatusBuilder()
                                                        .withReady(true)
                                                        .build()
                                        )
                                        // more than default (stabilizationWindowMs)
                                        .withStartTime(Instant.now().minusSeconds(500).toString())
                                        .build())
                        .build();


                final PodMetrics podMetrics = new PodMetricsBuilder()
                        .withNewMetadata()
                        .withName(podName)
                        .endMetadata()
                        .withContainers(
                                new ContainerMetricsBuilder()
                                        .withUsage(Map.of("cpu", Quantity.parse("300Mi")))
                                        .build()
                        )
                        .build();
                podConsumer.accept(pod, podMetrics, i);
                pods.add(pod);
                podsMetrics.add(podMetrics);

                server.expect()
                        .get()
                        .withPath("/api/v1/namespaces/ns/pods/%s".formatted(podName))
                        .andReturn(HttpURLConnection.HTTP_OK, pod)
                        .always();
            }
            final PodList podList = new PodListBuilder()
                    .withItems(pods)
                    .build();

            server.expect()
                    .get()
                    .withPath("/api/v1/namespaces/ns/pods?labelSelector=%s".formatted(
                                    URLEncoder.encode(
                                            "cluster=%s,component=bookkeeper,resource-set=bookkeeper".formatted(clusterName),
                                            StandardCharsets.UTF_8)
                            )
                    )
                    .andReturn(HttpURLConnection.HTTP_OK, podList)
                    .always();

            final PodMetricsList podMetricsList = new PodMetricsListBuilder()
                    .withItems(podsMetrics)
                    .build();

            server.expect()
                    .get()
                    .withPath("/apis/metrics.k8s.io/v1beta1/namespaces/ns/pods?labelSelector=%s".formatted(
                                    URLEncoder.encode(
                                            "cluster=%s,component=bookkeeper,resource-set=bookkeeper".formatted(clusterName),
                                            StandardCharsets.UTF_8)
                            )
                    )
                    .andReturn(HttpURLConnection.HTTP_OK, podMetricsList)
                    .once();

            server.expect()
                    .patch()
                    .withPath(
                            "/apis/kaap.oss.datastax.com/v1alpha1/namespaces/ns/bookkeepers/%s-bookkeeper".formatted(
                                    clusterName))
                    .andReply(HttpURLConnection.HTTP_OK, new BodyProvider<Object>() {
                        @Override
                        @SneakyThrows
                        public Object getBody(RecordedRequest recordedRequest) {
                            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            recordedRequest.getBody().copyTo(byteArrayOutputStream);
                            final ObjectMapper mapper = new ObjectMapper();
                            patchOp = mapper.convertValue(
                                    mapper.readValue(byteArrayOutputStream.toByteArray(), List.class).get(0),
                                    PatchOp.class);
                            return null;
                        }
                    })
                    .once();
        }

        @Override
        public void close() {
            server.after();
        }
    }

    /**
     * Test that output of "df -k" is used and parsed correctly
     */
    @Test
    public void testRestAPIParsing() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 3
                    autoscaler:
                        enabled: true
                """;

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                },
                null, server -> {
                    String bookieInfoOk = """
                            {
                              "freeSpace" : 49769177088,
                              "totalSpace" : 101129359360
                            }
                            """;
                    String bookieStateOk = """
                            {
                              "running" : true,
                              "readOnly" : false,
                              "shuttingDown" : false,
                              "availableForHighPriorityWrites" : true
                            }
                            """;
                    String bookieStateReadOnly = """
                            {
                              "running" : true,
                              "readOnly" : true,
                              "shuttingDown" : false,
                              "availableForHighPriorityWrites" : true
                            }
                            """;
                    for (int i = 0; i < 3; i++) {
                        // Bookie info
                        server.server.expect()
                                .get()
                                .withPath(genExpectedUrlForExecInPod("pul-bookkeeper-" + i,
                                        "curl -s http://localhost:8000/api/v1/bookie/info"))
                                .andUpgradeToWebSocket()
                                .open(new OutputStreamMessage(bookieInfoOk))
                                .done()
                                .always();

                        // Bookie state
                        String response = i == 0 ? bookieStateReadOnly : bookieStateOk;
                        server.server.expect()
                                .get()
                                .withPath(genExpectedUrlForExecInPod("pul-bookkeeper-" + i,
                                        "curl -s http://localhost:8000/api/v1/bookie/state"))
                                .andUpgradeToWebSocket()
                                .open(new OutputStreamMessage(response))
                                .done()
                                .always();
                        // AR list under replicated
                        server.server.expect()
                                .get()
                                .withPath(genExpectedUrlForExecInPod("pul-bookkeeper-" + i,
                                        "curl -s http://localhost:8000/api/v1/autorecovery"
                                                + "/list_under_replicated_ledger/"))
                                .andUpgradeToWebSocket()
                                .open(new OutputStreamMessage("No under replicated ledgers found"))
                                .done()
                                .always();
                    }
                });
        Assert.assertEquals(4, mockServer.patchOp.getValue());
    }

    /**
     * All is good, nothing to do
     */
    @Test
    public void testNoScaleUpOrDownNeeded() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 3
                    autoscaler:
                        enabled: true
                """;

        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    long usedBytes = 100000;

                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000)
                                    .usedBytes(usedBytes)
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(true)
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                },
                bookieInfofunc);
        Assert.assertNull(mockServer.patchOp);
    }

    private PodResource getMockPodResource() {
        return getMockPodResource("dummyPodName");
    }

    private PodResource getMockPodResource(String podName) {
        PodResource pr = Mockito.mock(PodResource.class);
        ObjectMeta meta = new ObjectMeta();
        meta.setName(podName);
        Mockito.when(pr.get()).thenReturn(new PodBuilder().withMetadata(meta).build());
        return pr;
    }

    /**
     * 2 out of 3 bookies are read-only:
     * add enough bookies to get to 3 writable
     */
    @Test
    public void testScaleUpReadOnlyBookie() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 3
                    autoscaler:
                        enabled: true
                """;

        final AtomicInteger count = new AtomicInteger(0);
        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    boolean isWritable = true;
                    long usedBytes = 100000;

                    if (count.getAndIncrement() != 0) {
                        isWritable = false;
                    }

                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000)
                                    .usedBytes(usedBytes)
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(isWritable)
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                },
                bookieInfofunc);
        Assert.assertEquals(5, mockServer.patchOp.getValue());
    }

    /**
     * Scale up from zero bookies
     */
    @Test
    public void testScaleUpFromZeroBookies() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 0
                    autoscaler:
                        enabled: true
                """;

        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    boolean isWritable = true;
                    long usedBytes = 100000;
                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000)
                                    .usedBytes(usedBytes)
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(isWritable)
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                },
                bookieInfofunc);
        Assert.assertEquals(3, mockServer.patchOp.getValue());
    }

    /**
     * All bookies are writable but some are at risk.
     */
    @Test
    public void testScaleUpLowDiskSpaceBookie() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 3
                    autoscaler:
                        enabled: true
                """;

        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    boolean isWritable = true;
                    long usedBytes = 990000;

                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000)
                                    .usedBytes(usedBytes)
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(isWritable)
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                },
                bookieInfofunc);
        Assert.assertEquals(4, mockServer.patchOp.getValue());
    }

    /**
     * One bookie is read only and one is at risk
     */
    @Test
    public void testScaleUpLowDiskSpaceAndReadOnlyBookies() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 3
                    autoscaler:
                        enabled: true
                """;

        final AtomicInteger count = new AtomicInteger(0);
        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    boolean isWritable = true;
                    long usedBytes = 100000;

                    int idx = count.getAndIncrement();
                    if (idx == 0) {
                        isWritable = false;
                    } else if (idx == 1) {
                        usedBytes = 990000;
                    }

                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000)
                                    .usedBytes(usedBytes)
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(isWritable)
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                },
                bookieInfofunc);
        Assert.assertEquals(5, mockServer.patchOp.getValue());
    }

    /**
     * All bookies are writable and disk usages below LWM
     */
    @Test
    public void testScaleDownBookie() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 4
                    autoscaler:
                        enabled: true
                """;

        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    boolean isWritable = true;
                    long usedBytes = 10000;

                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000)
                                    .usedBytes(usedBytes)
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(isWritable)
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                },
                bookieInfofunc, server -> {
                    for (int i = 0; i < 4; i++) {
                        // AR list under replicated
                        server.server.expect()
                                .get()
                                .withPath(genExpectedUrlForExecInPod("pul-bookkeeper-" + i,
                                        "curl -s http://localhost:8000/api/v1/autorecovery"
                                                + "/list_under_replicated_ledger/"))
                                .andUpgradeToWebSocket()
                                .open(new OutputStreamMessage("No under replicated ledgers found"))
                                .done()
                                .always();

                        server.server.expect()
                                .get()
                                .withPath(genExpectedUrlForExecInPod("pul-bookkeeper-" + i,
                                        "curl -s -X PUT -H \"Content-Type: application/json\" "
                                                + "-d '{\"readOnly\":true}' "
                                                + "http://localhost:8000/api/v1/bookie/state/readonly"))
                                .andUpgradeToWebSocket()
                                .open(new OutputStreamMessage("dummy"))
                                .done()
                                .always();
                    }


                    server.server.expect()
                            .get()
                            .withPath(genExpectedUrlForExecInPod("pul-bookkeeper-3",
                                    "bin/bookkeeper shell recover -f mockId"))
                            .andUpgradeToWebSocket()
                            .open(new OutputStreamMessage("Recover bookie operation completed with rc: OK: No problem"))
                            .done()
                            .always();

                    server.server.expect()
                            .get()
                            .withPath(genExpectedUrlForExecInPod("pul-bookkeeper-3",
                                    "bin/bookkeeper shell recover -f -d mockId"))
                            .andUpgradeToWebSocket()
                            .open(new OutputStreamMessage("cookie is deleted"))
                            .done()
                            .always();

                    server.server.expect()
                            .get()
                            .withPath(genExpectedUrlForExecInPod("pul-bookkeeper-3",
                                    "bin/bookkeeper shell listledgers -meta -bookieid mockId"))
                            .andUpgradeToWebSocket()
                            .open(new OutputStreamMessage(""))
                            .done()
                            .always();

                    server.server.expect()
                            .get()
                            .withPath(genExpectedUrlForExecInPod("pul-bookkeeper-3",
                                    "mv /pulsar/data/bookkeeper/journal/current/VERSION "
                                            + "/pulsar/data/bookkeeper/journal/current/VERSION.old.$(head "
                                            + "/dev/urandom | tr -dc a-z0-9 | head -c 8)"))
                            .andUpgradeToWebSocket()
                            .open(new OutputStreamMessage(""))
                            .done()
                            .always();
                });
        Assert.assertEquals(3, mockServer.patchOp.getValue());
    }

    /**
     * All bookies are writable and disk usages below LWM
     * but under replicated ledgers exist.
     */
    @Test
    public void testNotScaleDownUnderReplicatedCluster() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 4
                    autoscaler:
                        enabled: true
                """;

        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    boolean isWritable = true;
                    long usedBytes = 10000;

                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000)
                                    .usedBytes(usedBytes)
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(isWritable)
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                },
                bookieInfofunc, server -> {
                    for (int i = 0; i < 4; i++) {
                        final String podName = "%s-bookkeeper-%d".formatted("pul", i);
                        server.server.expect()
                                .get()
                                .withPath(genExpectedUrlForExecInPod(podName,
                                        "curl -s http://localhost:8000/api/v1/autorecovery"
                                                + "/list_under_replicated_ledger/"))
                                .andUpgradeToWebSocket()
                                .open(new OutputStreamMessage("blah blah"))
                                .done()
                                .always();
                    }
                });
        Assert.assertNull(mockServer.patchOp);
    }

    /**
     * Don't scale down if there is a read-only bookie
     */
    @Test
    public void testNotScaleDownReadOnlyBookie() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 4
                    autoscaler:
                        enabled: true
                """;

        AtomicBoolean isWritable = new AtomicBoolean(false);
        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    long usedBytes = 10000;

                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000)
                                    .usedBytes(usedBytes)
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(isWritable.getAndSet(true))
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                },
                bookieInfofunc);
        Assert.assertNull(mockServer.patchOp);
    }

    /**
     * Don't scale down if there is a full bookie
     */
    @Test
    public void testNotScaleDownFullBookie() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 5
                    autoscaler:
                        enabled: true
                """;

        AtomicLong usedBytes = new AtomicLong(990000L);
        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    boolean isWritable = true;

                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000L)
                                    .usedBytes(usedBytes.getAndSet(100000L))
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(isWritable)
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                },
                bookieInfofunc);

        Assert.assertNull(mockServer.patchOp);
    }

    @Test
    public void testStsNotReady() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 3
                    autoscaler:
                        enabled: true
                """;
        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    boolean isWritable = true;

                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000L)
                                    .usedBytes(990000L)
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(isWritable)
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                }, statefulSet -> {
                    statefulSet.getStatus().setReadyReplicas(2);
                },
                bookieInfofunc);
        Assert.assertNull(mockServer.patchOp);
    }

    @Test
    public void testPodJustStarted() {
        final String spec = """
                global:
                   name: pul
                bookkeeper:
                    replicas: 3
                    autoscaler:
                        enabled: true
                """;
        Function<PodResource, Pair<BookieAdminClient.BookieInfo, BookieAdminClient.BookieStats>> bookieInfofunc =
                podSpec -> {
                    boolean isWritable = true;

                    List<BookieAdminClient.BookieLedgerDiskInfo> ledgerDiskInfos = new ArrayList<>(1);
                    BookieAdminClient.BookieLedgerDiskInfo diskInfo =
                            BookieAdminClient.BookieLedgerDiskInfo.builder()
                                    .maxBytes(1000000L)
                                    .usedBytes(990000L)
                                    .build();
                    ledgerDiskInfos.add(diskInfo);

                    return Pair.of(BookieAdminClient.BookieInfo.builder()
                                    .podResource(podSpec)
                                    .build(),
                            BookieAdminClient.BookieStats.builder()
                                    .isWritable(isWritable)
                                    .ledgerDiskInfos(ledgerDiskInfos)
                                    .build()
                    );
                };

        final MockServer mockServer = runAutoscaler(spec, (pod, metrics, i) -> {
                    if (i == 2) {
                        pod.getStatus().setStartTime(Instant.now().minusSeconds(3).toString());
                    }
                }, statefulSet -> {
                },
                bookieInfofunc);
        Assert.assertNull(mockServer.patchOp);
    }

    private MockServer runAutoscaler(String spec, MockServer.PodConsumer podConf, Consumer<StatefulSet> stsConf,
                                     Function<PodResource, Pair<BookieAdminClient.BookieInfo,
                                             BookieAdminClient.BookieStats>> bookieInfofunc) {
        return runAutoscaler(spec, podConf, stsConf, bookieInfofunc, x -> {
        });
    }

    private MockServer runAutoscaler(String spec, MockServer.PodConsumer podConf, Consumer<StatefulSet> stsConf,
                                     Function<PodResource, Pair<BookieAdminClient.BookieInfo,
                                             BookieAdminClient.BookieStats>> bookieInfofunc,
                                     Consumer<MockServer> serverAfter) {
        final PulsarClusterSpec pulsarClusterSpec = MockKubernetesClient.readYaml(spec, PulsarClusterSpec.class);
        try (final MockServer server = MockServer.builder()
                .withPulsarClusterSpec(pulsarClusterSpec)
                .withPodConsumer(podConf)
                .withStsConsumer(stsConf)
                .build()) {
            server.start();
            serverAfter.accept(server);

            BookKeeperSetAutoscaler bkAutoscaler =
                    new BookKeeperSetAutoscaler(server.server.getClient(), NAMESPACE,
                            BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET, pulsarClusterSpec) {
                        @Override
                        protected BookieAdminClient newBookieAdminClient(GlobalSpec currentGlobalSpec,
                                                                         BookKeeperSetSpec currentBookKeeperSetSpec) {
                            return new MockBookieAdminClient(server.server.getClient(), NAMESPACE,
                                    pulsarClusterSpec.getGlobalSpec(),
                                    BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET,
                                    pulsarClusterSpec.getBookkeeper(),
                                    bookieInfofunc);
                        }
                    };
            bkAutoscaler.internalRun();
            return server;
        }

    }

    private static class MockBookieAdminClient extends PodExecBookieAdminClient {
        Function<PodResource, Pair<BookieInfo, BookieStats>> bookieInfofunc;
        Map<String, Pair<BookieInfo, BookieStats>> functionResult = new HashMap<>();

        public MockBookieAdminClient(KubernetesClient client, String namespace,
                                     GlobalSpec globalSpec,
                                     String bookkeeperSetName,
                                     BookKeeperSetSpec currentBookKeeperSetSpec,
                                     Function<PodResource, Pair<BookieInfo, BookieStats>> bookieInfofunc) {
            super(client, namespace, globalSpec, bookkeeperSetName, currentBookKeeperSetSpec);
            this.bookieInfofunc = bookieInfofunc;
        }

        @Override
        protected BookieInfo getBookieInfo(PodResource pod) {
            if (bookieInfofunc != null) {
                final Pair<BookieInfo, BookieStats> res = bookieInfofunc.apply(pod);
                System.out.println("putting result in " + pod.get().getMetadata().getName() + " " + res);
                functionResult.put(pod.get().getMetadata().getName(), res);
                return res.getLeft();
            }
            return super.getBookieInfo(pod);
        }

        @Override
        public BookieStats collectBookieStats(BookieInfo bookieInfo) {
            if (bookieInfofunc != null) {

                final String k = bookieInfo.getPodResource().get().getMetadata().getName();
                System.out.println("getting result with " + k);
                return functionResult.get(k).getRight();
            }
            return super.collectBookieStats(bookieInfo);
        }

        @Override
        protected String getBookieId(PodResource podResource) {
            return "mockId";
        }

        @Override
        public void setReadOnly(BookieInfo bookieInfo, boolean readonly) {
        }

    }

    private static String genExpectedUrlForExecInPod(String podName, String cmd) {
        return "/api/v1/namespaces/ns/pods/"
                + podName
                + "/exec?command=bash&command=-c&command="
                + URLEncoder.encode(cmd, StandardCharsets.UTF_8).replace("+", "%20")
                + "&container=pul-bookkeeper&stdout=true&stderr=true";
    }
}
