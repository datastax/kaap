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
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorkerSpec;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
public class PulsarClusterTest extends BaseK8sEnvTest {

    public static final Quantity SINGLE_POD_CPU = Quantity.parse("10m");
    public static final Quantity SINGLE_POD_MEM = Quantity.parse("128Mi");
    public static final ResourceRequirements RESOURCE_REQUIREMENTS = new ResourceRequirementsBuilder()
            .withRequests(Map.of("memory", SINGLE_POD_MEM, "cpu", SINGLE_POD_CPU))
            .build();


    @Test
    public void testCRDs() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();
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
        Assert.assertTrue(crds.contains("functionsworkers.com.datastax.oss"));
        Assert.assertTrue(crds.contains("bastions.com.datastax.oss"));
        Assert.assertTrue(crds.contains("pulsarclusters.com.datastax.oss"));
    }

    @Test
    public void testScaling() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        try {
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
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CompletableFuture<String> future = new CompletableFuture<>();
        try (final ExecWatch exec = client
                .pods()
                .inNamespace(namespace)
                .withName(podName)
                .inContainer(containerName)
                .writingOutput(baos)
                .writingError(baos)
                .usingListener(new SimpleListener(future, baos))
                .exec("bash", "-c", cmd);) {
            final String outputCmd = future.get(10, TimeUnit.SECONDS);
            log.info("Output cmd: {}", outputCmd);
            if (exec.exitCode().get().intValue() != 0) {
                log.error("Cmd failed with code {}: {}", exec.exitCode().get().intValue(), outputCmd);
                throw new RuntimeException("Cmd '%s' failed with: %s".formatted(cmd, outputCmd));
            }
        }
    }

    static class SimpleListener implements ExecListener {

        private CompletableFuture<String> data;
        private ByteArrayOutputStream baos;

        public SimpleListener(CompletableFuture<String> data, ByteArrayOutputStream baos) {
            this.data = data;
            this.baos = baos;
        }

        @Override
        public void onOpen() {
        }

        @Override
        public void onFailure(Throwable t, Response failureResponse) {
            System.err.println(t.getMessage());
            data.completeExceptionally(t);
        }

        @Override
        public void onClose(int code, String reason) {
            data.complete(baos.toString());
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
                .build());

        defaultSpecs.setBroker(BrokerSpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
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

    @Test
    public void testFunctions() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        specs.getZookeeper().setReplicas(1);
        specs.getBroker().setConfig(
                BaseComponentSpec.mergeMaps(specs.getBroker().getConfig(),
                        Map.of(
                                "managedLedgerDefaultAckQuorum", "1",
                                "managedLedgerDefaultEnsembleSize", "1",
                                "managedLedgerDefaultWriteQuorum", "1"

                        ))
        );
        specs.setFunctionsWorker(FunctionsWorkerSpec.builder()
                .replicas(1)
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
                .probe(ProbeConfig.builder()
                        .initial(5)
                        .period(5)
                        .build())
                .build()
        );
        try {
            applyPulsarCluster(specsToYaml(specs));
            awaitInstalled();
            awaitFunctionsWorkerRunning();
            final String proxyPod =
                    client.pods().withLabel("component", "proxy").list().getItems().get(0).getMetadata().getName();
            client.pods()
                    .inNamespace(namespace)
                    .withName("pulsar-function-0")
                    .waitUntilReady(30, TimeUnit.SECONDS);

            Awaitility.await().until(() -> {
                try {
                    execInPod(proxyPod, "pulsar-proxy",
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

            Awaitility.await().untilAsserted(() -> {
                printRunningPods();
                Assert.assertTrue(
                        client.pods().inNamespace(namespace).withName("pf-public-default-generator-0").isReady());
                Assert.assertTrue(
                        client.pods().inNamespace(namespace).withName("pf-public-default-generator-1").isReady());
            });
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }

    @Test
    public void testTokenAuth() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        specs.getZookeeper().setReplicas(1);
        specs.getBroker().setConfig(
                BaseComponentSpec.mergeMaps(specs.getBroker().getConfig(),
                        Map.of(
                                "managedLedgerDefaultAckQuorum", "1",
                                "managedLedgerDefaultEnsembleSize", "1",
                                "managedLedgerDefaultWriteQuorum", "1"

                        ))
        );
        specs.getGlobal()
                .setAuth(AuthConfig.builder()
                        .enabled(true)
                        .build());

        try {
            applyPulsarCluster(specsToYaml(specs));
            awaitJobCompleted("pulsar-token-auth-provisioner");

            final List<Secret> secrets = client.secrets()
                    .inNamespace(namespace)
                    .list().getItems();
            Assert.assertEquals(secrets.size(), 6);
            final Map<String, Secret> secretMap =
                    secrets.stream().collect(Collectors.toMap(s -> s.getMetadata().getName(), Function.identity()));
            Assert.assertTrue(secretMap.containsKey("token-private-key"));
            Assert.assertTrue(secretMap.containsKey("token-public-key"));
            Assert.assertTrue(secretMap.containsKey("token-superuser"));
            Assert.assertTrue(secretMap.containsKey("token-admin"));
            Assert.assertTrue(secretMap.containsKey("token-proxy"));
            Assert.assertTrue(secretMap.containsKey("token-websocket"));


        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }


    @Test
    public void testInstallWithHelm() throws Exception {
        try {
            helmInstall();
            awaitOperatorRunning();

            final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
            applyPulsarCluster(specsToYaml(specs));
            awaitInstalled();
            helmUninstall();
            client.resources(PulsarCluster.class).inNamespace(namespace)
                    .withName("pulsar-cluster")
                    .delete();
            awaitUninstalled();
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
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

        client.pods()
                .inNamespace(namespace)
                .withName("pulsar-zookeeper-0")
                .waitUntilReady(90, TimeUnit.SECONDS);

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

        client.pods()
                .inNamespace(namespace)
                .withName("pulsar-bookkeeper-0")
                .waitUntilReady(90, TimeUnit.SECONDS);

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

        client.pods()
                .inNamespace(namespace)
                .withName("pulsar-broker-0")
                .waitUntilReady(90, TimeUnit.SECONDS);

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

        client.apps().deployments()
                .inNamespace(namespace)
                .withName("pulsar-proxy")
                .waitUntilReady(90, TimeUnit.SECONDS);

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

        client.apps().deployments()
                .inNamespace(namespace)
                .withName("pulsar-autorecovery")
                .waitUntilReady(90, TimeUnit.SECONDS);

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

        client.apps().deployments()
                .inNamespace(namespace)
                .withName("pulsar-bastion")
                .waitUntilReady(90, TimeUnit.SECONDS);

    }

    private void awaitFunctionsWorkerRunning() {
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

        client.apps().statefulSets()
                .inNamespace(namespace)
                .withName("pulsar-function")
                .waitUntilReady(90, TimeUnit.SECONDS);

    }

    private void awaitUninstalled() {
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
}
