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
package com.datastax.oss.kaap.tests;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.crds.BaseComponentStatus;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.autorecovery.AutorecoverySpec;
import com.datastax.oss.kaap.crds.bastion.BastionSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperAutoscalerSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSetSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarCluster;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.crds.configs.AntiAffinityConfig;
import com.datastax.oss.kaap.crds.configs.AuthConfig;
import com.datastax.oss.kaap.crds.configs.ProbesConfig;
import com.datastax.oss.kaap.crds.configs.VolumeConfig;
import com.datastax.oss.kaap.crds.function.FunctionsWorkerSpec;
import com.datastax.oss.kaap.crds.proxy.ProxySpec;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeperSpec;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.Assert;

@Slf4j
public abstract class BasePulsarClusterTest extends BaseK8sEnvTest {

    public static final Quantity SINGLE_POD_CPU = Quantity.parse("10m");
    public static final Quantity SINGLE_POD_MEM = Quantity.parse("128Mi");
    public static final ResourceRequirements RESOURCE_REQUIREMENTS = new ResourceRequirementsBuilder()
            .withRequests(Map.of("memory", SINGLE_POD_MEM, "cpu", SINGLE_POD_CPU))
            .build();
    public static final String DEFAULT_PULSAR_CLUSTER_NAME = "pulsar";

    public BasePulsarClusterTest(Integer agents) {
        super(agents);
    }

    public BasePulsarClusterTest() {
        super(0);
    }

    @SneakyThrows
    protected String specsToYaml(PulsarClusterSpec spec) {
        final Map map = SerializationUtil.readYaml(
                """
                        apiVersion: kaap.oss.datastax.com/v1beta1
                        kind: PulsarCluster
                        metadata:
                            name: pulsar-cluster
                        """, Map.class);

        map.put("spec", spec);
        return SerializationUtil.writeAsYaml(map);
    }

    protected PulsarClusterSpec getDefaultPulsarClusterSpecs() {
        return getDefaultPulsarClusterSpecs(false);
    }

    protected PulsarClusterSpec getDefaultPulsarClusterSpecs(boolean pulsar3) {
        final PulsarClusterSpec defaultSpecs = new PulsarClusterSpec();
        defaultSpecs.setGlobal(GlobalSpec.builder()
                .name(DEFAULT_PULSAR_CLUSTER_NAME)
                .persistence(true)
                .image(pulsar3 ? PULSAR3_IMAGE : PULSAR_IMAGE)
                .imagePullPolicy("IfNotPresent")
                .storage(GlobalSpec.GlobalStorageConfig.builder()
                        .existingStorageClassName(env.getStorageClass())
                        .build())
                .auth(AuthConfig.builder()
                        .enabled(true)
                        .build()
                )
                .antiAffinity(AntiAffinityConfig.builder()
                        .host(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                                .enabled(false)
                                .build())
                        .build())
                .build());

        // speed up readiness
        final ProbesConfig probe = ProbesConfig.builder()
                .liveness(ProbesConfig.ProbeConfig.builder()
                        .initialDelaySeconds(30)
                        .periodSeconds(45)
                        .timeoutSeconds(60)
                        .build()
                )
                .readiness(
                        ProbesConfig.ProbeConfig.builder()
                                .initialDelaySeconds(10)
                                .periodSeconds(30)
                                .timeoutSeconds(60)
                                .failureThreshold(5)
                                .build()
                )
                .build();

        defaultSpecs.setZookeeper(ZooKeeperSpec.builder()
                .replicas(3)
                .resources(RESOURCE_REQUIREMENTS)
                .probes(probe)
                .dataVolume(VolumeConfig.builder()
                        .size("2Gi")
                        .build()
                )
                .build());

        defaultSpecs.setBookkeeper(BookKeeperSpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
                .probes(probe)
                .volumes(BookKeeperSpec.Volumes.builder()
                        .journal(
                                VolumeConfig.builder()
                                        .size("2Gi")
                                        .build()
                        ).ledgers(
                                VolumeConfig.builder()
                                        .size("2Gi")
                                        .build())
                        .build()
                )
                .autoscaler(BookKeeperAutoscalerSpec.builder()
                        .diskUsageToleranceHwm(0.8)
                        .diskUsageToleranceLwm(0.7)
                        .scaleUpBy(1)
                        .scaleDownBy(1)
                        .scaleUpMaxLimit(10)
                        .periodMs(5000L)
                        .stabilizationWindowMs(10000L)
                        .minWritableBookies(1)
                        .enabled(false)
                        .build())
                .build());

        defaultSpecs.setBroker(BrokerSpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
                .config(
                        Map.of(
                                "managedLedgerDefaultAckQuorum", "1",
                                "managedLedgerDefaultEnsembleSize", "1",
                                "managedLedgerDefaultWriteQuorum", "1"
                        )
                )
                .probes(BrokerSetSpec.BrokerProbesConfig.brokerProbeConfigBuilder()
                        .liveness(ProbesConfig.ProbeConfig.builder()
                                .initialDelaySeconds(5)
                                .periodSeconds(3)
                                .timeoutSeconds(60)
                                .failureThreshold(10)
                                .build()
                        )
                        .readiness(
                                ProbesConfig.ProbeConfig.builder()
                                        .initialDelaySeconds(1)
                                        .periodSeconds(2)
                                        .timeoutSeconds(65)
                                        .failureThreshold(10)
                                        .build()
                        )
                        .build())
                .build());
        defaultSpecs.setProxy(ProxySpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
                .probes(probe)
                .webSocket(ProxySpec.WebSocketConfig.builder()
                        .resources(RESOURCE_REQUIREMENTS)
                        .build())
                .build());
        defaultSpecs.setAutorecovery(AutorecoverySpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
                .build());
        defaultSpecs.setBastion(BastionSpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
                .build());
        defaultSpecs.setFunctionsWorker(FunctionsWorkerSpec.builder()
                .resources(RESOURCE_REQUIREMENTS)
                .runtime("kubernetes")
                .config(Map.of(
                        "numFunctionPackageReplicas", 1,
                        "functionInstanceMaxResources", Map.of(
                                "disk", 1000000000,
                                "ram", 12800000,
                                "cpu", 0.001d
                        )
                ))
                // default probe
                .build());
        return defaultSpecs;
    }


    protected void applyPulsarCluster(String content) {
        // ensure pulsar image is already there to not count the pulling time in the await-ready condition
        env.refreshImages();
        final PulsarCluster pulsarCluster = client.resources(PulsarCluster.class)
                .inNamespace(namespace)
                .load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
                .createOrReplace();

        Awaitility.await("waiting for pulsar cluster to be ready")
                .atMost(15, TimeUnit.MINUTES)
                .with().pollInterval(10, TimeUnit.SECONDS)
                .until(() -> {
                    if (isCrdReady(PulsarCluster.class, pulsarCluster.getMetadata().getName())) {
                        return true;
                    }
                    final String operatorPodName;
                    try {
                        operatorPodName = getOperatorPodName();
                    } catch (KubernetesClientException clientException) {
                        log.error("Error getting operator pod name", clientException);
                        return false;
                    }
                    printPodLogs(operatorPodName, 10);
                    return false;
                });

    }

    private boolean isCrdReady(Class<? extends CustomResource<?, ? extends BaseComponentStatus>> resource,
                               String name) {
        final List<Condition> conditions = client.resources(resource)
                .inNamespace(namespace)
                .withName(name)
                .get().getStatus()
                .getConditions();
        final Condition readyCondition =
                conditions.stream().filter(c -> c.getType().equals(CRDConstants.CONDITIONS_TYPE_READY))
                        .findAny().orElse(null);
        if (readyCondition == null) {
            return false;
        }
        if (readyCondition.getStatus().equals(CRDConstants.CONDITIONS_STATUS_TRUE)) {
            return true;
        }
        log.info("{} not ready, reason: {}", name, readyCondition.getReason());
        return false;
    }

    protected void awaitInstalled() {
        awaitZooKeeperRunning();
        awaitBookKeeperRunning();
        awaitBrokerRunning();
        awaitProxyRunning();
        awaitAutorecoveryRunning();
        awaitBastionRunning();
    }

    protected void awaitZooKeeperRunning() {
        Awaitility.await().untilAsserted(() -> {
            Assert.assertTrue(client.pods()
                    .inNamespace(namespace)
                    .withLabel("component", "zookeeper").list().getItems().size() >= 1);
            Assert.assertEquals(client.policy().v1().podDisruptionBudget()
                    .inNamespace(namespace)
                    .withLabel("component", "zookeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.configMaps()
                    .inNamespace(namespace)
                    .withLabel("component", "zookeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.services()
                    .inNamespace(namespace)
                    .withLabel("component", "zookeeper").list().getItems().size(), 2);
            Assert.assertEquals(client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withLabel("component", "zookeeper").list().getItems().size(), 1);
        });

        log.info("awaiting zk pod up");
        client.pods()
                .inNamespace(namespace)
                .withName("pulsar-zookeeper-0")
                .waitUntilReady(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

        Awaitility
                .await()
                .until(() ->
                        !client
                                .pods()
                                .inNamespace(namespace)
                                .withLabel("job-name", "pulsar-zookeeper-metadata")
                                .list()
                                .getItems()
                                .isEmpty()
                );

        log.info("awaiting zk init job to complete");
        awaitJobCompleted("pulsar-zookeeper-metadata");
    }

    protected void awaitBookKeeperRunning() {
        awaitBookKeeperRunning(1);
    }

    protected void awaitBookKeeperRunning(int expectedCount) {
        Awaitility.await().untilAsserted(() -> {
            Assert.assertEquals(client.pods()
                    .inNamespace(namespace)
                    .withLabel("component", "bookkeeper").list().getItems().size(), expectedCount);
            Assert.assertEquals(client.policy().v1().podDisruptionBudget()
                    .inNamespace(namespace)
                    .withLabel("component", "bookkeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.configMaps()
                    .inNamespace(namespace)
                    .withLabel("component", "bookkeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withLabel("component", "bookkeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.services()
                    .inNamespace(namespace)
                    .withLabel("component", "bookkeeper").list().getItems().size(), 1);
        });

        for (int i = 0; i < expectedCount; i++) {
            log.info("awaiting bk pod {} up", i);
            client.pods()
                    .inNamespace(namespace)
                    .withName("pulsar-bookkeeper-" + i)
                    .waitUntilReady(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);
        }
    }

    protected void awaitBrokerRunning() {
        Awaitility.await().untilAsserted(() -> {
            Assert.assertEquals(client.pods()
                    .inNamespace(namespace)
                    .withLabel("component", "broker").list().getItems().size(), 1);
            Assert.assertEquals(client.policy().v1().podDisruptionBudget()
                    .inNamespace(namespace)
                    .withLabel("component", "broker").list().getItems().size(), 1);
            Assert.assertEquals(client.configMaps()
                    .inNamespace(namespace)
                    .withLabel("component", "broker").list().getItems().size(), 1);
            Assert.assertEquals(client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withLabel("component", "broker").list().getItems().size(), 1);
            Assert.assertEquals(client.services()
                    .inNamespace(namespace)
                    .withLabel("component", "broker").list().getItems().size(), 1);
        });

        log.info("awaiting broker pod up");
        client.pods()
                .inNamespace(namespace)
                .withName("pulsar-broker-0")
                .waitUntilReady(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

    }

    protected void awaitProxyRunning() {
        Awaitility.await().untilAsserted(() -> {
            Assert.assertEquals(client.pods()
                    .inNamespace(namespace)
                    .withLabel("component", "proxy").list().getItems().size(), 1);
            Assert.assertEquals(client.policy().v1().podDisruptionBudget()
                    .inNamespace(namespace)
                    .withLabel("component", "proxy").list().getItems().size(), 1);
            Assert.assertEquals(client.configMaps()
                    .inNamespace(namespace)
                    .withLabel("component", "proxy").list().getItems().size(), 2);
            Assert.assertEquals(client.apps().deployments()
                    .inNamespace(namespace)
                    .withLabel("component", "proxy").list().getItems().size(), 1);
            Assert.assertEquals(client.services()
                    .inNamespace(namespace)
                    .withLabel("component", "proxy").list().getItems().size(), 1);
        });

        log.info("awaiting proxy pod up");
        client.apps().deployments()
                .inNamespace(namespace)
                .withName("pulsar-proxy")
                .waitUntilReady(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

    }


    protected void awaitAutorecoveryRunning() {
        Awaitility.await().untilAsserted(() -> {
            Assert.assertEquals(client.pods()
                    .inNamespace(namespace)
                    .withLabel("component", "autorecovery").list().getItems().size(), 1);
            Assert.assertEquals(client.configMaps()
                    .inNamespace(namespace)
                    .withLabel("component", "autorecovery").list().getItems().size(), 1);
            Assert.assertEquals(client.apps().deployments()
                    .inNamespace(namespace)
                    .withLabel("component", "autorecovery").list().getItems().size(), 1);
        });

        log.info("awaiting autorecovery pod up");
        client.apps().deployments()
                .inNamespace(namespace)
                .withName("pulsar-autorecovery")
                .waitUntilReady(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

    }


    protected void awaitBastionRunning() {
        Awaitility.await().untilAsserted(() -> {
            Assert.assertEquals(client.pods()
                    .inNamespace(namespace)
                    .withLabel("component", "bastion").list().getItems().size(), 1);
            Assert.assertEquals(client.configMaps()
                    .inNamespace(namespace)
                    .withLabel("component", "bastion").list().getItems().size(), 1);
            Assert.assertEquals(client.apps().deployments()
                    .inNamespace(namespace)
                    .withLabel("component", "bastion").list().getItems().size(), 1);
        });

        log.info("awaiting bastion pod up");
        client.apps().deployments()
                .inNamespace(namespace)
                .withName("pulsar-bastion")
                .waitUntilReady(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

    }

    protected void awaitFunctionsWorkerRunning() {
        Awaitility.await().untilAsserted(() -> {
            Assert.assertEquals(client.pods()
                    .inNamespace(namespace)
                    .withLabel("component", "function").list().getItems().size(), 1);
            Assert.assertEquals(client.policy().v1().podDisruptionBudget()
                    .inNamespace(namespace)
                    .withLabel("component", "function").list().getItems().size(), 1);
            Assert.assertEquals(client.configMaps()
                    .inNamespace(namespace)
                    .withLabel("component", "function").list().getItems().size(), 2);
            Assert.assertEquals(client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withLabel("component", "function").list().getItems().size(), 1);
            Assert.assertEquals(client.services()
                    .inNamespace(namespace)
                    .withLabel("component", "function").list().getItems().size(), 2);
        });

        log.info("awaiting functions worker pod up");
        client.apps().statefulSets()
                .inNamespace(namespace)
                .withName("pulsar-function")
                .waitUntilReady(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

    }

    protected void awaitJobCompleted(String name) {
        awaitJobCompleted(name, 2);
    }

    protected void awaitJobCompleted(String name, long minutes) {
        Awaitility.await()
                .atMost(minutes, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    final Job job = client.batch().v1().jobs()
                            .inNamespace(namespace)
                            .withName(name)
                            .get();
                    Assert.assertNotNull(job);
                    final JobStatus status = job.getStatus();
                    Assert.assertNotNull(status);
                    final Integer succeeded = status
                            .getSucceeded();
                    Assert.assertNotNull(succeeded);
                    Assert.assertEquals(succeeded.intValue(), 1);
                });

    }


    protected void awaitUninstalled() {
        Awaitility.await().untilAsserted(() -> {
            final List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .withLabel("app", DEFAULT_PULSAR_CLUSTER_NAME).list().getItems();
            log.info("found {} pods: {}", pods.size(), pods.stream().map(p -> p.getMetadata().getName()).collect(
                    Collectors.toList()));
            Assert.assertEquals(pods.size(), 0);
            Assert.assertEquals(
                    client.policy().v1().podDisruptionBudget()
                            .inNamespace(namespace)
                            .withLabel("app", DEFAULT_PULSAR_CLUSTER_NAME).list().getItems().size(), 0);
            Assert.assertEquals(client.configMaps()
                    .inNamespace(namespace)
                    .withLabel("app", DEFAULT_PULSAR_CLUSTER_NAME).list().getItems().size(), 0);
            Assert.assertEquals(client.services()
                    .inNamespace(namespace)
                    .withLabel("app", DEFAULT_PULSAR_CLUSTER_NAME).list().getItems().size(), 0);
            Assert.assertEquals(client.apps().statefulSets()
                    .inNamespace(namespace).withLabel("app", DEFAULT_PULSAR_CLUSTER_NAME).list().getItems().size(), 0);
            Assert.assertEquals(client.batch().v1().jobs()
                    .inNamespace(namespace).withLabel("app", DEFAULT_PULSAR_CLUSTER_NAME).list().getItems().size(), 0);
        });
    }

    protected void assertSourceInstalled() {
        Awaitility.await().until(() -> {
            try {
                execInBastionPod(
                        "bin/pulsar-admin sources create --name generator --tenant public --namespace default "
                                + "--destinationTopicName generator_test --source-type data-generator "
                                + "--ram 12800000 --cpu 0.001 --disk 1000000000 "
                                + "--parallelism 2");
                return true;
            } catch (Throwable t) {
                log.error("Cmd failed", t);
            }
            return false;
        });

        Awaitility.await().atMost(15, TimeUnit.MINUTES).untilAsserted(() -> {
            printRunningPods();
            Assert.assertTrue(
                    client.pods().inNamespace(namespace).withName("pf-public-default-generator-0").isReady());
            Assert.assertTrue(
                    client.pods().inNamespace(namespace).withName("pf-public-default-generator-1").isReady());
        });
    }
}
