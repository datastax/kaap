package com.datastax.oss.pulsaroperator.tests;

import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
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
public class PulsarClusterTest extends BaseK8sEnvironment {

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
        Assert.assertTrue(crds.contains("pulsarclusters.com.datastax.oss"));
    }

    @Test
    public void testBaseInstall() throws Exception {
        applyRBACManifests();
        applyOperatorManifests();
        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        kubectlApply(specsToYaml(specs));
        awaitInstalled();

        specs.getZookeeper().setReplicas(3);
        kubectlApply(specsToYaml(specs));

        client.pods()
                .inNamespace(NAMESPACE)
                .withName("pulsar-zookeeper-2")
                .waitUntilReady(90, TimeUnit.SECONDS);

        specs.getBookkeeper().setReplicas(3);
        kubectlApply(specsToYaml(specs));

        client.pods()
                .inNamespace(NAMESPACE)
                .withName("pulsar-bookkeeper-2")
                .waitUntilReady(90, TimeUnit.SECONDS);

        container.kubectl().delete.namespace(NAMESPACE).run("PulsarCluster", "pulsar-cluster");
        awaitUninstalled();
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
                        .existingStorageClassName("local-path")
                        .build())
                .build());

        defaultSpecs.setZookeeper(ZooKeeperSpec.builder()
                .replicas(1)
                .resources(new ResourceRequirementsBuilder()
                        .withRequests(Map.of("memory", Quantity.parse("512Mi"), "cpu", Quantity.parse("1")))
                        .build())
                .dataVolume(VolumeConfig.builder()
                        .size("100M")
                        .build()
                )
                .build());

        defaultSpecs.setBookkeeper(BookKeeperSpec.builder()
                .replicas(1)
                .resources(new ResourceRequirementsBuilder()
                        .withRequests(Map.of("memory", Quantity.parse("512Mi"), "cpu", Quantity.parse("1")))
                        .build())
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
        return defaultSpecs;
    }


    @Test
    public void testInstallWithHelm() throws Exception {
        helmInstall();
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            final int zk = client.pods().inNamespace(NAMESPACE).list().getItems().size();
            Assert.assertEquals(zk, 1);
        });
        kubectlApply(getHelmExampleFilePath("cluster.yaml"));
        awaitInstalled();
        container.kubectl().delete.namespace(NAMESPACE).run("PulsarCluster", "pulsar-cluster");
        awaitUninstalled();
    }

    private void awaitInstalled() {
        awaitOperatorRunning();
        awaitZooKeeperRunning();
        awaitBookKeeperRunning();
    }

    private void awaitZooKeeperRunning() {
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            log.info("statefulsets {}", client.apps().statefulSets().list().getItems());
            log.info("pdb1 {}", client.policy().v1().podDisruptionBudget().list().getItems());
            Assert.assertEquals(client.pods().withLabel("component", "zookeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.policy().v1().podDisruptionBudget().
                    withLabel("component", "zookeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.configMaps().withLabel("component", "zookeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.services().withLabel("component", "zookeeper").list().getItems().size(), 2);
            Assert.assertEquals(client.apps().statefulSets()
                    .withLabel("component", "zookeeper").list().getItems().size(), 1);
        });

        client.pods()
                .inNamespace(NAMESPACE)
                .withName("pulsar-zookeeper-0")
                .waitUntilReady(90, TimeUnit.SECONDS);

        final Pod jobPod = client.pods().inNamespace(NAMESPACE).withLabel("job-name", "pulsar-zookeeper")
                .list().getItems().get(0);
        client.pods().inNamespace(NAMESPACE).withName(jobPod.getMetadata().getName())
                .waitUntilCondition(pod -> pod.getStatus().getPhase().equals("Succeeded"), 2, TimeUnit.MINUTES);
    }

    private void awaitBookKeeperRunning() {
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Assert.assertEquals(client.pods().withLabel("component", "bookkeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.configMaps().withLabel("component", "bookkeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.apps().statefulSets()
                    .withLabel("component", "bookkeeper").list().getItems().size(), 1);
        });

        client.pods()
                .inNamespace(NAMESPACE)
                .withName("pulsar-bookkeeper-0")
                .waitUntilReady(90, TimeUnit.SECONDS);

    }

    private void awaitUninstalled() {
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            Assert.assertEquals(client.pods().withLabel("app", "pulsar").list().getItems().size(), 0);
            Assert.assertEquals(client.policy().v1().podDisruptionBudget().withLabel("app", "pulsar").list().getItems().size(), 0);
            Assert.assertEquals(client.configMaps().withLabel("app", "pulsar").list().getItems().size(), 0);
            Assert.assertEquals(client.services().withLabel("app", "pulsar").list().getItems().size(), 0);
            Assert.assertEquals(client.apps().statefulSets().withLabel("app", "pulsar").list().getItems().size(), 0);
            Assert.assertEquals(client.batch().v1().jobs().withLabel("app", "pulsar").list().getItems().size(), 0);
        });
    }
}
