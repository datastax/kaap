package com.datastax.oss.pulsaroperator.controllers;

import static org.mockito.Mockito.mock;
import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.autorecovery.Autorecovery;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.proxy.Proxy;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.Test;

@JBossLog
public class PulsarClusterControllerTest {

    static final String NAMESPACE = "ns";

    @Test
    public void testDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:2.10.2
                """;
        final MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(client.getCreatedResource(ZooKeeper.class).getResourceYaml(), """
                ---
                apiVersion: com.datastax.oss/v1alpha1
                kind: ZooKeeper
                metadata:
                  name: pulsarname-zookeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: com.datastax.oss/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  global:
                    name: pulsarname
                    components:
                      zookeeperBaseName: zookeeper
                      bookkeeperBaseName: bookkeeper
                      brokerBaseName: broker
                      proxyBaseName: proxy
                      autorecoveryBaseName: autorecovery
                    kubernetesClusterDomain: cluster.local
                    tls:
                      enabled: false
                      defaultSecretName: pulsar-tls
                    persistence: true
                    restartOnConfigMapChange: false
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    storage:
                      existingStorageClassName: default
                status:
                  ready: false
                """);

        Assert.assertEquals(client.getCreatedResource(BookKeeper.class).getResourceYaml(), """
                ---
                apiVersion: com.datastax.oss/v1alpha1
                kind: BookKeeper
                metadata:
                  name: pulsarname-bookkeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: com.datastax.oss/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  global:
                    name: pulsarname
                    components:
                      zookeeperBaseName: zookeeper
                      bookkeeperBaseName: bookkeeper
                      brokerBaseName: broker
                      proxyBaseName: proxy
                      autorecoveryBaseName: autorecovery
                    kubernetesClusterDomain: cluster.local
                    tls:
                      enabled: false
                      defaultSecretName: pulsar-tls
                    persistence: true
                    restartOnConfigMapChange: false
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    storage:
                      existingStorageClassName: default
                status:
                  ready: false
                """);

        Assert.assertEquals(client.getCreatedResource(Broker.class).getResourceYaml(), """
              ---
              apiVersion: com.datastax.oss/v1alpha1
              kind: Broker
              metadata:
                name: pulsarname-broker
                namespace: ns
                ownerReferences:
                - apiVersion: com.datastax.oss/v1alpha1
                  kind: PulsarCluster
                  blockOwnerDeletion: true
                  controller: true
                  name: pulsar-cluster
              spec:
                global:
                  name: pulsarname
                  components:
                    zookeeperBaseName: zookeeper
                    bookkeeperBaseName: bookkeeper
                    brokerBaseName: broker
                    proxyBaseName: proxy
                    autorecoveryBaseName: autorecovery
                  kubernetesClusterDomain: cluster.local
                  tls:
                    enabled: false
                    defaultSecretName: pulsar-tls
                  persistence: true
                  restartOnConfigMapChange: false
                  image: apachepulsar/pulsar:2.10.2
                  imagePullPolicy: IfNotPresent
                  storage:
                    existingStorageClassName: default
              status:
                ready: false
                """);


        Assert.assertEquals(client.getCreatedResource(Proxy.class).getResourceYaml(), """
                ---
                apiVersion: com.datastax.oss/v1alpha1
                kind: Proxy
                metadata:
                  name: pulsarname-proxy
                  namespace: ns
                  ownerReferences:
                  - apiVersion: com.datastax.oss/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  global:
                    name: pulsarname
                    components:
                      zookeeperBaseName: zookeeper
                      bookkeeperBaseName: bookkeeper
                      brokerBaseName: broker
                      proxyBaseName: proxy
                      autorecoveryBaseName: autorecovery
                    kubernetesClusterDomain: cluster.local
                    tls:
                      enabled: false
                      defaultSecretName: pulsar-tls
                    persistence: true
                    restartOnConfigMapChange: false
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    storage:
                      existingStorageClassName: default
                status:
                  ready: false
                """);


        Assert.assertEquals(client.getCreatedResource(Autorecovery.class).getResourceYaml(), """
                ---
                apiVersion: com.datastax.oss/v1alpha1
                kind: Autorecovery
                metadata:
                  name: pulsarname-autorecovery
                  namespace: ns
                  ownerReferences:
                  - apiVersion: com.datastax.oss/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  global:
                    name: pulsarname
                    components:
                      zookeeperBaseName: zookeeper
                      bookkeeperBaseName: bookkeeper
                      brokerBaseName: broker
                      proxyBaseName: proxy
                      autorecoveryBaseName: autorecovery
                    kubernetesClusterDomain: cluster.local
                    tls:
                      enabled: false
                      defaultSecretName: pulsar-tls
                    persistence: true
                    restartOnConfigMapChange: false
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    storage:
                      existingStorageClassName: default
                status:
                  ready: false
                """);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<PulsarCluster>
                result = invokeController(mockKubernetesClient, spec);
        Assert.assertTrue(result.isUpdateStatus());
        return mockKubernetesClient;
    }

    private UpdateControl<PulsarCluster> invokeController(MockKubernetesClient mockKubernetesClient, String spec)
            throws Exception {
        final PulsarClusterController controller = new PulsarClusterController(mockKubernetesClient.getClient());

        final PulsarCluster cluster = new PulsarCluster();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("pulsar-cluster");
        meta.setNamespace(NAMESPACE);
        cluster.setMetadata(meta);

        final PulsarClusterSpec clusterSpec = MockKubernetesClient.readYaml(spec, PulsarClusterSpec.class);
        cluster.setSpec(clusterSpec);

        final UpdateControl<PulsarCluster> result = controller.reconcile(cluster, mock(Context.class));
        return result;
    }
}