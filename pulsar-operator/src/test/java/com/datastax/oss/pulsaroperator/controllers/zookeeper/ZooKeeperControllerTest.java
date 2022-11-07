package com.datastax.oss.pulsaroperator.controllers.zookeeper;

import static org.mockito.Mockito.mock;
import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperFullSpec;
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
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@JBossLog
public class ZooKeeperControllerTest {

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

        final MockKubernetesClient.ResourceInteraction<ConfigMap> configMapInt = client
                .getCreatedResource(ConfigMap.class);
        final String configMap = configMapInt.getResourceYaml();
        log.info(configMap);
        Assert.assertEquals(configMap,
                """
                        ---
                        apiVersion: v1
                        kind: ConfigMap
                        metadata:
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: zookeeper
                          name: pulsarname-zookeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: ZooKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        data:
                          PULSAR_EXTRA_OPTS: -Dzookeeper.tcpKeepAlive=true -Dzookeeper.clientTcpKeepAlive=true -Dpulsar.log.root.level=info
                          PULSAR_GC: -XX:+UseG1GC
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_MEM: -Xms1g -Xmx1g -Dcom.sun.management.jmxremote -Djute.maxbuffer=10485760
                        """);


        final MockKubernetesClient.ResourceInteraction<StatefulSet> statefulSetInt = client
                .getCreatedResource(StatefulSet.class);
        final String statefulSet = statefulSetInt.getResourceYaml();
        Assert.assertEquals(statefulSet,
                """
                        ---
                        apiVersion: apps/v1
                        kind: StatefulSet
                        metadata:
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: zookeeper
                          name: pulsarname-zookeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: ZooKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          podManagementPolicy: Parallel
                          replicas: 3
                          selector:
                            matchLabels:
                              app: pulsarname
                              component: zookeeper
                          serviceName: pulsarname-zookeeper
                          template:
                            metadata:
                              annotations:
                                prometheus.io/port: 8080
                                prometheus.io/scrape: "true"
                              labels:
                                app: pulsarname
                                cluster: pulsarname
                                component: zookeeper
                            spec:
                              containers:
                              - args:
                                - "bin/apply-config-from-env.py conf/zookeeper.conf && bin/generate-zookeeper-config.sh conf/zookeeper.conf && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar zookeeper"
                                command:
                                - sh
                                - -c
                                env:
                                - name: ZOOKEEPER_SERVERS
                                  value: "pulsarname-zookeeper-0,pulsarname-zookeeper-1,pulsarname-zookeeper-2"
                                envFrom:
                                - configMapRef:
                                    name: pulsarname-zookeeper
                                image: apachepulsar/pulsar:2.10.2
                                imagePullPolicy: IfNotPresent
                                livenessProbe:
                                  exec:
                                    command:
                                    - timeout
                                    - 30
                                    - bin/pulsar-zookeeper-ruok.sh
                                  initialDelaySeconds: 20
                                  periodSeconds: 30
                                  timeoutSeconds: 30
                                name: pulsarname-zookeeper
                                ports:
                                - containerPort: 2181
                                  name: client
                                - containerPort: 2888
                                  name: server
                                - containerPort: 3888
                                  name: leader-election
                                readinessProbe:
                                  exec:
                                    command:
                                    - timeout
                                    - 30
                                    - bin/pulsar-zookeeper-ruok.sh
                                  initialDelaySeconds: 20
                                  periodSeconds: 30
                                  timeoutSeconds: 30
                                resources:
                                  requests:
                                    cpu: 0.3
                                    memory: 1Gi
                                volumeMounts:
                                - mountPath: /pulsar/data
                                  name: pulsarname-zookeeper-data
                              securityContext:
                                fsGroup: 0
                              terminationGracePeriodSeconds: 60
                          updateStrategy:
                            type: RollingUpdate
                          volumeClaimTemplates:
                          - apiVersion: v1
                            kind: PersistentVolumeClaim
                            metadata:
                              name: pulsarname-zookeeper-data
                            spec:
                              accessModes:
                              - ReadWriteOnce
                              resources:
                                requests:
                                  storage: 5Gi
                        """);

        final MockKubernetesClient.ResourceInteraction<Service> serviceInt = client.getCreatedResources(Service.class).get(0);
        final String service = serviceInt.getResourceYaml();
        Assert.assertEquals(service,
                """
                        ---
                        apiVersion: v1
                        kind: Service
                        metadata:
                          annotations:
                            service.alpha.kubernetes.io/tolerate-unready-endpoints: "true"
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: zookeeper
                          name: pulsarname-zookeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: ZooKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          clusterIP: None
                          ports:
                          - name: server
                            port: 2888
                          - name: leader-election
                            port: 3888
                          - name: client
                            port: 2181
                          publishNotReadyAddresses: true
                          selector:
                            app: pulsarname
                            component: zookeeper
                        """);

        final MockKubernetesClient.ResourceInteraction<Service> serviceIntCa = client
                .getCreatedResources(Service.class).get(1);
        final String serviceCa = serviceIntCa.getResourceYaml();
        Assert.assertEquals(serviceCa,
                """
                        ---
                        apiVersion: v1
                        kind: Service
                        metadata:
                          annotations: {}
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: zookeeper
                          name: pulsarname-zookeeper-ca
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: ZooKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          ports:
                          - name: server
                            port: 2888
                          - name: leader-election
                            port: 3888
                          - name: client
                            port: 2181
                          selector:
                            app: pulsarname
                            component: zookeeper
                            """);
    }

    @Test
    public void testImage() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    persistence: false
                    image: apachepulsar/pulsar:global
                    imagePullPolicy: IfNotPresent
                zookeeper:
                    dataVolume:
                        name: volume-name
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
                zookeeper:
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
    public void testValidateName() throws Exception {
        String spec = """
                global:
                    image: apachepulsar/pulsar:global
                zookeeper: {}
                """;
        invokeControllerAndAssertError(spec, "invalid configuration property "
                + "\"global.name\" for value \"null\": must not be null");
    }

    @Test
    public void testReplicas() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
                    replicas: 5
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);


        Assert.assertEquals((int) createdResource.getResource().getSpec().getReplicas(), 5);
        Assert.assertEquals(createdResource.getResource().getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv()
                .stream()
                .filter(e -> e.getName().equals("ZOOKEEPER_SERVERS"))
                .findFirst()
                .get()
                .getValue(), "pul-zookeeper-0,pul-zookeeper-1,pul-zookeeper-2,pul-zookeeper-3,pul-zookeeper-4");
    }

    @Test
    public void testConfig() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
                    config:
                        serverCnxnFactory: my.class.MyClass
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_MEM", "-Xms1g -Xmx1g -Dcom.sun.management.jmxremote -Djute.maxbuffer=10485760");
        expectedData.put("PULSAR_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dzookeeper.tcpKeepAlive=true -Dzookeeper.clientTcpKeepAlive=true -Dpulsar.log"
                + ".root.level=info");
        expectedData.put("serverCnxnFactory", "my.class.MyClass");

        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);
    }

    @Test
    public void testUpdateStrategy() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
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
                zookeeper:
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
                zookeeper:
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
                zookeeper:
                    gracePeriod: -1
                """;
        invokeControllerAndAssertError(spec, "invalid configuration property \"zookeeper.gracePeriod\" for value "
                + "\"-1\": must be greater than or equal to 0");

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
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
                zookeeper:
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
                zookeeper:
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
                zookeeper:
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
                zookeeper:
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
                zookeeper:
                    probe:
                        enabled: true
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(StatefulSet.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertProbe(container.getLivenessProbe(), 30, 20, 30);
        assertProbe(container.getReadinessProbe(), 30, 20, 30);


        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
                    probe:
                        enabled: true
                        period: 50
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(StatefulSet.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertProbe(container.getLivenessProbe(), 30, 20, 50);
        assertProbe(container.getReadinessProbe(), 30, 20, 50);

    }

    private void assertProbe(Probe probe, int timeout, int initial, int period) {
        Assert.assertEquals(probe.getExec().getCommand(),
                List.of("timeout", timeout + "", "bin/pulsar-zookeeper-ruok.sh"));

        Assert.assertEquals((int) probe.getInitialDelaySeconds(), initial);
        Assert.assertEquals((int) probe.getTimeoutSeconds(), timeout);
        Assert.assertEquals((int) probe.getPeriodSeconds(), period);
    }


    @Test
    public void testDataVolumeNoPersistence() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
                    dataVolume:
                        name: myvol
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        final PodSpec podSpec = createdResource.getResource().getSpec().getTemplate()
                .getSpec();
        final Container container = podSpec.getContainers().get(0);

        Assert.assertEquals(getVolumeMountByName(container.getVolumeMounts(), "pul-zookeeper-myvol").getMountPath(),
                "/pulsar/data");
        Assert.assertNotNull(getVolumeByName(podSpec.getVolumes(), "pul-zookeeper-myvol").getEmptyDir());
        Assert.assertNull(client.getCreatedResource(StorageClass.class));

    }

    @Test
    public void testDataVolumePersistenceDefaultExistingStorageClass() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                zookeeper:
                    dataVolume:
                        name: myvol
                        size: 1Gi
                        existingStorageClassName: default
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        final PodSpec podSpec = createdResource.getResource().getSpec().getTemplate()
                .getSpec();
        final Container container = podSpec.getContainers().get(0);

        Assert.assertEquals(getVolumeMountByName(container.getVolumeMounts(), "pul-zookeeper-myvol").getMountPath(),
                "/pulsar/data");
        Assert.assertNull(getVolumeByName(podSpec.getVolumes(), "pul-zookeeper-myvol"));

        final PersistentVolumeClaim persistentVolumeClaim = createdResource.getResource().getSpec()
                .getVolumeClaimTemplates().get(0);
        Assert.assertEquals(persistentVolumeClaim.getMetadata().getName(), "pul-zookeeper-myvol");

        Assert.assertEquals(persistentVolumeClaim.getSpec().getAccessModes(), List.of("ReadWriteOnce"));
        Assert.assertEquals(persistentVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                Quantity.parse("1Gi"));
        Assert.assertNull(persistentVolumeClaim.getSpec().getStorageClassName());

        Assert.assertNull(client.getCreatedResource(StorageClass.class));

    }

    @DataProvider(name = "dataVolumePersistenceExistingStorageClass")
    public static Object[][] dataVolumePersistenceExistingStorageClass() {
        return new Object[][] { {"""
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                zookeeper:
                    dataVolume:
                        name: myvol
                        size: 1Gi
                        existingStorageClassName: mystorage-class
                """ },
                {"""
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                    storage:
                        existingStorageClassName: mystorage-class
                zookeeper:
                    dataVolume:
                        name: myvol
                        size: 1Gi
                """ }};
    }

    @Test(dataProvider = "dataVolumePersistenceExistingStorageClass")
    public void testDataVolumePersistenceExistingStorageClass(String spec) throws Exception {
        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        final PodSpec podSpec = createdResource.getResource().getSpec().getTemplate()
                .getSpec();
        final Container container = podSpec.getContainers().get(0);

        Assert.assertEquals(getVolumeMountByName(container.getVolumeMounts(), "pul-zookeeper-myvol").getMountPath(),
                "/pulsar/data");
        Assert.assertNull(getVolumeByName(podSpec.getVolumes(), "pul-zookeeper-myvol"));

        final PersistentVolumeClaim persistentVolumeClaim = createdResource.getResource().getSpec()
                .getVolumeClaimTemplates().get(0);
        Assert.assertEquals(persistentVolumeClaim.getMetadata().getName(), "pul-zookeeper-myvol");

        Assert.assertEquals(persistentVolumeClaim.getSpec().getAccessModes(), List.of("ReadWriteOnce"));
        Assert.assertEquals(persistentVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                Quantity.parse("1Gi"));
        Assert.assertEquals(persistentVolumeClaim.getSpec().getStorageClassName(), "mystorage-class");

        Assert.assertNull(client.getCreatedResource(StorageClass.class));
    }

    @DataProvider(name = "dataVolumePersistenceStorageClass")
    public static Object[][] dataVolumePersistenceStorageClass() {
        return new Object[][] { {"""
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                zookeeper:
                    dataVolume:
                        name: myvol
                        size: 1Gi
                        storageClass:
                            reclaimPolicy: Retain
                            provisioner: kubernetes.io/aws-ebs
                            type: gp2
                            fsType: ext4
                            extraParams:
                                iopsPerGB: "10"
                """ },
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
                zookeeper:
                    dataVolume:
                        name: myvol
                        size: 1Gi
                """ }};
    }

    @Test(dataProvider = "dataVolumePersistenceStorageClass")
    public void testDataVolumePersistenceStorageClass(String spec) throws Exception {
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        final PodSpec podSpec = createdResource.getResource().getSpec().getTemplate()
                .getSpec();
        final Container container = podSpec.getContainers().get(0);

        Assert.assertEquals(getVolumeMountByName(container.getVolumeMounts(), "pul-zookeeper-myvol").getMountPath(),
                "/pulsar/data");
        Assert.assertNull(getVolumeByName(podSpec.getVolumes(), "pul-zookeeper-myvol"));

        final PersistentVolumeClaim persistentVolumeClaim = createdResource.getResource().getSpec()
                .getVolumeClaimTemplates().get(0);
        Assert.assertEquals(persistentVolumeClaim.getMetadata().getName(), "pul-zookeeper-myvol");

        Assert.assertEquals(persistentVolumeClaim.getSpec().getAccessModes(), List.of("ReadWriteOnce"));
        Assert.assertEquals(persistentVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                Quantity.parse("1Gi"));
        Assert.assertEquals(persistentVolumeClaim.getSpec().getStorageClassName(), "pul-zookeeper-myvol");


        final MockKubernetesClient.ResourceInteraction<StorageClass> createdStorageClass =
                client.getCreatedResource(StorageClass.class);

        final StorageClass storageClass = createdStorageClass.getResource();
        Assert.assertEquals(storageClass.getMetadata().getName(), "pul-zookeeper-myvol");
        Assert.assertEquals(storageClass.getMetadata().getNamespace(), NAMESPACE);
        Assert.assertEquals(storageClass.getMetadata().getOwnerReferences().get(0).getKind(), "ZooKeeper");

        Assert.assertEquals(storageClass.getMetadata().getLabels().size(), 3);
        Assert.assertEquals(storageClass.getReclaimPolicy(), "Retain");
        Assert.assertEquals(storageClass.getProvisioner(), "kubernetes.io/aws-ebs");
        Assert.assertEquals(storageClass.getParameters().size(), 3);
        Assert.assertEquals(storageClass.getParameters().get("type"), "gp2");
        Assert.assertEquals(storageClass.getParameters().get("fsType"), "ext4");
        Assert.assertEquals(storageClass.getParameters().get("iopsPerGB"), "10");
    }

    private VolumeMount getVolumeMountByName(Collection<VolumeMount> mounts, String name) {
        log.infof("looking for %s in mounts %s", name, mounts);
        for (VolumeMount mount : mounts) {
            if (mount.getName().equals(name)) {
                return mount;
            }
        }
        return null;
    }

    private Volume getVolumeByName(Collection<Volume> volumes, String name) {
        for (Volume volume : volumes) {
            if (volume.getName().equals(name)) {
                return volume;
            }
        }
        return null;
    }

    @Test
    public void testServices() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
                    service:
                        annotations:
                            myann: myann-value
                        additionalPorts:
                            - name: myport1
                              port: 3333
                """;

        MockKubernetesClient client = invokeController(spec);


        final List<MockKubernetesClient.ResourceInteraction<Service>> services =
                client.getCreatedResources(Service.class);

        Assert.assertEquals(services.size(), 2);
        for (MockKubernetesClient.ResourceInteraction<Service> service : services) {
            final ObjectMeta metadata = service.getResource().getMetadata();
            boolean isCaService = false;
            if (metadata.getName().equals("pul-zookeeper-ca")) {
                isCaService = true;
            } else if (!metadata.getName().equals("pul-zookeeper")) {
                Assert.fail("unexpected service " + metadata.getName());
            }
            Assert.assertEquals(metadata.getNamespace(), NAMESPACE);

            final List<ServicePort> ports = service.getResource().getSpec().getPorts();
            Assert.assertEquals(ports.size(), 4);
            for (ServicePort port : ports) {
                switch (port.getName()) {
                    case "server":
                        Assert.assertEquals((int) port.getPort(), 2888);
                        break;
                    case "leader-election":
                        Assert.assertEquals((int) port.getPort(), 3888);
                        break;
                    case "client":
                        Assert.assertEquals((int) port.getPort(), 2181);
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
            if (isCaService) {
                Assert.assertEquals(annotations.size(), 1);
                Assert.assertEquals(annotations.get("myann"), "myann-value");

                Assert.assertNull(service.getResource().getSpec().getClusterIP());
                Assert.assertNull(service.getResource().getSpec().getPublishNotReadyAddresses());
            } else {
                Assert.assertEquals(annotations.size(), 2);
                Assert.assertEquals(annotations.get("service.alpha.kubernetes.io/tolerate-unready-endpoints"), "true");
                Assert.assertEquals(annotations.get("myann"), "myann-value");

                Assert.assertEquals(service.getResource().getSpec().getClusterIP(), "None");
                Assert.assertEquals((boolean) service.getResource().getSpec().getPublishNotReadyAddresses(), true);

            }
        }
    }

    @SneakyThrows
    private void invokeControllerAndAssertError(String spec, String expectedErrorMessage) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<ZooKeeper> result = invokeController(mockKubernetesClient, spec);
        Assert.assertTrue(result.isUpdateStatus());
        Assert.assertEquals(result.getResource().getStatus().getError(),
                expectedErrorMessage);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<ZooKeeper>
                result = invokeController(mockKubernetesClient, spec);
        Assert.assertTrue(result.isUpdateStatus());
        return mockKubernetesClient;
    }

    private UpdateControl<ZooKeeper> invokeController(MockKubernetesClient mockKubernetesClient, String spec)
            throws Exception {
        final ZooKeeperController controller = new ZooKeeperController(mockKubernetesClient.getClient());

        final ZooKeeper zooKeeper = new ZooKeeper();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(CLUSTER_NAME + "-cr");
        meta.setNamespace(NAMESPACE);
        zooKeeper.setMetadata(meta);

        final ZooKeeperFullSpec zooKeeperFullSpec = MockKubernetesClient.readYaml(spec, ZooKeeperFullSpec.class);
        zooKeeper.setSpec(zooKeeperFullSpec);

        final UpdateControl<ZooKeeper> result = controller.reconcile(zooKeeper, mock(Context.class));
        return result;
    }
}