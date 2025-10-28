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

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.kaap.crds.broker.Broker;
import com.datastax.oss.kaap.crds.broker.BrokerFullSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ContainerStatusBuilder;
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
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.http.RecordedRequest;
import io.fabric8.mockwebserver.utils.BodyProvider;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.Data;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@EnableKubernetesMockClient(https = false)
public class BrokerAutoscalerTest {

    private static final String NAMESPACE = "ns";

    KubernetesMockServer server;
    KubernetesClient client;

    @FunctionalInterface
    public interface PodConsumer {

        void accept(Pod pod, PodMetrics metrics, int index);
    }

    @Data
    public static class PatchOp {
        String op;
        String path;
        Object value;
    }

    @SneakyThrows
    private PatchOp setupMocksAndRunAutoscaler(
            String spec,
            PodConsumer podConf,
            Consumer<StatefulSet> stsConf) {

        final PulsarClusterSpec pulsarClusterSpec = SerializationUtil.readYaml(spec, PulsarClusterSpec.class);
        pulsarClusterSpec.getGlobal().applyDefaults(null);
        pulsarClusterSpec.getBroker().applyDefaults(pulsarClusterSpec.getGlobalSpec());

        final Broker brokerCr = new Broker();
        brokerCr.setSpec(BrokerFullSpec.builder()
                .global(pulsarClusterSpec.getGlobal())
                .broker(pulsarClusterSpec.getBroker())
                .build());

        final String clusterSpecName = pulsarClusterSpec.getGlobal().getName();

        final PatchOp[] capturedPatch = new PatchOp[1];

        final int replicas = pulsarClusterSpec.getBroker().getReplicas();

        final BrokerResourcesFactory brokerResourcesFactory =
                new BrokerResourcesFactory(null, NAMESPACE, BrokerResourcesFactory.BROKER_DEFAULT_SET,
                        pulsarClusterSpec.getBroker(),
                        pulsarClusterSpec.getGlobal(), null);

        final Field field = brokerResourcesFactory.getClass().getDeclaredField("configMap");
        field.setAccessible(true);
        field.set(brokerResourcesFactory, new ConfigMapBuilder().build());
        final StatefulSet sts = brokerResourcesFactory.generateStatefulSet();
        sts.setStatus(new StatefulSetStatusBuilder()
                .withReplicas(replicas)
                .withReadyReplicas(replicas)
                .withUpdatedReplicas(replicas)
                .withCurrentRevision("rev")
                .withUpdateRevision("rev")
                .build());

        stsConf.accept(sts);

        server.expect()
                .get()
                .withPath("/apis/apps/v1/namespaces/ns/statefulsets/%s-broker".formatted(clusterSpecName))
                .andReturn(HttpURLConnection.HTTP_OK, sts)
                .once();

        server.expect()
                .get()
                .withPath("/apis/kaap.oss.datastax.com/v1beta1/namespaces/ns/brokers/%s-broker".formatted(
                        clusterSpecName))
                .andReturn(HttpURLConnection.HTTP_OK, brokerCr)
                .times(2);


        List<Pod> pods = new ArrayList<>();
        List<PodMetrics> podsMetrics = new ArrayList<>();

        for (int i = 0; i < replicas; i++) {
            final String podName = "%s-broker-%d".formatted(clusterSpecName, i);
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
            podConf.accept(pod, podMetrics, i);
            pods.add(pod);
            podsMetrics.add(podMetrics);

            server.expect()
                    .get()
                    .withPath("/api/v1/namespaces/ns/pods/%s".formatted(podName))
                    .andReturn(HttpURLConnection.HTTP_OK, pod)
                    .once();
        }
        final PodList podList = new PodListBuilder()
                .withItems(pods)
                .build();
        server.expect()
                .get()
                .withPath("/api/v1/namespaces/ns/pods?labelSelector=%s".formatted(
                                URLEncoder.encode("cluster=%s,component=broker,resource-set=broker"
                                                .formatted(clusterSpecName),
                                        StandardCharsets.UTF_8)
                        )
                )
                .andReturn(HttpURLConnection.HTTP_OK, podList)
                .once();

        final PodMetricsList podMetricsList = new PodMetricsListBuilder()
                .withItems(podsMetrics)
                .build();

        server.expect()
                .get()
                .withPath("/apis/metrics.k8s.io/v1beta1/namespaces/ns/pods?labelSelector=%s".formatted(
                                URLEncoder.encode("cluster=%s,component=broker,resource-set=broker".formatted(clusterSpecName),
                                        StandardCharsets.UTF_8)
                        )
                )
                .andReturn(HttpURLConnection.HTTP_OK, podMetricsList)
                .once();

        server.expect()
                .patch()
                .withPath("/apis/kaap.oss.datastax.com/v1beta1/namespaces/ns/brokers/%s-broker".formatted(
                        clusterSpecName))
                .andReply(HttpURLConnection.HTTP_OK, new BodyProvider<Object>() {
                    @Override
                    @SneakyThrows
                    public Object getBody(RecordedRequest recordedRequest) {
                        final ObjectMapper mapper = new ObjectMapper();
                        PatchOp patchOp = mapper.convertValue(
                                mapper.readValue(recordedRequest.getBody().readByteArray(), List.class).get(0),
                                PatchOp.class);
                        capturedPatch[0] = patchOp;
                        return null;
                    }
                })
                .once();

        final BrokerSetAutoscaler brokerAutoscaler =
                new BrokerSetAutoscaler(client, NAMESPACE,
                        BrokerResourcesFactory.BROKER_DEFAULT_SET, pulsarClusterSpec);
        brokerAutoscaler.internalRun();

        return capturedPatch[0];
    }

    @Test
    public void testScaleUp() {
        final String spec = """
                global:
                   name: pul
                broker:
                    replicas: 3
                    autoscaler:
                        enabled: true
                        resourcesUsageSource: K8SMetrics
                    resources:
                        requests:
                            cpu: 1
                """;
        final PatchOp patchOp = runAutoscaler(spec, (pod, metrics, i) -> {
            metrics.getContainers().get(0).getUsage().put("cpu", Quantity.parse("0.9"));
        }, statefulSet -> {
        });
        Assertions.assertEquals(4, patchOp.getValue());
    }

    @Test
    public void testScaleDown() {
        final String spec = """
                global:
                   name: pul
                broker:
                    replicas: 3
                    autoscaler:
                        enabled: true
                        resourcesUsageSource: K8SMetrics
                    resources:
                        requests:
                            cpu: 1
                """;
        final PatchOp patchOp = runAutoscaler(spec, (pod, metrics, i) -> {
            metrics.getContainers().get(0).getUsage().put("cpu", Quantity.parse("0.1"));
        }, statefulSet -> {
        });
        Assertions.assertEquals(2, patchOp.getValue());
    }

    @Test
    public void testStsNotReady() {
        final String spec = """
                global:
                   name: pul
                broker:
                    replicas: 3
                    autoscaler:
                        enabled: true
                        resourcesUsageSource: K8SMetrics
                    resources:
                        requests:
                            cpu: 1
                """;
        final PatchOp patchOp = runAutoscaler(spec, (pod, metrics, i) -> {
            metrics.getContainers().get(0).getUsage().put("cpu", Quantity.parse("0.1"));
        }, statefulSet -> {
            statefulSet.getStatus().setReadyReplicas(2);
        });
        Assertions.assertNull(patchOp);
    }

    @Test
    public void testPodJustStarted() {
        final String spec = """
                global:
                   name: pul
                broker:
                    replicas: 3
                    autoscaler:
                        enabled: true
                        resourcesUsageSource: K8SMetrics
                    resources:
                        requests:
                            cpu: 1
                """;
        final PatchOp patchOp = runAutoscaler(spec, (pod, metrics, i) -> {
            metrics.getContainers().get(0).getUsage().put("cpu", Quantity.parse("0.1"));
            if (i == 2) {
                pod.getStatus().setStartTime(Instant.now().minusSeconds(3).toString());
            }
        }, statefulSet -> {
        });
        Assertions.assertNull(patchOp);
    }


    @Test
    public void testDoNotScaleToZero() {
        final String spec = """
                global:
                   name: pul
                broker:
                    replicas: 1
                    autoscaler:
                        enabled: true
                        resourcesUsageSource: K8SMetrics
                    resources:
                        requests:
                            cpu: 1
                """;
        final PatchOp patchOp = runAutoscaler(spec, (pod, metrics, i) -> {
            metrics.getContainers().get(0).getUsage().put("cpu", Quantity.parse("0.1"));
        }, statefulSet -> {
        });
        Assertions.assertNull(patchOp);
    }

    private PatchOp runAutoscaler(String spec, PodConsumer podConf, Consumer<StatefulSet> stsConf) {
        try {
            return setupMocksAndRunAutoscaler(spec, podConf, stsConf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}