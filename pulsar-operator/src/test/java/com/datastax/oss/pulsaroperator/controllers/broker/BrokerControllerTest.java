package com.datastax.oss.pulsaroperator.controllers.broker;

import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.controllers.ControllerTestUtil;
import com.datastax.oss.pulsaroperator.controllers.KubeTestUtil;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerFullSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.Test;

@JBossLog
public class BrokerControllerTest {

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
                            component: broker
                          name: pulsarname-broker
                          namespace: ns
                          ownerReferences:
                          - apiVersion: pulsar.oss.datastax.com/v1alpha1
                            kind: Broker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        data:
                          PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                          PULSAR_GC: -XX:+UseG1GC
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_MEM: -Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler.linkCapacity=1024 -XX:+ExitOnOutOfMemoryError
                          allowAutoTopicCreationType: non-partitioned
                          backlogQuotaDefaultRetentionPolicy: producer_exception
                          brokerDeduplicationEnabled: "false"
                          clusterName: pulsarname
                          configurationStoreServers: pulsarname-zookeeper-ca.ns.svc.cluster.local:2181
                          exposeConsumerLevelMetricsInPrometheus: "false"
                          exposeTopicLevelMetricsInPrometheus: "true"
                          zookeeperServers: pulsarname-zookeeper-ca.ns.svc.cluster.local:2181
                        """);

        final String service = client
                .getCreatedResource(Service.class).getResourceYaml();
        Assert.assertEquals(service, """
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  labels:
                    app: pulsarname
                    cluster: pulsarname
                    component: broker
                  name: pulsarname-broker
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: Broker
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsarname-cr
                spec:
                  clusterIP: None
                  ports:
                  - name: http
                    port: 8080
                  - name: pulsar
                    port: 6650
                  selector:
                    app: pulsarname
                    component: broker
                  type: ClusterIP
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
                    component: broker
                  name: pulsarname-broker
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: Broker
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsarname-cr
                spec:
                  replicas: 3
                  selector:
                    matchLabels:
                      app: pulsarname
                      component: broker
                  serviceName: pulsarname-broker
                  template:
                    metadata:
                      annotations:
                        prometheus.io/port: 8080
                        prometheus.io/scrape: "true"
                      labels:
                        app: pulsarname
                        cluster: pulsarname
                        component: broker
                    spec:
                      containers:
                      - args:
                        - "bin/apply-config-from-env.py conf/broker.conf && bin/apply-config-from-env.py conf/client.conf && bin/gen-yml-from-env.py conf/functions_worker.yml && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar broker"
                        command:
                        - sh
                        - -c
                        envFrom:
                        - configMapRef:
                            name: pulsarname-broker
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        livenessProbe:
                          exec:
                            command:
                            - sh
                            - -c
                            - curl -s --max-time 5 --fail  http://localhost:8080/admin/v2/brokers/health > /dev/null
                          initialDelaySeconds: 10
                          periodSeconds: 30
                          timeoutSeconds: 5
                        name: pulsarname-broker
                        ports:
                        - containerPort: 8080
                          name: http
                        - containerPort: 6650
                          name: pulsar
                        readinessProbe:
                          exec:
                            command:
                            - sh
                            - -c
                            - curl -s --max-time 5 --fail  http://localhost:8080/admin/v2/brokers/health > /dev/null
                          initialDelaySeconds: 10
                          periodSeconds: 30
                          timeoutSeconds: 5
                        resources:
                          requests:
                            cpu: 1
                            memory: 2Gi
                      terminationGracePeriodSeconds: 60
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
                            component: broker
                          name: pulsarname-broker
                          namespace: ns
                          ownerReferences:
                          - apiVersion: pulsar.oss.datastax.com/v1alpha1
                            kind: Broker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          maxUnavailable: 1
                          selector:
                            matchLabels:
                              app: pulsarname
                              component: broker
                            """);

    }

    @Test
    public void testEnableFnWorker() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                broker:
                    functionsWorkerEnabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("clusterName", "pul");
        expectedData.put("allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("brokerDeduplicationEnabled", "false");
        expectedData.put("exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("functionsWorkerEnabled", "true");
        expectedData.put("PF_pulsarFunctionsCluster", "pul");
        expectedData.put("PF_pulsarServiceUrl", "pulsar://localhost:6650");
        expectedData.put("PF_pulsarWebServiceUrl", "http://localhost:8080");


        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);
    }


    @Test
    public void testTransactions() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                broker:
                    transactions:
                        enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("clusterName", "pul");
        expectedData.put("allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("brokerDeduplicationEnabled", "false");
        expectedData.put("exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("PULSAR_PREFIX_transactionCoordinatorEnabled", "true");

        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);
    }


    @Test
    public void testConfig() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                broker:
                    config:
                        PULSAR_LOG_LEVEL: debug
                        customConfig: customValue
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("clusterName", "pul");
        expectedData.put("allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "debug");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("brokerDeduplicationEnabled", "false");
        expectedData.put("exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("customConfig", "customValue");


        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);
    }

    @Test
    public void testAuthToken() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    auth:
                        enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("clusterName", "pul");
        expectedData.put("allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("brokerDeduplicationEnabled", "false");
        expectedData.put("exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("authParams", "file:///pulsar/token-superuser-stripped.jwt");
        expectedData.put("authPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
        expectedData.put("authorizationEnabled", "true");
        expectedData.put("authenticationEnabled", "true");
        expectedData.put("authenticationProviders", "org.apache.pulsar.broker.authentication.AuthenticationProviderToken");
        expectedData.put("proxyRoles", "proxy");
        expectedData.put("superUserRoles", "admin,proxy,superuser,websocket");
        expectedData.put("tokenPublicKey", "file:///pulsar/token-public-key/my-public.key");
        expectedData.put("brokerClientAuthenticationPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
        expectedData.put("brokerClientAuthenticationParameters", "file:///pulsar/token-superuser/superuser.jwt");

        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);

        final StatefulSet sts = client.getCreatedResource(StatefulSet.class).getResource();

        final List<Volume> volumes = sts.getSpec().getTemplate().getSpec().getVolumes();
        final List<VolumeMount> volumeMounts = sts.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getVolumeMounts();
        KubeTestUtil.assertRolesMounted(volumes, volumeMounts,
                "public-key", "superuser");
        final String cmdArg = sts.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs()
                .stream().collect(Collectors.joining(" "));
        Assert.assertTrue(cmdArg.contains("cat /pulsar/token-superuser/superuser.jwt | tr -d '\\n' > /pulsar/token-superuser-stripped"
                + ".jwt && "));

        Assert.assertEquals(sts.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getLivenessProbe().getExec().getCommand()
                .get(2), "curl -s --max-time 5 --fail -H \"Authorization: Bearer $(cat "
                + "/pulsar/token-superuser/superuser.jwt | tr -d '\\r')\" "
                + "http://localhost:8080/admin/v2/brokers/health > /dev/null");
    }


    @Test
    public void testReplicas() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                broker:
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
                broker:
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
                broker:
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
                broker:
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
                broker:
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
                broker:
                    gracePeriod: -1
                """;
        invokeControllerAndAssertError(spec, "invalid configuration property \"broker.gracePeriod\" for value "
                + "\"-1\": must be greater than or equal to 0");

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                broker:
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
                broker:
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
                broker:
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
                broker:
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
                broker:
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
                broker:
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
                broker:
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
                broker:
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
        Assert.assertEquals(metadata.getName(), "pul-broker");
        Assert.assertEquals(metadata.getNamespace(), NAMESPACE);
        final List<ServicePort> ports = service.getResource().getSpec().getPorts();
        Assert.assertEquals(ports.size(), 3);
        for (ServicePort port : ports) {
            switch (port.getName()) {
                case "http":
                    Assert.assertEquals((int) port.getPort(), 8080);
                    break;
                case "pulsar":
                    Assert.assertEquals((int) port.getPort(), 6650);
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
        Assert.assertEquals(annotations.size(), 1);
        Assert.assertEquals(annotations.get("myann"), "myann-value");

        Assert.assertEquals(service.getResource().getSpec().getClusterIP(), "None");
        Assert.assertEquals(service.getResource().getSpec().getType(), "ClusterIP");
    }

    @Test
    public void testPdbMaxUnavailable() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                broker:
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
                .getMetadata().getAnnotations().get("pulsar.oss.datastax.com/configmap-pul-broker");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        Assert.assertEquals(sts.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("pulsar.oss.datastax.com/configmap-pul-broker"),
                checksum1);

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    restartOnConfigMapChange: true
                broker:
                    config:
                        PULSAR_ROOT_LOG_LEVEL: debug
                """;

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        final String checksum2 = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("pulsar.oss.datastax.com/configmap-pul-broker");
        Assert.assertNotNull(checksum2);
        Assert.assertNotEquals(checksum1, checksum2);
    }

    private void assertProbe(Probe probe, int timeout, int initial, int period) {
        Assert.assertEquals(probe.getExec().getCommand(), List.of("sh", "-c",
                "curl -s --max-time %d --fail  http://localhost:8080/admin/v2/brokers/health > /dev/null".formatted(timeout)));

        Assert.assertEquals((int) probe.getInitialDelaySeconds(), initial);
        Assert.assertEquals((int) probe.getTimeoutSeconds(), timeout);
        Assert.assertEquals((int) probe.getPeriodSeconds(), period);
    }


    @SneakyThrows
    private void invokeControllerAndAssertError(String spec, String expectedErrorMessage) {
        new ControllerTestUtil<BrokerFullSpec, Broker>(NAMESPACE, CLUSTER_NAME)
                .invokeControllerAndAssertError(spec,
                        expectedErrorMessage,
                        Broker.class,
                        BrokerFullSpec.class,
                        BrokerController.class);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return new ControllerTestUtil<BrokerFullSpec, Broker>(NAMESPACE, CLUSTER_NAME)
                .invokeController(spec,
                        Broker.class,
                        BrokerFullSpec.class,
                        BrokerController.class);
    }
}