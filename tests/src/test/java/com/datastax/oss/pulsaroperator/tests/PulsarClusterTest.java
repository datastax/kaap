package com.datastax.oss.pulsaroperator.tests;

import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
public class PulsarClusterTest extends BaseK8sEnvTest {

    public static final Quantity SINGLE_POD_CPU = Quantity.parse("100m");
    public static final Quantity SINGLE_POD_MEM = Quantity.parse("512Mi");
    private static ObjectMapper yamlMapper = new ObjectMapper(YAMLFactory.builder()
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .build()
    )
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);


    @Test
    public void testCRDs() throws Exception {
        applyRBACManifests();
        applyOperatorManifests();
        final CustomResourceDefinitionList list = client.apiextensions().v1()
                .customResourceDefinitions()
                .list();
        final List<String> crds = list.getItems()
                .stream()
                .map(crd -> crd.getMetadata().getName())
                .collect(Collectors.toList());
        Assert.assertTrue(crds.contains("zookeepers.com.datastax.oss"));
        Assert.assertTrue(crds.contains("bookkeepers.com.datastax.oss"));
        Assert.assertTrue(crds.contains("brokers.com.datastax.oss"));
        Assert.assertTrue(crds.contains("proxies.com.datastax.oss"));
        Assert.assertTrue(crds.contains("pulsarclusters.com.datastax.oss"));
    }

    @Test
    public void testBaseInstall() throws Exception {
        applyRBACManifests();
        applyOperatorManifests();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        applyManifest(specsToYaml(specs));
        awaitInstalled();

        client.apps().statefulSets()
                .inNamespace(namespace)
                .withName("pulsar-zookeeper")
                .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                        && s.getStatus().getReadyReplicas() == 3, 180, TimeUnit.SECONDS);

        specs.getBookkeeper().setReplicas(3);
        applyManifest(specsToYaml(specs));

        client.apps().statefulSets()
                .inNamespace(namespace)
                .withName("pulsar-bookkeeper")
                .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                        && s.getStatus().getReadyReplicas() == 3, 180, TimeUnit.SECONDS);

        specs.getBroker().setReplicas(3);
        applyManifest(specsToYaml(specs));

        client.apps().statefulSets()
                .inNamespace(namespace)
                .withName("pulsar-broker")
                .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                        && s.getStatus().getReadyReplicas() == 3, 180, TimeUnit.SECONDS);

        assertProduceConsume();
        client.resources(PulsarCluster.class).inNamespace(namespace)
                .withName("pulsar-cluster")
                .delete();
        awaitUninstalled();
    }

    private void assertProduceConsume() {
        // use two different brokers to ensure broker's intra communication
        execInPod("pulsar-broker-2", "bin/pulsar-client produce -m test test-topic");
        execInPod("pulsar-broker-1", "bin/pulsar-client consume -s sub -p Earliest test-topic");
        final String proxyPod =
                client.pods().withLabel("component", "proxy").list().getItems().get(0).getMetadata().getName();

        execInPod(proxyPod, "pulsar-proxy-ws",
                "bin/pulsar-client --url \"ws://localhost:8000\" produce -m test test-topic-proxy");
        execInPod(proxyPod, "pulsar-proxy", "bin/pulsar-client consume -s sub-proxy -p Earliest test-topic-proxy");
    }

    @SneakyThrows
    private void execInPod(String podName, String cmd) {
        execInPod(podName, null, cmd);
    }

    @SneakyThrows
    private void execInPod(String podName, String containerName, String cmd) {
        log.info("Executing in pod {}: {}", containerName == null ? podName : podName + "/" + containerName, cmd);
        try (final ExecWatch exec = client
                .pods()
                .inNamespace(namespace)
                .withName(podName)
                .inContainer(containerName)
                .writingOutput(System.out)
                .writingError(System.err)
                .withTTY()
                .exec("bash", "-c", cmd);) {
            if (exec.exitCode().get().intValue() != 0) {
                log.error("Produce failed:\n{}", new String(exec.getOutput().readAllBytes(), StandardCharsets.UTF_8));
                Assert.fail();
            }
        }
    }

    @SneakyThrows
    private String specsToYaml(PulsarClusterSpec spec) {
        final Map map = yamlMapper.readValue(
                """
                        apiVersion: com.datastax.oss/v1alpha1
                        kind: PulsarCluster
                        metadata:
                            name: pulsar-cluster
                        """, Map.class);

        map.put("spec", spec);
        return yamlMapper.writeValueAsString(map);
    }

    private PulsarClusterSpec getDefaultPulsarClusterSpecs() {
        final PulsarClusterSpec defaultSpecs = new PulsarClusterSpec();
        defaultSpecs.setGlobal(GlobalSpec.builder()
                .name("pulsar")
                .persistence(true)
                .image(PULSAR_IMAGE)
                .imagePullPolicy("Never")
                .storage(GlobalSpec.GlobalStorageConfig.builder()
                        .existingStorageClassName(env.getStorageClass())
                        .build())
                .build());

        final ResourceRequirements resources = new ResourceRequirementsBuilder()
                .withRequests(Map.of("memory", SINGLE_POD_MEM, "cpu", SINGLE_POD_CPU))
                .build();
        defaultSpecs.setZookeeper(ZooKeeperSpec.builder()
                .replicas(3)
                .resources(resources)
                .dataVolume(VolumeConfig.builder()
                        .size("100M")
                        .build()
                )
                .build());

        defaultSpecs.setBookkeeper(BookKeeperSpec.builder()
                .replicas(1)
                .resources(resources)
                .volumes(BookKeeperSpec.Volumes.builder()
                        .journal(
                                VolumeConfig.builder()
                                        .size("100M")
                                        .build()
                        ).ledgers(
                                VolumeConfig.builder()
                                        .size("100M")
                                        .build())
                        .build()
                )
                .build());

        defaultSpecs.setBroker(BrokerSpec.builder()
                .replicas(1)
                .resources(resources)
                .build());
        defaultSpecs.setProxy(ProxySpec.builder()
                .replicas(1)
                .resources(resources)
                .webSocket(ProxySpec.WebSocketConfig.builder()
                        .resources(resources)
                        .build())
                .build());
        return defaultSpecs;
    }


    @Test
    public void testInstallWithHelm() throws Exception {
        try {
            helmInstall();
            awaitOperatorRunning();
            applyManifestFromFile(getHelmExampleFilePath("local-k3s.yaml"));
            awaitInstalled();
        } catch(Throwable t) {
            t.printStackTrace();
            Assert.fail("Error during the test", t);
        } finally {
            try {
                helmUninstall();
                client.resources(PulsarCluster.class).inNamespace(namespace)
                        .withName("pulsar-cluster")
                        .delete();
                awaitUninstalled();
            } catch (Throwable tt) {
                Assert.fail("Error during test cleanup", tt);
            }
        }
    }

    private void awaitInstalled() {
        awaitZooKeeperRunning();
        awaitBookKeeperRunning();
        awaitBrokerRunning();
        awaitProxyRunning();
    }

    private void awaitZooKeeperRunning() {
        Awaitility.await().atMost(90, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
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

        client.pods()
                .inNamespace(namespace)
                .withName("pulsar-zookeeper-0")
                .waitUntilReady(90, TimeUnit.SECONDS);

        final Pod jobPod = client.pods().inNamespace(namespace).withLabel("job-name", "pulsar-zookeeper")
                .list().getItems().get(0);
        client.pods().inNamespace(namespace).withName(jobPod.getMetadata().getName())
                .waitUntilCondition(pod -> pod.getStatus().getPhase().equals("Succeeded"), 2, TimeUnit.MINUTES);
    }

    private void awaitBookKeeperRunning() {
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Assert.assertEquals(client.pods()
                    .inNamespace(namespace)
                    .withLabel("component", "bookkeeper").list().getItems().size(), 1);
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

        client.pods()
                .inNamespace(namespace)
                .withName("pulsar-bookkeeper-0")
                .waitUntilReady(90, TimeUnit.SECONDS);

    }

    private void awaitBrokerRunning() {
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
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

        client.pods()
                .inNamespace(namespace)
                .withName("pulsar-broker-0")
                .waitUntilReady(90, TimeUnit.SECONDS);

    }

    private void awaitProxyRunning() {
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
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

        client.apps().deployments()
                .inNamespace(namespace)
                .withName("pulsar-proxy")
                .waitUntilReady(90, TimeUnit.SECONDS);

    }

    private void awaitUninstalled() {
        Awaitility.await().atMost(90, TimeUnit.SECONDS).pollInterval(5, TimeUnit.SECONDS).untilAsserted(() -> {
            final List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .withLabel("app", "pulsar").list().getItems();
            log.info("found {} pods: {}", pods.size(), pods.stream().map(p -> p.getMetadata().getName()).collect(
                    Collectors.toList()));
            Assert.assertEquals(pods.size(), 0);
            Assert.assertEquals(
                    client.policy().v1().podDisruptionBudget()
                            .inNamespace(namespace)
                            .withLabel("app", "pulsar").list().getItems().size(), 0);
            Assert.assertEquals(client.configMaps()
                    .inNamespace(namespace)
                    .withLabel("app", "pulsar").list().getItems().size(), 0);
            Assert.assertEquals(client.services()
                    .inNamespace(namespace)
                    .withLabel("app", "pulsar").list().getItems().size(), 0);
            Assert.assertEquals(client.apps().statefulSets()
                    .inNamespace(namespace).withLabel("app", "pulsar").list().getItems().size(), 0);
            Assert.assertEquals(client.batch().v1().jobs()
                    .inNamespace(namespace).withLabel("app", "pulsar").list().getItems().size(), 0);
        });
    }
}
