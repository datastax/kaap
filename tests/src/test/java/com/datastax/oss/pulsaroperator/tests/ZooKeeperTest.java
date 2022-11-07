package com.datastax.oss.pulsaroperator.tests;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
public class ZooKeeperTest extends BaseK8sEnvironment {

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
        Assert.assertTrue(crds.contains("pulsarclusters.com.datastax.oss"));
    }

    @Test
    @SuppressWarnings("checkstyle:linelength")
    public void testInstallZookeeper() throws Exception {
        applyRBACManifests();
        applyOperatorManifests();
        String manifest = """
                apiVersion: com.datastax.oss/v1alpha1
                kind: PulsarCluster
                metadata:
                  name: pulsar-cluster
                spec:
                  global:
                    name: pulsar
                    persistence: true
                    image: %s
                  zookeeper:
                    component: zookeeper
                    replicas: 1
                    config:
                      PULSAR_MEM: "-Xms1g -Xmx1g -Dcom.sun.management.jmxremote -Djute.maxbuffer=10485760"
                      PULSAR_GC: "-XX:+UseG1GC"
                      PULSAR_LOG_LEVEL: "info"
                      PULSAR_LOG_ROOT_LEVEL: "info"
                      PULSAR_EXTRA_OPTS: "-Dzookeeper.tcpKeepAlive=true -Dzookeeper.clientTcpKeepAlive=true -Dpulsar.log.root.level=info"
                    imagePullPolicy: Never
                    updateStrategy:
                      type: RollingUpdate
                    dataVolume:
                      name: data
                      size: 100M
                      # K3S storage class name https://docs.k3s.io/storage
                      existingStorageClassName:  local-path
                """.formatted(PULSAR_IMAGE);
        kubectlApply(manifest);

        awaitInstalled();
        container.kubectl().delete.namespace(NAMESPACE).run("PulsarCluster", "pulsar-cluster");
        awaitUninstalled();
    }



    @Test
    public void testInstallZookeeperWithHelm() throws Exception {
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
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            log.info("statefulsets {}", client.apps().statefulSets().list().getItems());
            Assert.assertEquals(client.pods().withLabel("component", "zookeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.configMaps().withLabel("component", "zookeeper").list().getItems().size(), 1);
            Assert.assertEquals(client.services().withLabel("component", "zookeeper").list().getItems().size(), 2);
            Assert.assertEquals(client.apps().statefulSets()
                    .withLabel("component", "zookeeper").list().getItems().size(), 1);
        });
    }

    private void awaitUninstalled() {
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
            log.info("statefulsets {}", client.apps().statefulSets().list().getItems());
            Assert.assertEquals(client.pods().withLabel("component", "zookeeper").list().getItems().size(), 0);
            Assert.assertEquals(client.configMaps().withLabel("component", "zookeeper").list().getItems().size(), 0);
            Assert.assertEquals(client.services().withLabel("component", "zookeeper").list().getItems().size(), 0);
            Assert.assertEquals(client.apps().statefulSets()
                    .withLabel("component", "zookeeper").list().getItems().size(), 0);
        });
    }
}
