package com.datastax.oss.pulsaroperator.controllers.proxy;

import static org.mockito.Mockito.mock;
import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.BaseComponentStatus;
import com.datastax.oss.pulsaroperator.crds.proxy.Proxy;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxyFullSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.Test;

@JBossLog
public class ProxyControllerTest {

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

        Assert.assertEquals(client
                        .getCreatedResource(ConfigMap.class).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ConfigMap
                        metadata:
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: proxy
                          name: pulsarname-proxy
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: Proxy
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        data:
                          PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                          PULSAR_GC: -XX:+UseG1GC
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_MEM: -Xms1g -Xmx1g -XX:MaxDirectMemorySize=1g
                          brokerServiceURL: pulsar://pulsarname-broker.ns.svc.cluster.local:6650/
                          brokerServiceURLTLS: pulsar+ssl://pulsarname-broker.ns.svc.cluster.local:6651/
                          brokerWebServiceURL: http://pulsarname-broker.ns.svc.cluster.local:8080/
                          brokerWebServiceURLTLS: https://pulsarname-broker.ns.svc.cluster.local:8443/
                          clusterName: pulsarname
                          configurationStoreServers: pulsarname-zookeeper-ca.ns.svc.cluster.local:2181
                          numHttpServerThreads: 10
                          zookeeperServers: pulsarname-zookeeper-ca.ns.svc.cluster.local:2181
                        """);

        Assert.assertEquals(client
                        .getCreatedResources(ConfigMap.class).get(1).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ConfigMap
                        metadata:
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: proxy
                          name: pulsarname-proxy-ws
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: Proxy
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        data:
                          PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                          PULSAR_GC: -XX:+UseG1GC
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_MEM: -Xms1g -Xmx1g -XX:MaxDirectMemorySize=1g
                          brokerServiceUrl: pulsar://pulsarname-broker.ns.svc.cluster.local:6650/
                          brokerServiceUrlTls: pulsar+ssl://pulsarname-broker.ns.svc.cluster.local:6651/
                          clusterName: pulsarname
                          configurationStoreServers: pulsarname-zookeeper-ca.ns.svc.cluster.local:2181
                          numHttpServerThreads: 10
                          serviceUrl: http://pulsarname-broker.ns.svc.cluster.local:8080/
                          serviceUrlTls: https://pulsarname-broker.ns.svc.cluster.local:8443/
                          webServicePort: 8000
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
                    component: proxy
                  name: pulsarname-proxy
                  namespace: ns
                  ownerReferences:
                  - apiVersion: com.datastax.oss/v1alpha1
                    kind: Proxy
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsarname-cr
                spec:
                  ports:
                  - name: http
                    port: 8080
                  - name: pulsar
                    port: 6650
                  selector:
                    app: pulsarname
                    component: proxy
                  type: LoadBalancer
                """);

        final String depl = client
                .getCreatedResource(Deployment.class).getResourceYaml();
        Assert.assertEquals(depl, """
                ---
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  labels:
                    app: pulsarname
                    cluster: pulsarname
                    component: proxy
                  name: pulsarname-proxy
                  namespace: ns
                  ownerReferences:
                  - apiVersion: com.datastax.oss/v1alpha1
                    kind: Proxy
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsarname-cr
                spec:
                  replicas: 3
                  selector:
                    matchLabels:
                      app: pulsarname
                      component: proxy
                  strategy:
                    rollingUpdate:
                      maxSurge: 1
                      maxUnavailable: 0
                    type: RollingUpdate
                  template:
                    metadata:
                      annotations:
                        prometheus.io/port: 8080
                        prometheus.io/scrape: "true"
                      labels:
                        app: pulsarname
                        cluster: pulsarname
                        component: proxy
                    spec:
                      containers:
                      - args:
                        - "bin/apply-config-from-env.py conf/proxy.conf && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar proxy"
                        command:
                        - sh
                        - -c
                        envFrom:
                        - configMapRef:
                            name: pulsarname-proxy
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        livenessProbe:
                          exec:
                            command:
                            - sh
                            - -c
                            - curl -s --max-time 5 --fail http://localhost:8080/metrics/ > /dev/null
                          initialDelaySeconds: 10
                          periodSeconds: 30
                          timeoutSeconds: 5
                        name: pulsarname-proxy
                        ports:
                        - containerPort: 8001
                          name: wss
                        readinessProbe:
                          exec:
                            command:
                            - sh
                            - -c
                            - curl -s --max-time 5 --fail http://localhost:8080/metrics/ > /dev/null
                          initialDelaySeconds: 10
                          periodSeconds: 30
                          timeoutSeconds: 5
                        resources:
                          requests:
                            cpu: 1
                            memory: 1Gi
                      - args:
                        - "bin/apply-config-from-env.py conf/websocket.conf && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar websocket"
                        command:
                        - sh
                        - -c
                        envFrom:
                        - configMapRef:
                            name: pulsarname-proxy-ws
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        name: pulsarname-proxy-ws
                        ports:
                        - containerPort: 8080
                          name: http
                        resources:
                          requests:
                            cpu: 1
                            memory: 1Gi
                      initContainers:
                      - args:
                        - |
                          until nslookup pulsarname-bookkeeper-0.pulsarname-bookkeeper.ns; do
                              sleep 3;
                          done;
                        command:
                        - sh
                        - -c
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        name: wait-bookkeeper-ready
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
                            component: proxy
                          name: pulsarname-proxy
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: Proxy
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          maxUnavailable: 1
                          selector:
                            matchLabels:
                              app: pulsarname
                              component: proxy
                            """);

    }

    @Test
    public void testConfig() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    config:
                        PULSAR_LOG_LEVEL: debug
                        customConfig: customValue
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();

        expectedData.put("brokerServiceURL", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("brokerServiceURLTLS", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedData.put("brokerWebServiceURL", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("brokerWebServiceURLTLS", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("clusterName", "pul");

        expectedData.put("PULSAR_MEM", "-Xms1g -Xmx1g -XX:MaxDirectMemorySize=1g");
        expectedData.put("PULSAR_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "debug");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("numHttpServerThreads", "10");
        expectedData.put("customConfig", "customValue");

        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);
    }

    @Test
    public void testConfigWebSocket() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    config:
                        PULSAR_LOG_LEVEL: debug
                        customConfig: customValue
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResources(ConfigMap.class).get(1);

        Map<String, String> expectedData = new HashMap<>();

        expectedData.put("brokerServiceUrl", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("brokerServiceUrlTls", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedData.put("serviceUrl", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("serviceUrlTls", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("clusterName", "pul");
        expectedData.put("webServicePort", "8000");

        expectedData.put("PULSAR_MEM", "-Xms1g -Xmx1g -XX:MaxDirectMemorySize=1g");
        expectedData.put("PULSAR_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "debug");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("numHttpServerThreads", "10");
        expectedData.put("customConfig", "customValue");
        expectedData.put("webServicePort", "8000");

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
                proxy:
                    replicas: 5
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);

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

        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);


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
                proxy:
                    image: apachepulsar/pulsar:zk
                    imagePullPolicy: Always
                """;
        client = invokeController(spec);
        createdResource =
                client.getCreatedResource(Deployment.class);


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
                proxy:
                    updateStrategy:
                        type: RollingUpdate
                        rollingUpdate:
                            maxUnavailable: 2
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);

        Assert.assertEquals(
                createdResource.getResource().getSpec().getStrategy()
                        .getType(),
                "RollingUpdate"
        );
        Assert.assertEquals(
                (int) createdResource.getResource().getSpec().getStrategy()
                        .getRollingUpdate().getMaxUnavailable().getIntVal(),
                2
        );
    }

    @Test
    public void testAnnotations() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    annotations:
                        annotation-1: ann1-value
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);

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
                proxy:
                    gracePeriod: -1
                """;
        invokeControllerAndAssertError(spec, "invalid configuration property \"proxy.gracePeriod\" for value "
                + "\"-1\": must be greater than or equal to 0");

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    gracePeriod: 0
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);

        Assert.assertEquals((long) createdResource.getResource().getSpec().getTemplate()
                .getSpec().getTerminationGracePeriodSeconds(), 0L);

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    gracePeriod: 120
                """;

        client = invokeController(spec);

        createdResource =
                client.getCreatedResource(Deployment.class);

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
                proxy:
                    resources:
                        requests:
                          memory: 1.5Gi
                          cpu: 0.5
                        limits:
                          memory: 2Gi
                          cpu: 1
                    webSocket:
                        resources:
                            requests:
                              memory: 1.3Gi
                              cpu: 0.3
                            limits:
                              memory: 1.5Gi
                              cpu: 0.5
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);
        final ResourceRequirements resources = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0).getResources();

        Assert.assertEquals(resources.getRequests().get("memory"), Quantity.parse("1.5Gi"));
        Assert.assertEquals(resources.getRequests().get("cpu"), Quantity.parse("0.5"));

        Assert.assertEquals(resources.getLimits().get("memory"), Quantity.parse("2Gi"));
        Assert.assertEquals(resources.getLimits().get("cpu"), Quantity.parse("1"));

        final ResourceRequirements wsResources = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(1).getResources();

        Assert.assertEquals(wsResources.getRequests().get("memory"), Quantity.parse("1.3Gi"));
        Assert.assertEquals(wsResources.getRequests().get("cpu"), Quantity.parse("0.3"));

        Assert.assertEquals(wsResources.getLimits().get("memory"), Quantity.parse("1.5Gi"));
        Assert.assertEquals(wsResources.getLimits().get("cpu"), Quantity.parse("0.5"));

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
                proxy:
                    nodeSelectors:
                        overridelabel: overridden
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);
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


        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);
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
                proxy:
                    probe:
                        enabled: false
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);


        Container container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        Assert.assertNull(container.getLivenessProbe());
        Assert.assertNull(container.getReadinessProbe());

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    probe:
                        enabled: true
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(Deployment.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertProbe(container.getLivenessProbe(), 5, 10, 30);
        assertProbe(container.getReadinessProbe(), 5, 10, 30);


        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    probe:
                        enabled: true
                        period: 50
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(Deployment.class);

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
                proxy:
                    service:
                        annotations:
                            myann: myann-value
                        additionalPorts:
                            - name: myport1
                              port: 3333
                        loadBalancerIP: 10.11.11.11
                        type: ClusterIP
                        enablePlainTextWithTLS: true
                """;

        MockKubernetesClient client = invokeController(spec);


        final MockKubernetesClient.ResourceInteraction<Service> service =
                client.getCreatedResource(Service.class);

        final ObjectMeta metadata = service.getResource().getMetadata();
        Assert.assertEquals(metadata.getName(), "pul-proxy");
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

        Assert.assertNull(service.getResource().getSpec().getClusterIP());
        Assert.assertEquals(service.getResource().getSpec().getType(), "ClusterIP");
        Assert.assertEquals(service.getResource().getSpec().getLoadBalancerIP(), "10.11.11.11");

        final MockKubernetesClient.ResourceInteraction<Deployment> deployment =
                client.getCreatedResource(Deployment.class);

        Assert.assertEquals(
                deployment.getResource()
                        .getSpec()
                        .getTemplate()
                        .getSpec()
                        .getContainers()
                        .get(0)
                        .getPorts()
                        .stream()
                        .filter(p -> p.getName().equals("myport1"))
                        .findFirst()
                        .get()
                        .getContainerPort()
                        .intValue(),
                3333
        );
    }

    @Test
    public void testPdbMaxUnavailable() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
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


        Deployment depl = client.getCreatedResource(Deployment.class).getResource();
        System.out.println(depl.getSpec().getTemplate()
                .getMetadata().getAnnotations());
        final String checksum1 = depl.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-proxy");
        Assert.assertNotNull(checksum1);

        final String checksum1ws = depl.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-proxy-ws");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        depl = client.getCreatedResource(Deployment.class).getResource();
        Assert.assertEquals(depl.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-proxy"),
                checksum1);
        Assert.assertEquals(depl.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-proxy-ws"),
                checksum1ws);

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    restartOnConfigMapChange: true
                proxy:
                    config:
                        PULSAR_ROOT_LOG_LEVEL: debug
                """;

        client = invokeController(spec);
        depl = client.getCreatedResource(Deployment.class).getResource();
        final String checksum2 = depl.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-proxy");
        Assert.assertNotNull(checksum2);
        Assert.assertNotEquals(checksum1, checksum2);

        final String checksum2ws = depl.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-proxy-ws");
        Assert.assertNotNull(checksum2ws);
        Assert.assertNotEquals(checksum1ws, checksum2ws);
    }

    private void assertProbe(Probe probe, int timeout, int initial, int period) {
        Assert.assertEquals(probe.getExec().getCommand(), List.of("sh", "-c",
                "curl -s --max-time %d --fail http://localhost:8080/metrics/ > /dev/null".formatted(timeout)));

        Assert.assertEquals((int) probe.getInitialDelaySeconds(), initial);
        Assert.assertEquals((int) probe.getTimeoutSeconds(), timeout);
        Assert.assertEquals((int) probe.getPeriodSeconds(), period);
    }


    @SneakyThrows
    private void invokeControllerAndAssertError(String spec, String expectedErrorMessage) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<Proxy> result = invokeController(mockKubernetesClient, spec);
        Assert.assertTrue(result.isUpdateStatus());
        Assert.assertFalse(result.getResource().getStatus().isReady());
        Assert.assertEquals(result.getResource().getStatus().getMessage(),
                expectedErrorMessage);
        Assert.assertEquals(result.getResource().getStatus().getReason(),
                BaseComponentStatus.Reason.ErrorConfig);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<Proxy>
                result = invokeController(mockKubernetesClient, spec);
        Assert.assertTrue(result.isUpdateStatus());
        Assert.assertTrue(result.getResource().getStatus().isReady());
        Assert.assertNull(result.getResource().getStatus().getMessage());
        Assert.assertNull(result.getResource().getStatus().getReason());
        return mockKubernetesClient;
    }

    private UpdateControl<Proxy> invokeController(MockKubernetesClient mockKubernetesClient, String spec)
            throws Exception {
        final ProxyController controller = new ProxyController(mockKubernetesClient.getClient());

        final Proxy proxy = new Proxy();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(CLUSTER_NAME + "-cr");
        meta.setNamespace(NAMESPACE);
        proxy.setMetadata(meta);

        final ProxyFullSpec proxyFullSpec = MockKubernetesClient.readYaml(spec, ProxyFullSpec.class);
        proxy.setSpec(proxyFullSpec);

        final UpdateControl<Proxy> result = controller.reconcile(proxy, mock(Context.class));
        return result;
    }
}