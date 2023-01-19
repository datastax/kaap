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
package com.datastax.oss.pulsaroperator.controllers.bookkeeper;

import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.controllers.ControllerTestUtil;
import com.datastax.oss.pulsaroperator.controllers.KubeTestUtil;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@JBossLog
public class BookKeeperControllerTest {

    static final String NAMESPACE = "ns";
    static final String CLUSTER_NAME = "pulsarname";

    @Test
    public void testDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:2.10.2
                """;

        final MockKubernetesClient client = invokeController(spec);

        final String configMap = client
                .getCreatedResource(ConfigMap.class).getResourceYaml();
        Assert.assertEquals(configMap,
                """
                        ---
                        apiVersion: v1
                        kind: ConfigMap
                        metadata:
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: bookkeeper
                          name: pulsarname-bookkeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: pulsar.oss.datastax.com/v1alpha1
                            kind: BookKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        data:
                          BOOKIE_GC: -XX:+UseG1GC
                          BOOKIE_MEM: -Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler.linkCapacity=1024 -XX:+ExitOnOutOfMemoryError
                          PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_PREFIX_autoRecoveryDaemonEnabled: "false"
                          PULSAR_PREFIX_httpServerEnabled: "true"
                          PULSAR_PREFIX_reppDnsResolverClass: org.apache.pulsar.zookeeper.ZkBookieRackAffinityMapping
                          PULSAR_PREFIX_statsProviderClass: org.apache.bookkeeper.stats.prometheus.PrometheusMetricsProvider
                          PULSAR_PREFIX_useHostNameAsBookieID: "true"
                          PULSAR_PREFIX_zkServers: pulsarname-zookeeper-ca.ns.svc.cluster.local:2181
                        """);

        final String service = client
                .getCreatedResource(Service.class).getResourceYaml();
        Assert.assertEquals(service, """
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  annotations:
                    service.alpha.kubernetes.io/tolerate-unready-endpoints: "true"
                  labels:
                    app: pulsarname
                    cluster: pulsarname
                    component: bookkeeper
                  name: pulsarname-bookkeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: BookKeeper
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsarname-cr
                spec:
                  clusterIP: None
                  ports:
                  - name: server
                    port: 3181
                  publishNotReadyAddresses: true
                  selector:
                    app: pulsarname
                    component: bookkeeper
                """);

        final String sts = client
                .getCreatedResource(StatefulSet.class).getResourceYaml();
        Assert.assertEquals(sts, """
                ---
                apiVersion: apps/v1
                kind: StatefulSet
                metadata:
                  labels:
                    app: pulsarname
                    cluster: pulsarname
                    component: bookkeeper
                  name: pulsarname-bookkeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: BookKeeper
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsarname-cr
                spec:
                  podManagementPolicy: Parallel
                  replicas: 3
                  selector:
                    matchLabels:
                      app: pulsarname
                      component: bookkeeper
                  serviceName: pulsarname-bookkeeper
                  template:
                    metadata:
                      annotations:
                        prometheus.io/port: 8080
                        prometheus.io/scrape: "true"
                      labels:
                        app: pulsarname
                        cluster: pulsarname
                        component: bookkeeper
                    spec:
                      containers:
                      - args:
                        - "bin/apply-config-from-env.py conf/bookkeeper.conf && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar bookie"
                        command:
                        - sh
                        - -c
                        envFrom:
                        - configMapRef:
                            name: pulsarname-bookkeeper
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        livenessProbe:
                          httpGet:
                            path: /api/v1/bookie/is_ready
                            port: 8000
                          initialDelaySeconds: 10
                          periodSeconds: 30
                          timeoutSeconds: 5
                        name: pulsarname-bookkeeper
                        ports:
                        - containerPort: 3181
                          name: client
                        readinessProbe:
                          httpGet:
                            path: /api/v1/bookie/is_ready
                            port: 8000
                          initialDelaySeconds: 10
                          periodSeconds: 30
                          timeoutSeconds: 5
                        resources:
                          requests:
                            cpu: 1
                            memory: 2Gi
                        volumeMounts:
                        - mountPath: /pulsar/data/bookkeeper/journal
                          name: pulsarname-bookkeeper-journal
                        - mountPath: /pulsar/data/bookkeeper/ledgers
                          name: pulsarname-bookkeeper-ledgers
                      dnsConfig:
                        options:
                        - name: ndots
                          value: 4
                      initContainers:
                      - args:
                        - |
                          bin/apply-config-from-env.py conf/bookkeeper.conf && bin/bookkeeper shell metaformat --nonInteractive || true;
                        command:
                        - sh
                        - -c
                        envFrom:
                        - configMapRef:
                            name: pulsarname-bookkeeper
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        name: pulsar-bookkeeper-metaformat
                      securityContext:
                        fsGroup: 0
                      terminationGracePeriodSeconds: 60
                  updateStrategy:
                    type: RollingUpdate
                  volumeClaimTemplates:
                  - apiVersion: v1
                    kind: PersistentVolumeClaim
                    metadata:
                      name: pulsarname-bookkeeper-journal
                    spec:
                      accessModes:
                      - ReadWriteOnce
                      resources:
                        requests:
                          storage: 20Gi
                  - apiVersion: v1
                    kind: PersistentVolumeClaim
                    metadata:
                      name: pulsarname-bookkeeper-ledgers
                    spec:
                      accessModes:
                      - ReadWriteOnce
                      resources:
                        requests:
                          storage: 50Gi
                """);

        final MockKubernetesClient.ResourceInteraction<PodDisruptionBudget> pdbInt = client
                .getCreatedResource(PodDisruptionBudget.class);
        final String pdb = pdbInt.getResourceYaml();
        Assert.assertEquals(pdb,
                """
                        ---
                        apiVersion: policy/v1
                        kind: PodDisruptionBudget
                        metadata:
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: bookkeeper
                          name: pulsarname-bookkeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: pulsar.oss.datastax.com/v1alpha1
                            kind: BookKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          maxUnavailable: 1
                          selector:
                            matchLabels:
                              app: pulsarname
                              component: bookkeeper
                            """);

    }

    @Test
    public void testConfig() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    config:
                        PULSAR_LOG_LEVEL: debug
                        customConfig: customValue
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_autoRecoveryDaemonEnabled", "false");
        // In k8s always want to use hostname as bookie ID since IP addresses are ephemeral
        expectedData.put("PULSAR_PREFIX_useHostNameAsBookieID", "true");
        // HTTP server used by health check
        expectedData.put("PULSAR_PREFIX_httpServerEnabled", "true");
        //Pulsar's metadata store based rack awareness solution
        expectedData.put("PULSAR_PREFIX_reppDnsResolverClass",
                "org.apache.pulsar.zookeeper.ZkBookieRackAffinityMapping");

        expectedData.put("BOOKIE_MEM",
                "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled "
                        + "-Dio.netty.recycler.linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("BOOKIE_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "debug");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_statsProviderClass",
                "org.apache.bookkeeper.stats.prometheus.PrometheusMetricsProvider");
        expectedData.put("PULSAR_PREFIX_zkServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_customConfig", "customValue");


        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);
    }


    @Test
    public void testReplicas() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    replicas: 5
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        Assert.assertEquals((int) createdResource.getResource().getSpec().getReplicas(), 5);
    }

    @Test
    public void testImage() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    persistence: false
                    image: apachepulsar/pulsar:global
                    imagePullPolicy: IfNotPresent
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);


        Assert.assertEquals(createdResource.getResource().getSpec()
                .getTemplate()
                .getSpec().getContainers().get(0)
                .getImage(), "apachepulsar/pulsar:global");
        Assert.assertEquals(createdResource.getResource().getSpec()
                .getTemplate()
                .getSpec().getContainers().get(0)
                .getImagePullPolicy(), "IfNotPresent");

        spec = """
                global:
                    name: pulsarname
                    persistence: false
                    image: apachepulsar/pulsar:global
                    imagePullPolicy: IfNotPresent
                bookkeeper:
                    image: apachepulsar/pulsar:zk
                    imagePullPolicy: Always
                """;
        client = invokeController(spec);
        createdResource =
                client.getCreatedResource(StatefulSet.class);


        Assert.assertEquals(createdResource.getResource().getSpec()
                .getTemplate()
                .getSpec().getContainers().get(0)
                .getImage(), "apachepulsar/pulsar:zk");
        Assert.assertEquals(createdResource.getResource().getSpec()
                .getTemplate()
                .getSpec().getContainers().get(0)
                .getImagePullPolicy(), "Always");

    }


    @Test
    public void testUpdateStrategy() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    updateStrategy:
                        type: RollingUpdate
                        rollingUpdate:
                            partition: 3
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        Assert.assertEquals(
                createdResource.getResource().getSpec().getUpdateStrategy()
                        .getType(),
                "RollingUpdate"
        );
        Assert.assertEquals(
                (int) createdResource.getResource().getSpec().getUpdateStrategy()
                        .getRollingUpdate().getPartition(),
                3
        );
    }

    @Test
    public void testPodManagementPolicy() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    podManagementPolicy: OrderedReady
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        Assert.assertEquals(
                createdResource.getResource().getSpec().getPodManagementPolicy(),
                "OrderedReady"
        );
    }

    @Test
    public void testAnnotations() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    annotations:
                        annotation-1: ann1-value
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        final Map<String, String> annotations =
                createdResource.getResource().getSpec().getTemplate().getMetadata().getAnnotations();

        Assert.assertEquals(annotations.size(), 3);
        Assert.assertEquals(annotations.get("prometheus.io/scrape"), "true");
        Assert.assertEquals(annotations.get("prometheus.io/port"), "8080");
        Assert.assertEquals(annotations.get("annotation-1"), "ann1-value");
    }

    @Test
    public void testGracePeriod() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    gracePeriod: -1
                """;
        invokeControllerAndAssertError(spec, "invalid configuration property \"bookkeeper.gracePeriod\" for value "
                + "\"-1\": must be greater than or equal to 0");

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    gracePeriod: 0
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        Assert.assertEquals((long) createdResource.getResource().getSpec().getTemplate()
                .getSpec().getTerminationGracePeriodSeconds(), 0L);

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    gracePeriod: 120
                """;

        client = invokeController(spec);

        createdResource =
                client.getCreatedResource(StatefulSet.class);

        Assert.assertEquals((long) createdResource.getResource().getSpec().getTemplate()
                .getSpec().getTerminationGracePeriodSeconds(), 120L);
    }


    @Test
    public void testResources() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    resources:
                        requests:
                          memory: 1.5Gi
                          cpu: 0.5
                        limits:
                          memory: 2Gi
                          cpu: 1
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);
        final ResourceRequirements resources = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0).getResources();

        Assert.assertEquals(resources.getRequests().get("memory"), Quantity.parse("1.5Gi"));
        Assert.assertEquals(resources.getRequests().get("cpu"), Quantity.parse("0.5"));

        Assert.assertEquals(resources.getLimits().get("memory"), Quantity.parse("2Gi"));
        Assert.assertEquals(resources.getLimits().get("cpu"), Quantity.parse("1"));

    }

    @Test
    public void testNodeSelectors() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    nodeSelectors:
                        globallabel: global
                        overridelabel: to-be-overridden
                bookkeeper:
                    nodeSelectors:
                        overridelabel: overridden
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);
        final Map<String, String> nodeSelectors = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getNodeSelector();
        Assert.assertEquals(nodeSelectors.size(), 2);
        Assert.assertEquals(nodeSelectors.get("globallabel"), "global");
        Assert.assertEquals(nodeSelectors.get("overridelabel"), "overridden");
    }


    @Test
    public void testVolumesNoPersistence() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);
        final PodSpec podSpec = createdResource.getResource().getSpec().getTemplate()
                .getSpec();

        Assert.assertEquals(podSpec.getVolumes().size(), 2);
        Assert.assertEquals(podSpec.getVolumes().get(0).getName(), "pul-bookkeeper-journal");
        Assert.assertNotNull(podSpec.getVolumes().get(0).getEmptyDir());
        Assert.assertEquals(podSpec.getVolumes().get(1).getName(), "pul-bookkeeper-ledgers");
        Assert.assertNotNull(podSpec.getVolumes().get(1).getEmptyDir());

        final List<VolumeMount> mounts = podSpec.getContainers().get(0).getVolumeMounts();
        Assert.assertEquals(mounts.size(), 2);
        Assert.assertEquals(mounts.get(0).getName(), "pul-bookkeeper-journal");
        Assert.assertEquals(mounts.get(0).getMountPath(), "/pulsar/data/bookkeeper/journal");
        Assert.assertEquals(mounts.get(1).getName(), "pul-bookkeeper-ledgers");
        Assert.assertEquals(mounts.get(1).getMountPath(), "/pulsar/data/bookkeeper/ledgers");
        Assert.assertNull(client.getCreatedResource(StorageClass.class));
    }


    @Test
    public void testDataVolumePersistenceDefaultExistingStorageClass() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    volumes:
                        journal:
                            name: jname
                            size: 1Gi
                            existingStorageClassName: default
                        ledgers:
                            name: lname
                            size: 2Gi
                            existingStorageClassName: default
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        final PodSpec podSpec = createdResource.getResource().getSpec().getTemplate()
                .getSpec();
        final Container container = podSpec.getContainers().get(0);

        Assert.assertEquals(
                KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-bookkeeper-jname").getMountPath(),
                "/pulsar/data/bookkeeper/journal");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-bookkeeper-jname"));

        Assert.assertEquals(
                KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-bookkeeper-lname").getMountPath(),
                "/pulsar/data/bookkeeper/ledgers");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-bookkeeper-lname"));


        for (final PersistentVolumeClaim persistentVolumeClaim : createdResource.getResource().getSpec()
                .getVolumeClaimTemplates()) {
            Assert.assertEquals(persistentVolumeClaim.getSpec().getAccessModes(), List.of("ReadWriteOnce"));
            Assert.assertNull(persistentVolumeClaim.getSpec().getStorageClassName());
            switch (persistentVolumeClaim.getMetadata().getName()) {
                case "pul-bookkeeper-jname":
                    Assert.assertEquals(persistentVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                            Quantity.parse("1Gi"));
                    break;
                case "pul-bookkeeper-lname":
                    Assert.assertEquals(persistentVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                            Quantity.parse("2Gi"));
                    break;
            }
        }
        Assert.assertNull(client.getCreatedResource(StorageClass.class));

    }

    @DataProvider(name = "dataVolumePersistenceExistingStorageClass")
    public static Object[][] dataVolumePersistenceExistingStorageClass() {
        return new Object[][]{{"""
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    volumes:
                        journal:
                            existingStorageClassName: mystorage-class
                        ledgers:
                            existingStorageClassName: mystorage-class
                """},
                {"""
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                    storage:
                        existingStorageClassName: mystorage-class
                """}};
    }

    @Test(dataProvider = "dataVolumePersistenceExistingStorageClass")
    public void testDataVolumePersistenceExistingStorageClass(String spec) throws Exception {
        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        final PodSpec podSpec = createdResource.getResource().getSpec().getTemplate()
                .getSpec();
        final Container container = podSpec.getContainers().get(0);

        Assert.assertEquals(
                KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-bookkeeper-journal").getMountPath(),
                "/pulsar/data/bookkeeper/journal");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-bookkeeper-journal"));

        Assert.assertEquals(
                KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-bookkeeper-ledgers").getMountPath(),
                "/pulsar/data/bookkeeper/ledgers");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-bookkeeper-ledgers"));


        for (final PersistentVolumeClaim persistentVolumeClaim : createdResource.getResource().getSpec()
                .getVolumeClaimTemplates()) {
            Assert.assertEquals(persistentVolumeClaim.getSpec().getAccessModes(), List.of("ReadWriteOnce"));
            Assert.assertEquals(persistentVolumeClaim.getSpec().getStorageClassName(), "mystorage-class");
            switch (persistentVolumeClaim.getMetadata().getName()) {
                case "pul-bookkeeper-journal":
                    Assert.assertEquals(persistentVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                            Quantity.parse("20Gi"));
                    break;
                case "pul-bookkeeper-ledgers":
                    Assert.assertEquals(persistentVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                            Quantity.parse("50Gi"));
                    break;
            }
        }
        Assert.assertNull(client.getCreatedResource(StorageClass.class));

    }

    @DataProvider(name = "dataVolumePersistenceStorageClass")
    public static Object[][] dataVolumePersistenceStorageClass() {
        return new Object[][]{{"""
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    volumes:
                        journal:
                            storageClass:
                                reclaimPolicy: Retain
                                provisioner: kubernetes.io/aws-ebs
                                type: gp2
                                fsType: ext4
                                extraParams:
                                    iopsPerGB: "10"
                        ledgers:
                            storageClass:
                                reclaimPolicy: Retain
                                provisioner: kubernetes.io/aws-ebs
                                type: gp2
                                fsType: ext4
                                extraParams:
                                    iopsPerGB: "10"
                """},
                {"""
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                    storage:
                        storageClass:
                            reclaimPolicy: Retain
                            provisioner: kubernetes.io/aws-ebs
                            type: gp2
                            fsType: ext4
                            extraParams:
                                iopsPerGB: "10"
                """}};
    }

    @Test(dataProvider = "dataVolumePersistenceStorageClass")
    public void testDataVolumePersistenceStorageClass(String spec) throws Exception {
        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        final PodSpec podSpec = createdResource.getResource().getSpec().getTemplate()
                .getSpec();
        final Container container = podSpec.getContainers().get(0);

        Assert.assertEquals(
                KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-bookkeeper-journal").getMountPath(),
                "/pulsar/data/bookkeeper/journal");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-bookkeeper-journal"));

        Assert.assertEquals(
                KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-bookkeeper-ledgers").getMountPath(),
                "/pulsar/data/bookkeeper/ledgers");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-bookkeeper-ledgers"));


        for (final PersistentVolumeClaim persistentVolumeClaim : createdResource.getResource().getSpec()
                .getVolumeClaimTemplates()) {
            Assert.assertEquals(persistentVolumeClaim.getSpec().getAccessModes(), List.of("ReadWriteOnce"));
            switch (persistentVolumeClaim.getMetadata().getName()) {
                case "pul-bookkeeper-journal":
                    Assert.assertEquals(persistentVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                            Quantity.parse("20Gi"));
                    break;
                case "pul-bookkeeper-ledgers":
                    Assert.assertEquals(persistentVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                            Quantity.parse("50Gi"));
                    break;
            }
        }
        final List<MockKubernetesClient.ResourceInteraction<StorageClass>> storageClasses =
                client.getCreatedResources(StorageClass.class);
        Assert.assertEquals(storageClasses.size(), 2);
        for (MockKubernetesClient.ResourceInteraction<StorageClass> createdStorageClass : storageClasses) {

            final StorageClass storageClass = createdStorageClass.getResource();
            if (!storageClass.getMetadata().getName().equals("pul-bookkeeper-journal")
                    && !storageClass.getMetadata().getName().equals("pul-bookkeeper-ledgers")) {
                Assert.fail("unexpected storageClass " + storageClass.getMetadata().getName());
            }
            Assert.assertEquals(storageClass.getMetadata().getNamespace(), NAMESPACE);
            Assert.assertEquals(storageClass.getMetadata().getOwnerReferences().size(), 0);

            Assert.assertEquals(storageClass.getMetadata().getLabels().size(), 3);
            Assert.assertEquals(storageClass.getReclaimPolicy(), "Retain");
            Assert.assertEquals(storageClass.getProvisioner(), "kubernetes.io/aws-ebs");
            Assert.assertEquals(storageClass.getParameters().size(), 3);
            Assert.assertEquals(storageClass.getParameters().get("type"), "gp2");
            Assert.assertEquals(storageClass.getParameters().get("fsType"), "ext4");
            Assert.assertEquals(storageClass.getParameters().get("iopsPerGB"), "10");
        }
    }


    @Test
    public void testDNSConfig() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    dnsConfig:
                        nameservers:
                          - 1.2.3.4
                        searches:
                          - ns1.svc.cluster-domain.example
                          - my.dns.search.suffix
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);
        final PodDNSConfig dnsConfig = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getDnsConfig();
        Assert.assertEquals(dnsConfig.getNameservers(), List.of("1.2.3.4"));
        Assert.assertEquals(dnsConfig.getSearches(), List.of("ns1.svc.cluster-domain.example", "my.dns.search.suffix"));
    }


    @Test
    public void testProbe() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    probe:
                        enabled: false
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);


        Container container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        Assert.assertNull(container.getLivenessProbe());
        Assert.assertNull(container.getReadinessProbe());

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    probe:
                        enabled: true
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(StatefulSet.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertProbe(container.getLivenessProbe(), 5, 10, 30);
        assertProbe(container.getReadinessProbe(), 5, 10, 30);


        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    probe:
                        enabled: true
                        period: 50
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(StatefulSet.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertProbe(container.getLivenessProbe(), 5, 10, 50);
        assertProbe(container.getReadinessProbe(), 5, 10, 50);

    }


    @Test
    public void testService() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    service:
                        annotations:
                            myann: myann-value
                        additionalPorts:
                            - name: myport1
                              port: 3333
                """;

        MockKubernetesClient client = invokeController(spec);


        final MockKubernetesClient.ResourceInteraction<Service> service =
                client.getCreatedResource(Service.class);

        final ObjectMeta metadata = service.getResource().getMetadata();
        Assert.assertEquals(metadata.getName(), "pul-bookkeeper");
        Assert.assertEquals(metadata.getNamespace(), NAMESPACE);
        final List<ServicePort> ports = service.getResource().getSpec().getPorts();
        Assert.assertEquals(ports.size(), 2);
        for (ServicePort port : ports) {
            switch (port.getName()) {
                case "server":
                    Assert.assertEquals((int) port.getPort(), 3181);
                    break;
                case "myport1":
                    Assert.assertEquals((int) port.getPort(), 3333);
                    break;
                default:
                    Assert.fail("unexpected port " + port.getName());
                    break;
            }
        }
        final Map<String, String> annotations = service.getResource().getMetadata().getAnnotations();
        Assert.assertEquals(annotations.size(), 2);
        Assert.assertEquals(annotations.get("service.alpha.kubernetes.io/tolerate-unready-endpoints"), "true");
        Assert.assertEquals(annotations.get("myann"), "myann-value");

        Assert.assertEquals(service.getResource().getSpec().getClusterIP(), "None");
        Assert.assertEquals((boolean) service.getResource().getSpec().getPublishNotReadyAddresses(), true);
    }

    @Test
    public void testPdbMaxUnavailable() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    pdb:
                        maxUnavailable: 3
                """;

        MockKubernetesClient client = invokeController(spec);

        final MockKubernetesClient.ResourceInteraction<PodDisruptionBudget> pdb =
                client.getCreatedResource(PodDisruptionBudget.class);

        Assert.assertEquals((int) pdb.getResource().getSpec().getMaxUnavailable().getIntVal(), 3);
    }


    @Test
    public void testRestartOnConfigMapChange() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    restartOnConfigMapChange: true
                """;

        MockKubernetesClient client = invokeController(spec);


        StatefulSet sts = client.getCreatedResource(StatefulSet.class).getResource();
        System.out.println(sts.getSpec().getTemplate()
                .getMetadata().getAnnotations());
        final String checksum1 = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("pulsar.oss.datastax.com/configmap-pul-bookkeeper");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        Assert.assertEquals(sts.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("pulsar.oss.datastax.com/configmap-pul-bookkeeper"),
                checksum1);

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    restartOnConfigMapChange: true
                bookkeeper:
                    config:
                        PULSAR_ROOT_LOG_LEVEL: debug
                """;

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        final String checksum2 = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("pulsar.oss.datastax.com/configmap-pul-bookkeeper");
        Assert.assertNotNull(checksum2);
        Assert.assertNotEquals(checksum1, checksum2);
    }

    @Test
    public void testAdditionalVolumes() throws Exception {

        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    additionalVolumes:
                        volumes:
                            - name: vol1
                              secret:
                                secretName: mysecret
                        mounts:
                            - name: vol1
                              mountPath: /pulsar/custom
                              readOnly: true
                """;

        MockKubernetesClient client = invokeController(spec);

        final StatefulSet sts = client.getCreatedResource(StatefulSet.class).getResource();

        final PodSpec podSpec = sts.getSpec().getTemplate().getSpec();
        KubeTestUtil.assertVolumeFromSecret(podSpec.getVolumes(), "vol1", "mysecret");
        KubeTestUtil.assertVolumeMount(podSpec.getContainers().get(0)
                .getVolumeMounts(), "vol1", "/pulsar/custom", true);
    }


    private void assertProbe(Probe probe, int timeout, int initial, int period) {
        Assert.assertEquals(probe.getHttpGet().getPort().getIntVal().intValue(), 8000);
        Assert.assertEquals(probe.getHttpGet().getPath(), "/api/v1/bookie/is_ready");

        Assert.assertEquals((int) probe.getInitialDelaySeconds(), initial);
        Assert.assertEquals((int) probe.getTimeoutSeconds(), timeout);
        Assert.assertEquals((int) probe.getPeriodSeconds(), period);
    }


    @SneakyThrows
    private void invokeControllerAndAssertError(String spec, String expectedErrorMessage) {
        new ControllerTestUtil<BookKeeperFullSpec, BookKeeper>(NAMESPACE, CLUSTER_NAME)
                .invokeControllerAndAssertError(spec,
                        expectedErrorMessage,
                        BookKeeper.class,
                        BookKeeperFullSpec.class,
                        BookKeeperController.class);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return new ControllerTestUtil<BookKeeperFullSpec, BookKeeper>(NAMESPACE, CLUSTER_NAME)
                .invokeController(spec,
                        BookKeeper.class,
                        BookKeeperFullSpec.class,
                        BookKeeperController.class);
    }
}