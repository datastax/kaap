package com.datastax.oss.pulsaroperator.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.controllers.utils.TokenAuthProvisioner;
import com.datastax.oss.pulsaroperator.crds.BaseComponentStatus;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.autorecovery.Autorecovery;
import com.datastax.oss.pulsaroperator.crds.bastion.Bastion;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorker;
import com.datastax.oss.pulsaroperator.crds.proxy.Proxy;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@JBossLog
public class PulsarClusterControllerTest {

    public static final String GLOBAL_SPEC_YAML_PART = """
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
                dnsConfig:
                  options:
                  - name: ndots
                    value: 4
                kubernetesClusterDomain: cluster.local
                tls:
                  enabled: false
                  defaultSecretName: pulsar-tls
                  zookeeper:
                    enabled: false
                  bookkeeper:
                    enabled: false
                  broker:
                    enabled: false
                  proxy:
                    enabled: false
                    enabledWithBroker: false
                  ssCa:
                    enabled: false
                persistence: true
                restartOnConfigMapChange: false
                auth:
                  enabled: false
                  token:
                    publicKeyFile: my-public.key
                    privateKeyFile: my-private.key
                    superUserRoles:
                    - admin
                    - proxy
                    - superuser
                    - websocket
                    proxyRoles:
                    - proxy
                    initialize: true
                image: apachepulsar/pulsar:2.10.2
                imagePullPolicy: IfNotPresent
                storage:
                  existingStorageClassName: default""";
    static final String NAMESPACE = "ns";

    TokenAuthProvisioner tokenAuthProvisioner;

    @BeforeMethod
    public void setup() {
        tokenAuthProvisioner = mock(TokenAuthProvisioner.class);
    }

    @Test
    public void testInstallCluster() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:2.10.2
                """;
        // first installation, install zk from scratch
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        UpdateControl<PulsarCluster> control = invokeController(client, spec, r -> null);
        final MockKubernetesClient.ResourceInteraction<ZooKeeper> createdZk =
                client.getCreatedResource(ZooKeeper.class);
        assertZkYaml(createdZk.getResourceYaml());
        Assert.assertEquals(client.countCreatedResources(), 1);
        assertUpdateControlInitializing(control);


        // zk not ready yet (no condition), must reschedule
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> createdZk.getResource());
        Assert.assertEquals(client.countCreatedResources(), 0);
        assertUpdateControlInitializing(control);

        // zk not ready yet (with condition), must reschedule
        setReadyCondition(createdZk.getResource().getStatus(), false);
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> createdZk.getResource());
        Assert.assertEquals(client.countCreatedResources(), 0);
        assertUpdateControlInitializing(control);


        // zk ready, bk starts
        setReadyCondition(createdZk.getResource().getStatus(), true);
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> {
            if (r.isAssignableFrom(ZooKeeper.class)) {
                return createdZk.getResource();
            }

            return null;
        });
        Assert.assertEquals(client.countCreatedResources(), 1);
        assertUpdateControlInitializing(control);
        final MockKubernetesClient.ResourceInteraction<BookKeeper> createdBk =
                client.getCreatedResource(BookKeeper.class);
        assertBkYaml(createdBk.getResourceYaml());


        // bk not ready yet (no condition), must reschedule
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> {
            if (r.isAssignableFrom(ZooKeeper.class)) {
                return createdZk.getResource();
            }
            if (r.isAssignableFrom(BookKeeper.class)) {
                return createdBk.getResource();
            }
            return null;
        });
        Assert.assertEquals(client.countCreatedResources(), 0);
        assertUpdateControlInitializing(control);


        // bk not ready yet (with condition), must reschedule
        setReadyCondition(createdBk.getResource().getStatus(), false);
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> {
            if (r.isAssignableFrom(ZooKeeper.class)) {
                return createdZk.getResource();
            }
            if (r.isAssignableFrom(BookKeeper.class)) {
                return createdBk.getResource();
            }
            return null;
        });
        Assert.assertEquals(client.countCreatedResources(), 0);
        assertUpdateControlInitializing(control);


        // bk ready, let's start everything
        setReadyCondition(createdBk.getResource().getStatus(), true);
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> {
            if (r.isAssignableFrom(ZooKeeper.class)) {
                return createdZk.getResource();
            }
            if (r.isAssignableFrom(BookKeeper.class)) {
                return createdBk.getResource();
            }
            return null;
        });
        Assert.assertEquals(client.countCreatedResources(), 4);
        assertUpdateControlInitializing(control);

        assertBrokerYaml(client);
        assertProxyYaml(client);
        assertAutorecoveryYaml(client);
        assertBastionYaml(client);


        final MockKubernetesClient clientCreatedEveryoneElse = client;
        // not everyone ready, must reschedule
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> {
            if (r.isAssignableFrom(ZooKeeper.class)) {
                return createdZk.getResource();
            }
            if (r.isAssignableFrom(BookKeeper.class)) {
                return createdBk.getResource();
            }
            return clientCreatedEveryoneElse.getCreatedResource((Class<? extends HasMetadata>) r).getResource();
        });
        Assert.assertEquals(client.countCreatedResources(), 0);
        assertUpdateControlInitializing(control);


        // broker ready, functions worker starts
        setReadyCondition(clientCreatedEveryoneElse.getCreatedResource(Broker.class).getResource().getStatus(), true);
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> {
            if (r.isAssignableFrom(ZooKeeper.class)) {
                return createdZk.getResource();
            }
            if (r.isAssignableFrom(BookKeeper.class)) {
                return createdBk.getResource();
            }
            final MockKubernetesClient.ResourceInteraction<? extends HasMetadata> resourceInteraction =
                    clientCreatedEveryoneElse.getCreatedResource((Class<? extends HasMetadata>) r);
            if (resourceInteraction == null) {
                return null;
            }
            final CustomResource resource = (CustomResource) resourceInteraction.getResource();
            ((BaseComponentStatus) resource.getStatus()).setConditions(
                    List.of(AbstractController.createReadyCondition(1L)));
            return resource;
        });
        Assert.assertEquals(client.countCreatedResources(), 1);
        assertUpdateControlInitializing(control);
        assertFnWorkerYaml(client);

        final MockKubernetesClient clientCreatedFnWorker = client;


        // everyone ready, update cluster condition
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> {
            if (r.isAssignableFrom(ZooKeeper.class)) {
                return createdZk.getResource();
            }
            if (r.isAssignableFrom(BookKeeper.class)) {
                return createdBk.getResource();
            }
            MockKubernetesClient.ResourceInteraction<? extends HasMetadata> resourceInteraction =
                    clientCreatedEveryoneElse.getCreatedResource((Class<? extends HasMetadata>) r);
            if (resourceInteraction == null) {
                resourceInteraction = clientCreatedFnWorker.getCreatedResource((Class<? extends HasMetadata>) r);
            }
            final CustomResource resource = (CustomResource) resourceInteraction.getResource();
            ((BaseComponentStatus) resource.getStatus()).setConditions(
                    List.of(AbstractController.createReadyCondition(1L)));
            return resource;
        });
        Assert.assertEquals(client.countCreatedResources(), 0);
        Assert.assertNull(control.getScheduleDelay().orElse(null));
        Condition readyCondition = getReadyCondition(control.getResource().getStatus());
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_TRUE);
    }

    private void assertFnWorkerYaml(MockKubernetesClient client) {
        Assert.assertEquals(client.getCreatedResource(FunctionsWorker.class).getResourceYaml(), """
                ---
                apiVersion: pulsar.oss.datastax.com/v1alpha1
                kind: FunctionsWorker
                metadata:
                  name: pulsarname-functionsworker
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  %s
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
                  conditions: []
                """.formatted(GLOBAL_SPEC_YAML_PART));
    }

    private void assertBastionYaml(MockKubernetesClient client) {
        Assert.assertEquals(client.getCreatedResource(Bastion.class).getResourceYaml(), """
                ---
                apiVersion: pulsar.oss.datastax.com/v1alpha1
                kind: Bastion
                metadata:
                  name: pulsarname-bastion
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  %s
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
                  conditions: []
                """.formatted(GLOBAL_SPEC_YAML_PART));
    }

    private void assertAutorecoveryYaml(MockKubernetesClient client) {
        Assert.assertEquals(client.getCreatedResource(Autorecovery.class).getResourceYaml(), """
                ---
                apiVersion: pulsar.oss.datastax.com/v1alpha1
                kind: Autorecovery
                metadata:
                  name: pulsarname-autorecovery
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  %s
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
                  conditions: []
                """.formatted(GLOBAL_SPEC_YAML_PART));
    }

    private void assertProxyYaml(MockKubernetesClient client) {
        Assert.assertEquals(client.getCreatedResource(Proxy.class).getResourceYaml(), """
                ---
                apiVersion: pulsar.oss.datastax.com/v1alpha1
                kind: Proxy
                metadata:
                  name: pulsarname-proxy
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  %s
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
                  conditions: []
                """.formatted(GLOBAL_SPEC_YAML_PART));
    }

    private void assertBrokerYaml(MockKubernetesClient client) {
        Assert.assertEquals(client.getCreatedResource(Broker.class).getResourceYaml(), """
                ---
                apiVersion: pulsar.oss.datastax.com/v1alpha1
                kind: Broker
                metadata:
                  name: pulsarname-broker
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  %s
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
                  conditions: []
                """.formatted(GLOBAL_SPEC_YAML_PART));
    }

    private void assertBkYaml(String yaml) {
        Assert.assertEquals(yaml, """
                ---
                apiVersion: pulsar.oss.datastax.com/v1alpha1
                kind: BookKeeper
                metadata:
                  name: pulsarname-bookkeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  %s
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
                    autoscaler:
                      enabled: false
                      periodMs: 10000
                      diskUsageToleranceHwm: 0.92
                      diskUsageToleranceLwm: 0.75
                      minWritableBookies: 3
                      scaleUpBy: 1
                      scaleDownBy: 1
                      stabilizationWindowMs: 300000
                status:
                  conditions: []
                """.formatted(GLOBAL_SPEC_YAML_PART));
    }

    private void assertZkYaml(String yaml) {
        Assert.assertEquals(yaml, """
                ---
                apiVersion: pulsar.oss.datastax.com/v1alpha1
                kind: ZooKeeper
                metadata:
                  name: pulsarname-zookeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  %s
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
                  conditions: []
                """.formatted(GLOBAL_SPEC_YAML_PART));
    }


    @FunctionalInterface
    private interface ExistingResourceProvider {
        Object getExistingCustomResource(Class<?> resourceClass);
    }

    @SneakyThrows
    private UpdateControl<PulsarCluster> invokeController(MockKubernetesClient client,
                                                          String spec,
                                                          ExistingResourceProvider existingResourceProvider) {

        final PulsarClusterController controller =
                new PulsarClusterController(client.getClient()) {

                    @Override
                    protected <CR extends CustomResource<SPEC, ?>, SPEC> CR getExistingCustomResource(
                            Class<CR> resourceClass, String namespace, String crFullName) {
                        Assert.assertEquals(namespace, NAMESPACE);
                        return (CR) existingResourceProvider.getExistingCustomResource(resourceClass);
                    }

                    @Override
                    protected TokenAuthProvisioner getTokenAuthProvisioner(String namespace) {
                        Assert.assertEquals(namespace, NAMESPACE);
                        return tokenAuthProvisioner;
                    }
                };

        final PulsarCluster cr = new PulsarCluster();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("pulsar-cluster");
        meta.setNamespace(NAMESPACE);
        cr.setMetadata(meta);

        final PulsarClusterSpec fSpec = MockKubernetesClient.readYaml(spec, PulsarClusterSpec.class);
        cr.setSpec(fSpec);

        return controller.reconcile(cr, mock(Context.class));
    }

    private void setReadyCondition(BaseComponentStatus status, boolean ready) {
        final List<Condition> conditions = status.getConditions();
        final Condition current =
                conditions.stream().filter(c -> c.getType().equals(CRDConstants.CONDITIONS_TYPE_READY))
                        .findAny().orElse(null);
        if (current == null) {
            List<Condition> newConditions = new ArrayList<>(conditions);
            if (ready) {
                newConditions.add(AbstractController.createReadyCondition(1L));
            } else {
                newConditions.add(AbstractController.createNotReadyInitializingCondition(1L));
            }
            status.setConditions(newConditions);
        } else {
            current.setStatus(CRDConstants.CONDITIONS_STATUS_TRUE);
        }
    }

    private Condition getReadyCondition(BaseComponentStatus status) {
        final List<Condition> conditions = status.getConditions();
        return conditions.stream().filter(c -> c.getType().equals(CRDConstants.CONDITIONS_TYPE_READY))
                .findAny().get();
    }

    private void assertUpdateControlInitializing(UpdateControl<PulsarCluster> updateControl) {
        Assert.assertEquals(updateControl.getScheduleDelay().get().longValue(), 5000L);
        Condition readyCondition = getReadyCondition(updateControl.getResource().getStatus());
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_FALSE);
        Assert.assertEquals(readyCondition.getReason(), CRDConstants.CONDITIONS_TYPE_READY_REASON_INITIALIZING);
    }

    @Test
    public void testAuthTokenProvisioner() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:2.10.2
                    auth:
                        enabled: true
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        UpdateControl<PulsarCluster> control = invokeController(client, spec, r -> null);
        Assert.assertEquals(client.countCreatedResources(), 1);
        assertUpdateControlInitializing(control);
        verify(tokenAuthProvisioner).generateSecretsIfAbsent(any());
    }
}