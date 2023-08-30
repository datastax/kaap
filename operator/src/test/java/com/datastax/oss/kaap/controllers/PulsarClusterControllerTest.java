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
package com.datastax.oss.kaap.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import com.datastax.oss.kaap.controllers.utils.TokenAuthProvisioner;
import com.datastax.oss.kaap.crds.BaseComponentStatus;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.autorecovery.Autorecovery;
import com.datastax.oss.kaap.crds.bastion.Bastion;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeper;
import com.datastax.oss.kaap.crds.broker.Broker;
import com.datastax.oss.kaap.crds.cluster.PulsarCluster;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.crds.function.FunctionsWorker;
import com.datastax.oss.kaap.crds.proxy.Proxy;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeper;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@JBossLog
public class PulsarClusterControllerTest {

    public static final String GLOBAL_SPEC_YAML_PART = """
            global:
                name: pulsar-spec-1
                clusterName: public-ap-southeast-2
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
                  caPath: /etc/ssl/certs/ca-certificates.crt
                  zookeeper:
                    enabled: false
                  bookkeeper:
                    enabled: false
                  broker:
                    enabled: false
                  proxy:
                    enabled: false
                    enabledWithBroker: false
                  autorecovery:
                    enabled: false
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
                  existingStorageClassName: default
                antiAffinity:
                  host:
                    enabled: true
                    required: true
                  zone:
                    enabled: false
                zookeeperPlainSslStorePassword: false""";
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
                    name: pulsar-spec-1
                    clusterName: public-ap-southeast-2
                    image: apachepulsar/pulsar:2.10.2
                """;
        // first installation, install zk from scratch
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        UpdateControl<PulsarCluster> control = invokeController(client, spec, r -> null);
        final MockKubernetesClient.ResourceInteraction<ZooKeeper> createdZk =
                client.getCreatedResource(ZooKeeper.class);
        assertZkYaml(createdZk.getResourceYaml());
        Assert.assertEquals(client.countCreatedResources(), 1);
        KubeTestUtil.assertUpdateControlInitializing(control);


        // zk not ready yet (no condition), must reschedule
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> createdZk.getResource());
        Assert.assertEquals(client.countCreatedResources(), 0);
        KubeTestUtil.assertUpdateControlInitializing(control);

        // zk not ready yet (with condition), must reschedule
        setReadyCondition(createdZk.getResource().getStatus(), false);
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> createdZk.getResource());
        Assert.assertEquals(client.countCreatedResources(), 0);
        KubeTestUtil.assertUpdateControlInitializing(control);


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
        KubeTestUtil.assertUpdateControlInitializing(control);
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
        KubeTestUtil.assertUpdateControlInitializing(control);


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
        KubeTestUtil.assertUpdateControlInitializing(control);


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
        KubeTestUtil.assertUpdateControlInitializing(control);

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
        KubeTestUtil.assertUpdateControlInitializing(control);


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
        KubeTestUtil.assertUpdateControlInitializing(control);
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
        Condition readyCondition = KubeTestUtil.getReadyCondition(control.getResource().getStatus());
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_TRUE);
    }

    private void assertFnWorkerYaml(MockKubernetesClient client) {
        Assert.assertEquals(client.getCreatedResource(FunctionsWorker.class).getResourceYaml(), """
                ---
                apiVersion: kaap.oss.datastax.com/v1beta1
                kind: FunctionsWorker
                metadata:
                  name: pulsar-spec-1-functionsworker
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
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
                    pdb:
                      enabled: true
                      maxUnavailable: 1
                    probes:
                      readiness:
                        enabled: true
                        timeoutSeconds: 5
                        initialDelaySeconds: 10
                        periodSeconds: 30
                      liveness:
                        enabled: true
                        timeoutSeconds: 5
                        initialDelaySeconds: 10
                        periodSeconds: 30
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
                apiVersion: kaap.oss.datastax.com/v1beta1
                kind: Bastion
                metadata:
                  name: pulsar-spec-1-bastion
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
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
                apiVersion: kaap.oss.datastax.com/v1beta1
                kind: Autorecovery
                metadata:
                  name: pulsar-spec-1-autorecovery
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
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
                apiVersion: kaap.oss.datastax.com/v1beta1
                kind: Proxy
                metadata:
                  name: pulsar-spec-1-proxy
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
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
                    pdb:
                      enabled: true
                      maxUnavailable: 1
                    probes:
                      readiness:
                        enabled: true
                        timeoutSeconds: 5
                        initialDelaySeconds: 10
                        periodSeconds: 30
                      liveness:
                        enabled: true
                        timeoutSeconds: 5
                        initialDelaySeconds: 10
                        periodSeconds: 30
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
                      probes: {}
                    kafka:
                      enabled: false
                      exposePorts: true
                    setsUpdateStrategy: RollingUpdate
                status:
                  conditions: []
                """.formatted(GLOBAL_SPEC_YAML_PART));
    }

    private void assertBrokerYaml(MockKubernetesClient client) {
        Assert.assertEquals(client.getCreatedResource(Broker.class).getResourceYaml(), """
                ---
                apiVersion: kaap.oss.datastax.com/v1beta1
                kind: Broker
                metadata:
                  name: pulsar-spec-1-broker
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
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
                    pdb:
                      enabled: true
                      maxUnavailable: 1
                    probes:
                      readiness:
                        enabled: true
                        timeoutSeconds: 5
                        initialDelaySeconds: 10
                        periodSeconds: 30
                      liveness:
                        enabled: true
                        timeoutSeconds: 5
                        initialDelaySeconds: 10
                        periodSeconds: 30
                      useHealthCheckForLiveness: true
                      useHealthCheckForReadiness: true
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
                      periodMs: 60000
                      min: 1
                      lowerCpuThreshold: 0.3
                      higherCpuThreshold: 0.8
                      scaleUpBy: 1
                      scaleDownBy: 1
                      stabilizationWindowMs: 300000
                      resourcesUsageSource: PulsarLBReport
                    kafka:
                      enabled: false
                      exposePorts: true
                    setsUpdateStrategy: RollingUpdate
                status:
                  conditions: []
                """.formatted(GLOBAL_SPEC_YAML_PART));
    }

    private void assertBkYaml(String yaml) {
        Assert.assertEquals(yaml, """
                ---
                apiVersion: kaap.oss.datastax.com/v1beta1
                kind: BookKeeper
                metadata:
                  name: pulsar-spec-1-bookkeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
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
                    pdb:
                      enabled: true
                      maxUnavailable: 1
                    probes:
                      readiness:
                        enabled: true
                        timeoutSeconds: 5
                        initialDelaySeconds: 10
                        periodSeconds: 30
                      liveness:
                        enabled: true
                        timeoutSeconds: 5
                        initialDelaySeconds: 10
                        periodSeconds: 30
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
                      scaleUpMaxLimit: 30
                      scaleDownBy: 1
                      stabilizationWindowMs: 300000
                    cleanUpPvcs: true
                    setsUpdateStrategy: RollingUpdate
                    autoRackConfig:
                      enabled: true
                      periodMs: 60000
                status:
                  conditions: []
                """.formatted(GLOBAL_SPEC_YAML_PART));
    }

    private void assertZkYaml(String yaml) {
        Assert.assertEquals(yaml, """
                ---
                apiVersion: kaap.oss.datastax.com/v1beta1
                kind: ZooKeeper
                metadata:
                  name: pulsar-spec-1-zookeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
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
                    pdb:
                      enabled: true
                      maxUnavailable: 1
                    probes:
                      readiness:
                        enabled: true
                        timeoutSeconds: 30
                        initialDelaySeconds: 20
                        periodSeconds: 30
                      liveness:
                        enabled: true
                        timeoutSeconds: 30
                        initialDelaySeconds: 20
                        periodSeconds: 30
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
        controller.operatorRuntimeConfiguration = new ControllerTestUtil.TestOperatorRuntimeConfiguration();

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

    @Test
    public void testAuthTokenProvisioner() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:2.10.2
                    auth:
                        enabled: true
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        UpdateControl<PulsarCluster> control = invokeController(client, spec, r -> null);
        Assert.assertEquals(client.countCreatedResources(), 1);
        KubeTestUtil.assertUpdateControlInitializing(control);
        verify(tokenAuthProvisioner).generateSecretsIfAbsent(any());
    }

    @Test
    public void testResourceSets() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:2.10.2
                    auth:
                        enabled: true
                    resourceSets:
                      set1: {}
                bookkeeper:
                    sets:
                        set1: {}
                broker:
                    sets:
                        set1: {}
                proxy:
                    sets: 
                        set1: {}
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<PulsarCluster> status = invokeController(client, spec, r -> null);
        final Condition readyCondition = KubeTestUtil.getReadyCondition(status.getResource().getStatus());
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_FALSE);
        Assert.assertEquals(readyCondition.getReason(), CRDConstants.CONDITIONS_TYPE_READY_REASON_INITIALIZING);
    }

    @Test
    public void testBookKeeperResourceSetsNotDefined() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:2.10.2
                    auth:
                        enabled: true
                    resourceSets:
                      set1: {}
                bookkeeper:
                    sets:
                        set1xx: {}
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<PulsarCluster> status = invokeController(client, spec, r -> null);
        final Condition readyCondition = KubeTestUtil.getReadyCondition(status.getResource().getStatus());
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_FALSE);
        Assert.assertEquals(readyCondition.getReason(), CRDConstants.CONDITIONS_TYPE_READY_REASON_INVALID_SPEC);
        Assert.assertTrue(readyCondition.getMessage().contains(
                "bookkeeper resource set set1xx is not defined in global resource sets (.global.resourceSets), only [set1]")
        );
    }

    @Test
    public void testBrokerResourceSetsNotDefined() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:2.10.2
                    auth:
                        enabled: true
                    resourceSets:
                      set1: {}
                broker:
                    sets:
                        set1xx: {}
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<PulsarCluster> status = invokeController(client, spec, r -> null);
        final Condition readyCondition = KubeTestUtil.getReadyCondition(status.getResource().getStatus());
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_FALSE);
        Assert.assertEquals(readyCondition.getReason(), CRDConstants.CONDITIONS_TYPE_READY_REASON_INVALID_SPEC);
        Assert.assertTrue(readyCondition.getMessage().contains(
                "broker resource set set1xx is not defined in global resource sets (.global.resourceSets), only [set1]")
        );
    }

    @Test
    public void testProxyResourceSetsNotDefined() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:2.10.2
                    auth:
                        enabled: true
                    resourceSets:
                      set1: {}
                proxy:
                    sets:
                        set1xx: {}
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<PulsarCluster> status = invokeController(client, spec, r -> null);
        final Condition readyCondition = KubeTestUtil.getReadyCondition(status.getResource().getStatus());
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_FALSE);
        Assert.assertEquals(readyCondition.getReason(), CRDConstants.CONDITIONS_TYPE_READY_REASON_INVALID_SPEC);
        Assert.assertTrue(readyCondition.getMessage().contains(
                "proxy resource set set1xx is not defined in global resource sets (.global.resourceSets), only [set1]")
        );
    }


    @Test
    public void testRacksOk() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:2.10.2
                    auth:
                        enabled: true
                    racks:
                        rack1: {}
                    resourceSets:
                      set1: {}
                      set2:
                        rack: rack1
                      set3:
                        rack: rack1
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<PulsarCluster> status = invokeController(client, spec, r -> null);
        final Condition readyCondition = KubeTestUtil.getReadyCondition(status.getResource().getStatus());
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_FALSE);
        Assert.assertEquals(readyCondition.getReason(), CRDConstants.CONDITIONS_TYPE_READY_REASON_INITIALIZING);
    }


    @Test
    public void testRacks() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:2.10.2
                    auth:
                        enabled: true
                    racks:
                        rack2: {}
                    resourceSets:
                      set1: {}
                      set2:
                        rack: rack1
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<PulsarCluster> status = invokeController(client, spec, r -> null);
        final Condition readyCondition = KubeTestUtil.getReadyCondition(status.getResource().getStatus());
        Assert.assertEquals(readyCondition.getStatus(), CRDConstants.CONDITIONS_STATUS_FALSE);
        Assert.assertEquals(readyCondition.getReason(), CRDConstants.CONDITIONS_TYPE_READY_REASON_INVALID_SPEC);
        Assert.assertTrue(readyCondition.getMessage().contains(
                "Resource set set2 references a rack rack1 that does not exist. You must define racks in .global.racks"),
                readyCondition.getMessage()
        );
    }


    @Test
    public void testAdjustBookKeeperReplicas() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    clusterName: public-ap-southeast-2
                    image: apachepulsar/pulsar:2.10.2
                bookkeeper:
                    replicas: 1
                    autoscaler:
                        enabled: true
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        UpdateControl<PulsarCluster> control = invokeController(client, spec, r -> null);
        final MockKubernetesClient.ResourceInteraction<ZooKeeper> createdZk =
                client.getCreatedResource(ZooKeeper.class);
        assertZkYaml(createdZk.getResourceYaml());
        Assert.assertEquals(client.countCreatedResources(), 1);
        KubeTestUtil.assertUpdateControlInitializing(control);


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
        KubeTestUtil.assertUpdateControlInitializing(control);
        final MockKubernetesClient.ResourceInteraction<BookKeeper> createdBk =
                client.getCreatedResource(BookKeeper.class);
        Assert.assertEquals(createdBk.getResource().getSpec().getBookkeeper().getDefaultBookKeeperSpecRef()
                .getReplicas(), 1);

        // bk ready
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
        KubeTestUtil.assertUpdateControlInitializing(control);


        AtomicInteger bkGetCount = new AtomicInteger();
        // simulate bk autoscaler changed the replicas to 2
        createdBk.getResource().getSpec().getBookkeeper().getDefaultBookKeeperSpecRef().setReplicas(2);
        client = new MockKubernetesClient(NAMESPACE);
        control = invokeController(client, spec, r -> {
            if (r.isAssignableFrom(ZooKeeper.class)) {
                return createdZk.getResource();
            }
            if (r.isAssignableFrom(BookKeeper.class)) {
                bkGetCount.incrementAndGet();
                return createdBk.getResource();
            }
            return null;
        });
        Assert.assertEquals(client.countCreatedResources(), 4);
        // it's important the controller works always with the same version of the bk resource
        Assert.assertEquals(bkGetCount.get(), 1);
        KubeTestUtil.assertUpdateControlInitializing(control);
    }

    @Test
    public void testAdjustBrokerReplicas() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    clusterName: public-ap-southeast-2
                    image: apachepulsar/pulsar:2.10.2
                broker:
                    replicas: 1
                    autoscaler:
                        enabled: true
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        UpdateControl<PulsarCluster> control = invokeController(client, spec, r -> null);
        final MockKubernetesClient.ResourceInteraction<ZooKeeper> createdZk =
                client.getCreatedResource(ZooKeeper.class);
        assertZkYaml(createdZk.getResourceYaml());
        Assert.assertEquals(client.countCreatedResources(), 1);
        KubeTestUtil.assertUpdateControlInitializing(control);


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
        KubeTestUtil.assertUpdateControlInitializing(control);
        final MockKubernetesClient.ResourceInteraction<BookKeeper> createdBk =
                client.getCreatedResource(BookKeeper.class);

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
        KubeTestUtil.assertUpdateControlInitializing(control);
        final MockKubernetesClient brokerClient = client;
        brokerClient.getCreatedResource(Broker.class).getResource().getSpec().getBroker().getDefaultBrokerSpecRef()
                .setReplicas(2);

        AtomicInteger brokerGetCount = new AtomicInteger();
        client = new MockKubernetesClient(NAMESPACE);

        control = invokeController(client, spec, r -> {
            if (r.isAssignableFrom(ZooKeeper.class)) {
                return createdZk.getResource();
            }
            if (r.isAssignableFrom(BookKeeper.class)) {
                return createdBk.getResource();
            }
            if (r.isAssignableFrom(Broker.class)) {
                brokerGetCount.incrementAndGet();
                return brokerClient.getCreatedResource(Broker.class).getResource();
            }
            return null;
        });
        Assert.assertEquals(client.countCreatedResources(), 3);
        // it's important the controller works always with the same version of the bk resource
        Assert.assertEquals(brokerGetCount.get(), 1);
        KubeTestUtil.assertUpdateControlInitializing(control);
    }
}
