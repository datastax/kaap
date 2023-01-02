package com.datastax.oss.pulsaroperator.tests;

import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.SerializationUtil;
import com.datastax.oss.pulsaroperator.crds.autorecovery.AutorecoverySpec;
import com.datastax.oss.pulsaroperator.crds.bastion.BastionSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
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

    @SneakyThrows
    protected String specsToYaml(PulsarClusterSpec spec) {
        final Map map = SerializationUtil.readYaml(
                """
                        apiVersion: pulsar.oss.datastax.com/v1alpha1
                        kind: PulsarCluster
                        metadata:
                            name: pulsar-cluster
                        """, Map.class);

        map.put("spec", spec);
        return SerializationUtil.writeAsYaml(map);
    }

    protected PulsarClusterSpec getDefaultPulsarClusterSpecs() {
        final PulsarClusterSpec defaultSpecs = new PulsarClusterSpec();
        defaultSpecs.setGlobal(GlobalSpec.builder()
                .name("pulsar")
                .persistence(true)
                .image(PULSAR_IMAGE)
                .imagePullPolicy("Never")
                .storage(GlobalSpec.GlobalStorageConfig.builder()
                        .existingStorageClassName(env.getStorageClass())
                        .build())
                .auth(AuthConfig.builder()
                        .enabled(true)
                        .build()
                )
                .build());

        // speed up readiness
        final ProbeConfig probe = ProbeConfig.builder()
                .initial(5)
                .period(5)
                .timeout(60)
                .build();

        defaultSpecs.setZookeeper(ZooKeeperSpec.builder()
                .replicas(3)
                .resources(RESOURCE_REQUIREMENTS)
                .probe(probe)
                .dataVolume(VolumeConfig.builder()
                        .size("2Gi")
                        .build()
                )
                .build());

        defaultSpecs.setBookkeeper(BookKeeperSpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
                .probe(probe)
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
                .probe(probe)
                .build());
        defaultSpecs.setProxy(ProxySpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
                .probe(probe)
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


    protected void awaitInstalled() {
        awaitZooKeeperRunning();
        awaitBookKeeperRunning();
        awaitBrokerRunning();
        awaitProxyRunning();
        awaitAutorecoveryRunning();
        awaitBastionRunning();
    }

    private void awaitZooKeeperRunning() {
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
                                .withLabel("job-name", "pulsar-zookeeper")
                                .list()
                                .getItems()
                                .isEmpty()
                );

        log.info("awaiting zk init job to complete");
        awaitJobCompleted("pulsar-zookeeper");
    }

    private void awaitBookKeeperRunning() {
        Awaitility.await().untilAsserted(() -> {
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

        log.info("awaiting bk pod up");
        client.pods()
                .inNamespace(namespace)
                .withName("pulsar-bookkeeper-0")
                .waitUntilReady(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

    }

    private void awaitBrokerRunning() {
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

    private void awaitProxyRunning() {
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


    private void awaitAutorecoveryRunning() {
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


    private void awaitBastionRunning() {
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

    private void awaitJobCompleted(String name) {
        Awaitility.await()
                .atMost(2, TimeUnit.MINUTES)
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
