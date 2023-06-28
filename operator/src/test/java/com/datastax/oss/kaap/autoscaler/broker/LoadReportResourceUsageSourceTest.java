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
package com.datastax.oss.kaap.autoscaler.broker;

import com.datastax.oss.kaap.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.kaap.crds.broker.Broker;
import com.datastax.oss.kaap.crds.broker.BrokerFullSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.fabric8.kubernetes.client.server.mock.OutputStreamMessage;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import lombok.Builder;
import lombok.SneakyThrows;
import org.testng.Assert;
import org.testng.annotations.Test;

public class LoadReportResourceUsageSourceTest {

    @Builder(setterPrefix = "with")
    public static class MockServer implements AutoCloseable {


        private PulsarClusterSpec pulsarClusterSpec;
        private BiConsumer<Pod, MockServer> podConsumer;

        KubernetesServer server;

        @SneakyThrows
        void start() {
            pulsarClusterSpec.getGlobal().applyDefaults(null);
            pulsarClusterSpec.getBroker().applyDefaults(pulsarClusterSpec.getGlobalSpec());

            final Broker brokerCr = new Broker();
            brokerCr.setSpec(BrokerFullSpec.builder()
                    .global(pulsarClusterSpec.getGlobal())
                    .broker(pulsarClusterSpec.getBroker())
                    .build());

            final String clusterName = pulsarClusterSpec.getGlobal().getName();

            server = new KubernetesServer(false);
            server.before();

            List<Pod> pods = new ArrayList<>();

            BrokerResourcesFactory brokerResourcesFactory =
                    new BrokerResourcesFactory(null, "ns", BrokerResourcesFactory.BROKER_DEFAULT_SET,
                            pulsarClusterSpec.getBroker(),
                            pulsarClusterSpec.getGlobal(), null);
            final Field field = brokerResourcesFactory.getClass().getDeclaredField("configMap");
            field.setAccessible(true);
            field.set(brokerResourcesFactory, new ConfigMapBuilder().build());
            final StatefulSet sts = brokerResourcesFactory.generateStatefulSet();


            final Integer replicas = pulsarClusterSpec.getBroker().getReplicas();

            for (int i = 0; i < replicas; i++) {
                final String podName = "%s-broker-%d".formatted(clusterName, i);
                final Pod pod = new PodBuilder()
                        .withNewMetadata()
                        .withName(podName)
                        .endMetadata()
                        .withSpec(sts.getSpec().getTemplate().getSpec())
                        .build();
                podConsumer.accept(pod, this);
                pods.add(pod);
            }

            final PodList podList = new PodListBuilder()
                    .withItems(pods)
                    .build();
            server.expect()
                    .get()
                    .withPath("/api/v1/namespaces/ns/pods?labelSelector=%s".formatted(
                                    URLEncoder.encode("app=pulsar"
                                                    .formatted(clusterName),
                                            StandardCharsets.UTF_8)
                            )
                    )
                    .andReturn(HttpURLConnection.HTTP_OK, podList)
                    .once();

        }

        @Override
        public void close() {
            server.after();
        }
    }

    @Test(expectedExceptions = ExecutionException.class)
    public void testLastFailed() throws Exception {
        final String spec = """
                global:
                   name: pul
                broker:
                    replicas: 2
                    autoscaler:
                        enabled: true
                """;

        final List<BrokerResourceUsageSource.ResourceUsage> brokersResourceUsages =
                createLoadReportResourceUsageSource(spec, (pod, server) -> {
                    final String[] split = pod.getMetadata().getName().split("-");
                    int replicaCount = Integer.parseInt(split[split.length - 1]);
                    final String podExec = genExpectedUrlForExecInPod(pod.getMetadata().getName(),
                            "curl http://localhost:8080/admin/v2/broker-stats/load-report/");
                    if (replicaCount == 0) {

                        server.server.expect()
                                .get()
                                .withPath(podExec)
                                .andUpgradeToWebSocket()
                                .open(new OutputStreamMessage("""
                                        {
                                            "cpu": {
                                                "usage": %f,
                                                "limit": 8.0
                                            },
                                            "other": {}
                                        }
                                        """.formatted(2.33 * (replicaCount + 1))))
                                .done()
                                .always();
                    }

                });
    }

    @Test
    public void testOk() throws Exception {
        final String spec = """
                global:
                   name: pul
                broker:
                    replicas: 2
                    autoscaler:
                        enabled: true
                """;

        final List<BrokerResourceUsageSource.ResourceUsage> brokersResourceUsages =
                createLoadReportResourceUsageSource(spec, (pod, server) -> {
                    final String[] split = pod.getMetadata().getName().split("-");
                    int replicaCount = Integer.parseInt(split[split.length - 1]);
                    final String podExec = genExpectedUrlForExecInPod(pod.getMetadata().getName(),
                            "curl http://localhost:8080/admin/v2/broker-stats/load-report/");

                    server.server.expect()
                            .get()
                            .withPath(podExec)
                            .andUpgradeToWebSocket()
                            .open(new OutputStreamMessage("""
                                    {
                                        "cpu": {
                                            "usage": %f,
                                            "limit": 8.0
                                        },
                                        "other": {}
                                    }
                                    """.formatted(2.33 * (replicaCount + 1))))
                            .done()
                            .always();

                });

        Assert.assertEquals(brokersResourceUsages.size(), 2);
        Assert.assertEquals(brokersResourceUsages.get(0).getPod(), "pul-broker-0");
        Assert.assertEquals(brokersResourceUsages.get(0).getPercentCpu() + "", "0.29");
        Assert.assertEquals(brokersResourceUsages.get(1).getPod(), "pul-broker-1");
        Assert.assertEquals(brokersResourceUsages.get(1).getPercentCpu() + "", "0.58");
    }


    private List<BrokerResourceUsageSource.ResourceUsage> createLoadReportResourceUsageSource(String spec,
                                                                                              BiConsumer<Pod,
                                                                                                      MockServer> podConf) {
        final PulsarClusterSpec pulsarClusterSpec = MockKubernetesClient.readYaml(spec, PulsarClusterSpec.class);
        try (final MockServer server = MockServer.builder()
                .withPulsarClusterSpec(pulsarClusterSpec)
                .withPodConsumer(podConf)
                .build();) {
            server.start();


            final LoadReportResourceUsageSource source =
                    new LoadReportResourceUsageSource(server.server.getClient(), "ns", Map.of("app", "pulsar"),
                            BrokerResourcesFactory.BROKER_DEFAULT_SET,
                            pulsarClusterSpec.getBroker(),
                            pulsarClusterSpec.getGlobalSpec());
            return source.getBrokersResourceUsages();
        }
    }

    private static String genExpectedUrlForExecInPod(String podName, String cmd) {
        return "/api/v1/namespaces/ns/pods/"
                + podName
                + "/exec?command=bash&command=-c&command="
                + URLEncoder.encode(cmd, StandardCharsets.UTF_8).replace("+", "%20")
                + "&container=pul-broker&stdout=true&stderr=true";
    }


}