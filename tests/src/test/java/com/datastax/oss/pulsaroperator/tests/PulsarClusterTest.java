package com.datastax.oss.pulsaroperator.tests;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.SerializationUtil;
import com.datastax.oss.pulsaroperator.crds.autorecovery.AutorecoverySpec;
import com.datastax.oss.pulsaroperator.crds.bastion.BastionSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
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

    public static final Quantity SINGLE_POD_CPU = Quantity.parse("25m");
    public static final Quantity SINGLE_POD_MEM = Quantity.parse("512Mi");
    public static final ResourceRequirements RESOURCE_REQUIREMENTS = new ResourceRequirementsBuilder()
            .withRequests(Map.of("memory", SINGLE_POD_MEM, "cpu", SINGLE_POD_CPU))
            .build();


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
        Assert.assertTrue(crds.contains("autorecoveries.com.datastax.oss"));
        Assert.assertTrue(crds.contains("bastions.com.datastax.oss"));
        Assert.assertTrue(crds.contains("pulsarclusters.com.datastax.oss"));
    }

    @Test
    public void testBaseInstall() throws Exception {
        applyRBACManifests();
        applyOperatorManifests();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        applyPulsarCluster(specsToYaml(specs));
        awaitInstalled();

        client.apps().statefulSets()
                .inNamespace(namespace)
                .withName("pulsar-zookeeper")
                .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                        && s.getStatus().getReadyReplicas() == 3, 180, TimeUnit.SECONDS);

        specs.getBookkeeper().setReplicas(3);
        specs.getBroker().setConfig(
                BaseComponentSpec.mergeMaps(
                        specs.getBroker().getConfig(),
                        Map.of(
                                "managedLedgerDefaultAckQuorum", "2",
                                "managedLedgerDefaultEnsembleSize", "2",
                                "managedLedgerDefaultWriteQuorum", "2"
                        )
                )
        );
        applyPulsarCluster(specsToYaml(specs));

        client.apps().statefulSets()
                .inNamespace(namespace)
                .withName("pulsar-bookkeeper")
                .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                        && s.getStatus().getReadyReplicas() == 3, 180, TimeUnit.SECONDS);

        specs.getBroker().setReplicas(3);
        applyPulsarCluster(specsToYaml(specs));

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
        final Map map = SerializationUtil.readYaml(
                """
                        apiVersion: com.datastax.oss/v1alpha1
                        kind: PulsarCluster
                        metadata:
                            name: pulsar-cluster
                        """, Map.class);

        map.put("spec", spec);
        return SerializationUtil.writeAsYaml(map);
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

        defaultSpecs.setZookeeper(ZooKeeperSpec.builder()
                .replicas(3)
                .resources(RESOURCE_REQUIREMENTS)
                .dataVolume(VolumeConfig.builder()
                        .size("100M")
                        .build()
                )
                .build());

        defaultSpecs.setBookkeeper(BookKeeperSpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
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
                .resources(RESOURCE_REQUIREMENTS)
                .build());
        defaultSpecs.setProxy(ProxySpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
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
        return defaultSpecs;
    }


    @Test
    public void testInstallWithHelm() throws Exception {
        try {
            helmInstall();
            awaitOperatorRunning();

            final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
            applyPulsarCluster(specsToYaml(specs));
            awaitInstalled();
        } catch (Throwable t) {
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
        awaitAutorecoveryRunning();
        awaitBastionRunning();
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


    private void awaitAutorecoveryRunning() {
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
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

        client.apps().deployments()
                .inNamespace(namespace)
                .withName("pulsar-autorecovery")
                .waitUntilReady(90, TimeUnit.SECONDS);

    }


    private void awaitBastionRunning() {
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
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

        client.apps().deployments()
                .inNamespace(namespace)
                .withName("pulsar-bastion")
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
