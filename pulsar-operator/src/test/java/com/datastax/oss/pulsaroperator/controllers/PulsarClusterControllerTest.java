package com.datastax.oss.pulsaroperator.controllers;

import static org.mockito.Mockito.mock;
import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.autorecovery.Autorecovery;
import com.datastax.oss.pulsaroperator.crds.bastion.Bastion;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorker;
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
                      bastionBaseName: bastion
                      functionsWorkerBaseName: function
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
                  zookeeper:
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    replicas: 3
                    probe:
                      enabled: true
                      timeout: 30
                      initial: 20
                      period: 30
                    pdb:
                      enabled: true
                      maxUnavailable: 1
                    podManagementPolicy: Parallel
                    updateStrategy:
                      type: RollingUpdate
                    gracePeriod: 60
                    resources:
                      requests:
                        cpu: 0.3
                        memory: 1Gi
                    dataVolume:
                      name: data
                      size: 5Gi
                      existingStorageClassName: default
                    metadataInitializationJob:
                      timeout: 60
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
                      bastionBaseName: bastion
                      functionsWorkerBaseName: function
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
                  bookkeeper:
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    replicas: 3
                    probe:
                      enabled: true
                      timeout: 5
                      initial: 10
                      period: 30
                    pdb:
                      enabled: true
                      maxUnavailable: 1
                    updateStrategy:
                      type: RollingUpdate
                    podManagementPolicy: Parallel
                    gracePeriod: 60
                    resources:
                      requests:
                        cpu: 1
                        memory: 2Gi
                    volumes:
                      journal:
                        name: journal
                        size: 20Gi
                        existingStorageClassName: default
                      ledgers:
                        name: ledgers
                        size: 50Gi
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
                      bastionBaseName: bastion
                      functionsWorkerBaseName: function
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
                  broker:
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    replicas: 3
                    probe:
                      enabled: true
                      timeout: 5
                      initial: 10
                      period: 30
                    pdb:
                      enabled: true
                      maxUnavailable: 1
                    functionsWorkerEnabled: false
                    webSocketServiceEnabled: false
                    transactions:
                      enabled: false
                      partitions: 16
                    gracePeriod: 60
                    resources:
                      requests:
                        cpu: 1
                        memory: 2Gi
                    service:
                      type: ClusterIP
                    autoscaler:
                      enabled: false
                      periodMs: 10000
                      min: 1
                      lowerCpuThreshold: 0.3
                      higherCpuThreshold: 0.8
                      scaleUpBy: 1
                      scaleDownBy: 1
                      stabilizationWindowMs: 300000
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
                      bastionBaseName: bastion
                      functionsWorkerBaseName: function
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
                  proxy:
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    replicas: 3
                    probe:
                      enabled: true
                      timeout: 5
                      initial: 10
                      period: 30
                    pdb:
                      enabled: true
                      maxUnavailable: 1
                    updateStrategy:
                      rollingUpdate:
                        maxSurge: 1
                        maxUnavailable: 0
                      type: RollingUpdate
                    gracePeriod: 60
                    resources:
                      requests:
                        cpu: 1
                        memory: 1Gi
                    service:
                      type: LoadBalancer
                      enablePlainTextWithTLS: false
                    webSocket:
                      enabled: true
                      resources:
                        requests:
                          cpu: 1
                          memory: 1Gi
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
                      bastionBaseName: bastion
                      functionsWorkerBaseName: function
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
                  autorecovery:
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    replicas: 1
                    gracePeriod: 60
                    resources:
                      requests:
                        cpu: 0.3
                        memory: 512Mi
                status:
                  ready: false
                """);


        Assert.assertEquals(client.getCreatedResource(Bastion.class).getResourceYaml(), """
                ---
                apiVersion: com.datastax.oss/v1alpha1
                kind: Bastion
                metadata:
                  name: pulsarname-bastion
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
                      bastionBaseName: bastion
                      functionsWorkerBaseName: function
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
                  bastion:
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    replicas: 1
                    gracePeriod: 60
                    resources:
                      requests:
                        cpu: 0.25
                        memory: 256Mi
                    targetProxy: true
                status:
                  ready: false
                """);


        Assert.assertEquals(client.getCreatedResource(FunctionsWorker.class).getResourceYaml(), """
                ---
                apiVersion: com.datastax.oss/v1alpha1
                kind: FunctionsWorker
                metadata:
                  name: pulsarname-functionsworker
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
                      bastionBaseName: bastion
                      functionsWorkerBaseName: function
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
                  functionsWorker:
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    replicas: 0
                    probe:
                      enabled: true
                      timeout: 5
                      initial: 10
                      period: 30
                    pdb:
                      enabled: true
                      maxUnavailable: 1
                    updateStrategy:
                      type: RollingUpdate
                    podManagementPolicy: Parallel
                    gracePeriod: 60
                    resources:
                      requests:
                        cpu: 1
                        memory: 4Gi
                    service:
                      type: ClusterIP
                    logsVolume:
                      name: logs
                      size: 5Gi
                      existingStorageClassName: default
                    runtime: process
                    rbac:
                      create: true
                      namespaced: true
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