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
package com.datastax.oss.kaap.controllers.proxy;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.ControllerTestUtil;
import com.datastax.oss.kaap.controllers.KubeTestUtil;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.proxy.Proxy;
import com.datastax.oss.kaap.crds.proxy.ProxyFullSpec;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.NodeSelectorRequirement;
import io.fabric8.kubernetes.api.model.NodeSelectorTerm;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodAffinityTerm;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.apps.Deployment;
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
public class ProxyControllerTest {

    static final String NAMESPACE = "ns";
    static final String CLUSTER_NAME = "pulsar-spec-1";

    @Test
    public void testDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
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
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: proxy
                            resource-set: proxy
                          name: pulsar-spec-1-proxy
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: Proxy
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        data:
                          PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_PREFIX_brokerServiceURL: pulsar://pulsar-spec-1-broker.ns.svc.cluster.local:6650/
                          PULSAR_PREFIX_brokerServiceURLTLS: pulsar+ssl://pulsar-spec-1-broker.ns.svc.cluster.local:6651/
                          PULSAR_PREFIX_brokerWebServiceURL: http://pulsar-spec-1-broker.ns.svc.cluster.local:8080/
                          PULSAR_PREFIX_brokerWebServiceURLTLS: https://pulsar-spec-1-broker.ns.svc.cluster.local:8443/
                          PULSAR_PREFIX_clusterName: pulsar-spec-1
                          PULSAR_PREFIX_configurationStoreServers: pulsar-spec-1-zookeeper-ca.ns.svc.cluster.local:2181
                          PULSAR_PREFIX_numHttpServerThreads: 10
                          PULSAR_PREFIX_zookeeperServers: pulsar-spec-1-zookeeper-ca.ns.svc.cluster.local:2181
                        """);

        Assert.assertEquals(client
                        .getCreatedResources(ConfigMap.class).get(1).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ConfigMap
                        metadata:
                          labels:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: proxy
                            resource-set: proxy
                          name: pulsar-spec-1-proxy-ws
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: Proxy
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        data:
                          PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_PREFIX_brokerServiceUrl: pulsar://pulsar-spec-1-broker.ns.svc.cluster.local:6650/
                          PULSAR_PREFIX_brokerServiceUrlTls: pulsar+ssl://pulsar-spec-1-broker.ns.svc.cluster.local:6651/
                          PULSAR_PREFIX_clusterName: pulsar-spec-1
                          PULSAR_PREFIX_configurationStoreServers: pulsar-spec-1-zookeeper-ca.ns.svc.cluster.local:2181
                          PULSAR_PREFIX_numHttpServerThreads: 10
                          PULSAR_PREFIX_serviceUrl: http://pulsar-spec-1-broker.ns.svc.cluster.local:8080/
                          PULSAR_PREFIX_serviceUrlTls: https://pulsar-spec-1-broker.ns.svc.cluster.local:8443/
                          PULSAR_PREFIX_webServicePort: 8000
                          PULSAR_PREFIX_zookeeperServers: pulsar-spec-1-zookeeper-ca.ns.svc.cluster.local:2181
                        """);

        final String service = client
                .getCreatedResource(Service.class).getResourceYaml();
        Assert.assertEquals(service, """
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  labels:
                    app: pulsar
                    cluster: pulsar-spec-1
                    component: proxy
                    resource-set: proxy
                  name: pulsar-spec-1-proxy
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
                    kind: Proxy
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-spec-1-cr
                spec:
                  ports:
                  - name: http
                    port: 8080
                  - name: pulsar
                    port: 6650
                  - name: ws
                    port: 8000
                  selector:
                    app: pulsar
                    cluster: pulsar-spec-1
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
                    app: pulsar
                    cluster: pulsar-spec-1
                    component: proxy
                    resource-set: proxy
                  name: pulsar-spec-1-proxy
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
                    kind: Proxy
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-spec-1-cr
                spec:
                  replicas: 3
                  selector:
                    matchLabels:
                      app: pulsar
                      cluster: pulsar-spec-1
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
                        app: pulsar
                        cluster: pulsar-spec-1
                        component: proxy
                        resource-set: proxy
                    spec:
                      affinity:
                        podAntiAffinity:
                          requiredDuringSchedulingIgnoredDuringExecution:
                          - labelSelector:
                              matchLabels:
                                app: pulsar
                                cluster: pulsar-spec-1
                                component: proxy
                            topologyKey: kubernetes.io/hostname
                      containers:
                      - args:
                        - "bin/apply-config-from-env.py conf/proxy.conf && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar proxy"
                        command:
                        - sh
                        - -c
                        envFrom:
                        - configMapRef:
                            name: pulsar-spec-1-proxy
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        livenessProbe:
                          exec:
                            command:
                            - sh
                            - -c
                            - curl -s --max-time 5 --fail  http://localhost:8080/metrics/ > /dev/null
                          initialDelaySeconds: 10
                          periodSeconds: 30
                          timeoutSeconds: 5
                        name: pulsar-spec-1-proxy
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
                            - curl -s --max-time 5 --fail  http://localhost:8080/metrics/ > /dev/null
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
                            name: pulsar-spec-1-proxy-ws
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        name: pulsar-spec-1-proxy-ws
                        ports:
                        - containerPort: 8000
                          name: ws
                        resources:
                          requests:
                            cpu: 1
                            memory: 1Gi
                      dnsConfig:
                        options:
                        - name: ndots
                          value: 4
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
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: proxy
                            resource-set: proxy
                          name: pulsar-spec-1-proxy
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: Proxy
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          maxUnavailable: 1
                          selector:
                            matchLabels:
                              app: pulsar
                              cluster: pulsar-spec-1
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

        expectedData.put("PULSAR_PREFIX_brokerServiceURL", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("PULSAR_PREFIX_brokerServiceURLTLS", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedData.put("PULSAR_PREFIX_brokerWebServiceURL", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("PULSAR_PREFIX_brokerWebServiceURLTLS", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");

        expectedData.put("PULSAR_LOG_LEVEL", "debug");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_numHttpServerThreads", "10");
        expectedData.put("PULSAR_PREFIX_customConfig", "customValue");

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

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_brokerServiceURL", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("PULSAR_PREFIX_brokerServiceURLTLS", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedData.put("PULSAR_PREFIX_brokerWebServiceURL", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("PULSAR_PREFIX_brokerWebServiceURLTLS", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");

        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_numHttpServerThreads", "10");

        expectedData.put("PULSAR_PREFIX_authenticationEnabled", "true");
        expectedData.put("PULSAR_PREFIX_authorizationEnabled", "true");
        expectedData.put("PULSAR_PREFIX_superUserRoles", "admin,proxy,superuser,websocket");
        expectedData.put("PULSAR_PREFIX_authenticationProviders",
                "org.apache.pulsar.broker.authentication.AuthenticationProviderToken");
        expectedData.put("PULSAR_PREFIX_tokenPublicKey", "file:///pulsar/token-public-key/my-public.key");
        expectedData.put("PULSAR_PREFIX_brokerClientAuthenticationPlugin",
                "org.apache.pulsar.client.impl.auth.AuthenticationToken");
        expectedData.put("PULSAR_PREFIX_brokerClientAuthenticationParameters", "file:///pulsar/token-proxy/proxy.jwt");

        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).get(0).getResource().getData(),
                expectedData);


        Map<String, Object> expectedDataForWs = new HashMap<>();
        expectedDataForWs.put("PULSAR_PREFIX_brokerServiceUrl", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedDataForWs.put("PULSAR_PREFIX_brokerServiceUrlTls",
                "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedDataForWs.put("PULSAR_PREFIX_serviceUrl", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedDataForWs.put("PULSAR_PREFIX_serviceUrlTls", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedDataForWs.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedDataForWs.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedDataForWs.put("PULSAR_PREFIX_clusterName", "pul");
        expectedDataForWs.put("PULSAR_PREFIX_webServicePort", "8000");

        expectedDataForWs.put("PULSAR_LOG_LEVEL", "info");
        expectedDataForWs.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedDataForWs.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedDataForWs.put("PULSAR_PREFIX_numHttpServerThreads", "10");
        expectedDataForWs.put("PULSAR_PREFIX_webServicePort", "8000");
        expectedDataForWs.put("PULSAR_PREFIX_authenticationProviders",
                "org.apache.pulsar.broker.authentication.AuthenticationProviderToken,"
                        + "org.apache.pulsar.broker.authentication.AuthenticationProviderTls");
        expectedDataForWs.put("PULSAR_PREFIX_authenticationEnabled", "true");
        expectedDataForWs.put("PULSAR_PREFIX_authorizationEnabled", "true");
        expectedDataForWs.put("PULSAR_PREFIX_superUserRoles", "admin,proxy,superuser,websocket");
        expectedDataForWs.put("PULSAR_PREFIX_authenticationProviders",
                "org.apache.pulsar.broker.authentication.AuthenticationProviderToken,"
                        + "org.apache.pulsar.broker.authentication.AuthenticationProviderTls");
        expectedDataForWs.put("PULSAR_PREFIX_tokenPublicKey", "file:///pulsar/token-public-key/my-public.key");
        expectedDataForWs.put("PULSAR_PREFIX_brokerClientAuthenticationPlugin",
                "org.apache.pulsar.client.impl.auth.AuthenticationToken");
        expectedDataForWs.put("PULSAR_PREFIX_brokerClientAuthenticationParameters",
                "file:///pulsar/token-websocket/websocket.jwt");

        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).get(1).getResource().getData(),
                expectedDataForWs);


        final Deployment deployment = client.getCreatedResource(Deployment.class).getResource();

        final List<Volume> volumes = deployment.getSpec().getTemplate().getSpec().getVolumes();
        final List<VolumeMount> volumeMounts = deployment.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getVolumeMounts();
        KubeTestUtil.assertRolesMounted(volumes, volumeMounts,
                "private-key", "public-key", "superuser", "websocket", "proxy");
        final String cmdArg = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs()
                .stream().collect(Collectors.joining(" "));
        Assert.assertTrue(cmdArg.contains(
                "cat /pulsar/token-superuser/superuser.jwt | tr -d '\\n' > /pulsar/token-superuser-stripped"
                        + ".jwt && "));

        final String cmdArgWs = deployment.getSpec().getTemplate().getSpec().getContainers().get(1).getArgs()
                .stream().collect(Collectors.joining(" "));
        Assert.assertTrue(cmdArgWs.contains("echo \"tokenPublicKey=\" >> /pulsar/conf/websocket.conf && "));

        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getLivenessProbe().getExec().getCommand()
                .get(2), "curl -s --max-time 5 --fail -H \"Authorization: Bearer $(cat "
                + "/pulsar/token-superuser/superuser.jwt | tr -d '\\r')\" http://localhost:8080/metrics/ > /dev/null");

        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getReadinessProbe().getExec().getCommand()
                .get(2), "curl -s --max-time 5 --fail -H \"Authorization: Bearer $(cat "
                + "/pulsar/token-superuser/superuser.jwt | tr -d '\\r')\" http://localhost:8080/metrics/ > /dev/null");
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
                        dont: takeit
                    webSocket:
                        config:
                            PULSAR_LOG_LEVEL: trace
                            please: takethis
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResources(ConfigMap.class).get(1);

        Map<String, String> expectedData = new HashMap<>();

        expectedData.put("PULSAR_PREFIX_brokerServiceUrl", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("PULSAR_PREFIX_brokerServiceUrlTls", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedData.put("PULSAR_PREFIX_serviceUrl", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("PULSAR_PREFIX_serviceUrlTls", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");
        expectedData.put("PULSAR_PREFIX_webServicePort", "8000");

        expectedData.put("PULSAR_LOG_LEVEL", "trace");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_numHttpServerThreads", "10");
        expectedData.put("PULSAR_PREFIX_please", "takethis");
        expectedData.put("PULSAR_PREFIX_webServicePort", "8000");

        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);
    }


    @Test
    public void testTlsEnabledOnProxy() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    tls:
                        broker:
                            enabled: true
                        proxy:
                            enabled: true
                            enabledWithBroker: true
                        functionsWorker:
                            enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_brokerServiceURL", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("PULSAR_PREFIX_brokerServiceURLTLS", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedData.put("PULSAR_PREFIX_brokerWebServiceURL", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("PULSAR_PREFIX_brokerWebServiceURLTLS", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");

        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_numHttpServerThreads", "10");
        expectedData.put("PULSAR_PREFIX_tlsEnabledWithKeyStore", "true");
        expectedData.put("PULSAR_PREFIX_tlsKeyStore", "/pulsar/tls.keystore.jks");
        expectedData.put("PULSAR_PREFIX_tlsTrustStore", "/pulsar/tls.truststore.jks");
        expectedData.put("PULSAR_PREFIX_brokerClientTlsTrustStore", "/pulsar/tls.truststore.jks");
        expectedData.put("PULSAR_PREFIX_tlsEnabledInProxy", "true");
        expectedData.put("PULSAR_PREFIX_tlsCertificateFilePath", "/pulsar/certs/tls.crt");
        expectedData.put("PULSAR_PREFIX_tlsKeyFilePath", "/pulsar/tls-pk8.key");
        expectedData.put("PULSAR_PREFIX_tlsTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_brokerClientTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_brokerServicePortTls", "6651");
        expectedData.put("PULSAR_PREFIX_webServicePortTls", "8443");
        expectedData.put("PULSAR_PREFIX_servicePortTls", "6651");
        expectedData.put("PULSAR_PREFIX_tlsEnabledWithBroker", "true");
        expectedData.put("PULSAR_PREFIX_tlsHostnameVerificationEnabled", "true");
        expectedData.put("PULSAR_PREFIX_functionWorkerWebServiceURLTLS",
                "https://pul-function-ca.ns.svc.cluster.local:6751");

        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);


        final ConfigMap wsConfigMap = client.getCreatedResources(ConfigMap.class).get(1).getResource();
        Map<String, String> wsExpectedData = new HashMap<>();

        wsExpectedData.put("PULSAR_PREFIX_brokerServiceUrl", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        wsExpectedData.put("PULSAR_PREFIX_brokerServiceUrlTls", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        wsExpectedData.put("PULSAR_PREFIX_serviceUrl", "http://pul-broker.ns.svc.cluster.local:8080/");
        wsExpectedData.put("PULSAR_PREFIX_serviceUrlTls", "https://pul-broker.ns.svc.cluster.local:8443/");
        wsExpectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        wsExpectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        wsExpectedData.put("PULSAR_PREFIX_clusterName", "pul");
        wsExpectedData.put("PULSAR_PREFIX_webServicePort", "8000");

        wsExpectedData.put("PULSAR_LOG_LEVEL", "info");
        wsExpectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        wsExpectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        wsExpectedData.put("PULSAR_PREFIX_numHttpServerThreads", "10");
        wsExpectedData.put("PULSAR_PREFIX_webServicePort", "8000");

        wsExpectedData.put("PULSAR_PREFIX_webServicePortTls", "8001");
        wsExpectedData.put("PULSAR_PREFIX_tlsEnabled", "true");
        wsExpectedData.put("PULSAR_PREFIX_tlsCertificateFilePath", "/pulsar/certs/tls.crt");
        wsExpectedData.put("PULSAR_PREFIX_tlsKeyFilePath", "/pulsar/tls-pk8.key");
        wsExpectedData.put("PULSAR_PREFIX_tlsEnabledWithKeyStore", "true");
        wsExpectedData.put("PULSAR_PREFIX_tlsKeyStore", "/pulsar/tls.keystore.jks");
        wsExpectedData.put("PULSAR_PREFIX_tlsTrustStore", "/pulsar/tls.truststore.jks");
        wsExpectedData.put("PULSAR_PREFIX_brokerClientTlsEnabled", "true");
        wsExpectedData.put("PULSAR_PREFIX_tlsTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        wsExpectedData.put("PULSAR_PREFIX_brokerClientTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");

        Assert.assertEquals(wsConfigMap.getData(), wsExpectedData);


        final Deployment deployment = client.getCreatedResource(Deployment.class).getResource();
        KubeTestUtil.assertTlsVolumesMounted(deployment, GlobalSpec.DEFAULT_TLS_SECRET_NAME);
        final String command = deployment.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getArgs().get(0);
        Assert.assertEquals(command, """
                openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key -out /pulsar/tls-pk8.key -nocrypt && certconverter() {
                    local name=pulsar
                    local crtFile=/pulsar/certs/tls.crt
                    local keyFile=/pulsar/certs/tls.key
                    caFile=/etc/ssl/certs/ca-certificates.crt
                    p12File=/pulsar/tls.p12
                    keyStoreFile=/pulsar/tls.keystore.jks
                    trustStoreFile=/pulsar/tls.truststore.jks
                                
                    head /dev/urandom | base64 | head -c 24 > /pulsar/keystoreSecret.txt
                    export tlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                    export PF_tlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                    export tlsKeyStorePassword=$(cat /pulsar/keystoreSecret.txt)
                    export PF_tlsKeyStorePassword=$(cat /pulsar/keystoreSecret.txt)
                    export PULSAR_PREFIX_brokerClientTlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                                
                    openssl pkcs12 \\
                        -export \\
                        -in ${crtFile} \\
                        -inkey ${keyFile} \\
                        -out ${p12File} \\
                        -name ${name} \\
                        -passout "file:/pulsar/keystoreSecret.txt"
                                
                    keytool -importkeystore \\
                        -srckeystore ${p12File} \\
                        -srcstoretype PKCS12 -srcstorepass:file "/pulsar/keystoreSecret.txt" \\
                        -alias ${name} \\
                        -destkeystore ${keyStoreFile} \\
                        -deststorepass:file "/pulsar/keystoreSecret.txt"
                                
                    keytool -import \\
                        -file ${caFile} \\
                        -storetype JKS \\
                        -alias ${name} \\
                        -keystore ${trustStoreFile} \\
                        -storepass:file "/pulsar/keystoreSecret.txt" \\
                        -trustcacerts -noprompt
                } &&
                certconverter &&
                echo '' && bin/apply-config-from-env.py conf/proxy.conf && OPTS="${OPTS} -Dlog4j2.formatMsgNoLookups=true" exec bin/pulsar proxy""");


        final Service service = client.getCreatedResource(Service.class).getResource();
        final List<ServicePort> ports = service.getSpec().getPorts();
        Assert.assertEquals(ports.size(), 3);
        for (ServicePort port : ports) {
            switch (port.getName()) {
                case "https":
                    Assert.assertEquals((int) port.getPort(), 8443);
                    break;
                case "pulsarssl":
                    Assert.assertEquals((int) port.getPort(), 6651);
                    break;
                case "wss":
                    Assert.assertEquals((int) port.getPort(), 8001);
                    break;
                default:
                    Assert.fail("unexpected port " + port.getName());
                    break;
            }
        }
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
                    name: pulsar-spec-1
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
                    name: pulsar-spec-1
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
                    podAnnotations:
                        annotation-2: ann2-value
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(Deployment.class)
                        .getResource().getSpec().getTemplate().getMetadata().getAnnotations(),
                Map.of(
                        "prometheus.io/scrape", "true",
                        "prometheus.io/port", "8080",
                        "annotation-2", "ann2-value"
                )
        );
        client.getCreatedResources().forEach(resource -> {
            if (resource.getResource() instanceof Service) {
                return;
            }
            Assert.assertEquals(
                    resource.getResource().getMetadata().getAnnotations(),
                    Map.of(
                            "annotation-1", "ann1-value"
                    )
            );
        });
    }


    @Test
    public void testLabels() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    labels:
                        label-1: label1-value
                    podLabels:
                        label-2: label2-value
                """;
        MockKubernetesClient client = invokeController(spec);


        Assert.assertEquals(
                client.getCreatedResource(Deployment.class)
                        .getResource().getSpec().getTemplate().getMetadata().getLabels(),
                Map.of(
                        "cluster", "pul",
                        "app", "pulsar",
                        "component", "proxy",
                        "resource-set", "proxy",
                        "label-2", "label2-value"
                )
        );
        Assert.assertEquals(
                client.getCreatedResource(Deployment.class).getResource().getMetadata().getLabels(),
                Map.of(
                        "cluster", "pul",
                        "app", "pulsar",
                        "component", "proxy",
                        "resource-set", "proxy",
                        "label-1", "label1-value"
                )
        );
    }

    @Test
    public void testMatchLabels() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    matchLabels:
                        cluster: ""
                        app: another-app
                        custom: customvalue
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(Deployment.class)
                        .getResource().getSpec().getSelector().getMatchLabels(),
                Map.of(
                        "app", "another-app",
                        "component", "proxy",
                        "custom", "customvalue"
                )
        );
    }


    @Test
    public void testEnv() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                proxy:
                    env:
                    - name: env1
                      value: env1-value
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(Deployment.class)
                        .getResource().getSpec().getTemplate().getSpec().getContainers().get(0)
                        .getEnv(),
                List.of(new EnvVar("env1", "env1-value", null))
        );
    }

    @Test
    public void testResourceNameOverride() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                proxy:
                    overrideResourceName: proxy-override
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertNotNull(client.getCreatedResource(Deployment.class, "proxy-override"));
        Assert.assertNotNull(client.getCreatedResource(ConfigMap.class, "proxy-override"));
        Assert.assertNotNull(client.getCreatedResource(Service.class, "proxy-override"));
        Assert.assertNotNull(client.getCreatedResource(PodDisruptionBudget.class, "proxy-override"));
    }

    @Test
    public void testInitContainers() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                proxy:
                    initContainers:
                        - name: myinit
                          image: myimage:latest
                          command: ["echo test"]
                          volumeMounts:
                            - name: certs
                              mountPath: /pulsar/certs
                              readOnly: true
                          resources:
                            requests:
                              cpu: 100m
                              memory: 128Mi
                """;
        MockKubernetesClient client = invokeController(spec);

        final List<Container> initContainers = client.getCreatedResource(Deployment.class)
                .getResource().getSpec().getTemplate().getSpec().getInitContainers();
        Assert.assertEquals(initContainers.size(), 1);
        Assert.assertEquals(SerializationUtil.writeAsYaml(initContainers.get(0)),
                """
                        ---
                        command:
                        - echo test
                        image: myimage:latest
                        name: myinit
                        resources:
                          requests:
                            cpu: 100m
                            memory: 128Mi
                        volumeMounts:
                        - mountPath: /pulsar/certs
                          name: certs
                          readOnly: true
                        """
        );
    }


    @Test
    public void testSidecars() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                proxy:
                    sidecars:
                        - name: mycontainer
                          image: myimage:latest
                          command: ["echo test"]
                          volumeMounts:
                            - name: certs
                              mountPath: /pulsar/certs
                              readOnly: true
                          resources:
                            requests:
                              cpu: 100m
                              memory: 128Mi
                """;
        MockKubernetesClient client = invokeController(spec);

        final List<Container> containers = client.getCreatedResource(Deployment.class)
                .getResource().getSpec().getTemplate().getSpec().getContainers();
        Assert.assertEquals(containers.size(), 3);
        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        KubeTestUtil.getContainerByName(containers, "mycontainer")),
                """
                        ---
                        command:
                        - echo test
                        image: myimage:latest
                        name: mycontainer
                        resources:
                          requests:
                            cpu: 100m
                            memory: 128Mi
                        volumeMounts:
                        - mountPath: /pulsar/certs
                          name: certs
                          readOnly: true
                        """
        );
    }

    @Test
    public void testImagePullSecrets() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    imagePullSecrets:
                        - secret1
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(Deployment.class)
                        .getResource().getSpec().getTemplate().getSpec().getImagePullSecrets(),
                List.of(new LocalObjectReference("secret1"))
        );
    }


    @Test
    public void testServiceAccountName() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    serviceAccountName: my-service-account
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(Deployment.class)
                        .getResource().getSpec().getTemplate().getSpec().getServiceAccountName(),
                "my-service-account"
        );
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
    public void testPriorityClassName() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    priorityClassName: pulsar-priority
                """;

        MockKubernetesClient client = invokeController(spec);
        Assert.assertEquals(client.getCreatedResource(Deployment.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getPriorityClassName(), "pulsar-priority");
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
    public void testTolerations() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                proxy:
                    tolerations:
                        - key: "app"
                          operator: "Equal"
                          value: "pulsar"
                          effect: "NoSchedule"
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);
        final List<Toleration> tolerations = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getTolerations();
        Assert.assertEquals(tolerations.size(), 1);
        final Toleration toleration = tolerations.get(0);
        Assert.assertEquals(toleration.getKey(), "app");
        Assert.assertEquals(toleration.getOperator(), "Equal");
        Assert.assertEquals(toleration.getValue(), "pulsar");
        Assert.assertEquals(toleration.getEffect(), "NoSchedule");
    }


    @Test
    public void testNodeAffinity() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                proxy:
                    nodeAffinity:
                        requiredDuringSchedulingIgnoredDuringExecution:
                            nodeSelectorTerms:
                                - matchExpressions:
                                    - key: nodepool
                                      operator: In
                                      values:
                                      - pulsar
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);
        final NodeAffinity nodeAffinity = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getAffinity().getNodeAffinity();

        final List<NodeSelectorTerm> nodeSelectorTerms =
                nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution().getNodeSelectorTerms();
        Assert.assertEquals(nodeSelectorTerms.size(), 1);

        final NodeSelectorRequirement nodeSelectorRequirement = nodeSelectorTerms.get(0).getMatchExpressions().get(0);
        Assert.assertEquals(nodeSelectorRequirement.getKey(), "nodepool");
        Assert.assertEquals(nodeSelectorRequirement.getOperator(), "In");
        Assert.assertEquals(nodeSelectorRequirement.getValues(), List.of("pulsar"));
    }

    @Test
    public void testPodAntiAffinityHost() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                """;
        MockKubernetesClient client = invokeController(spec);
        final PodAffinityTerm term = client.getCreatedResource(Deployment.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getAffinity().getPodAntiAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .get(0);
        Assert.assertEquals(term.getTopologyKey(), "kubernetes.io/hostname");
        Assert.assertEquals(term.getLabelSelector().getMatchLabels(), Map.of(
                "app", "pulsar",
                "cluster", "pul",
                "component", "proxy"
        ));

        spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                    antiAffinity:
                        host:
                            required: false
                """;
        client = invokeController(spec);
        final WeightedPodAffinityTerm weightedPodAffinityTerm = client.getCreatedResource(Deployment.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getAffinity().getPodAntiAffinity()
                .getPreferredDuringSchedulingIgnoredDuringExecution()
                .get(0);
        Assert.assertEquals(weightedPodAffinityTerm.getWeight().intValue(), 100);
        Assert.assertEquals(weightedPodAffinityTerm.getPodAffinityTerm().getTopologyKey(), "kubernetes.io/hostname");
        Assert.assertEquals(weightedPodAffinityTerm.getPodAffinityTerm().getLabelSelector().getMatchLabels(), Map.of(
                "app", "pulsar",
                "cluster", "pul",
                "component", "proxy"
        ));

        spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                    antiAffinity:
                        host:
                            enabled: false
                """;
        client = invokeController(spec);
        Assert.assertNull(client.getCreatedResource(Deployment.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getAffinity());
    }

    @Test
    public void testPodAntiAffinityZone() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                    antiAffinity:
                        host:
                            enabled: false
                        zone:
                            enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);
        final WeightedPodAffinityTerm weightedPodAffinityTerm = client.getCreatedResource(Deployment.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getAffinity().getPodAntiAffinity()
                .getPreferredDuringSchedulingIgnoredDuringExecution()
                .get(0);
        Assert.assertEquals(weightedPodAffinityTerm.getWeight().intValue(), 100);
        Assert.assertEquals(weightedPodAffinityTerm
                .getPodAffinityTerm().getTopologyKey(), "failure-domain.beta.kubernetes.io/zone");
        Assert.assertEquals(weightedPodAffinityTerm.getPodAffinityTerm().getLabelSelector().getMatchLabels(), Map.of(
                "app", "pulsar",
                "cluster", "pul",
                "component", "proxy"
        ));
    }


    @Test
    public void testPodAntiAffinityHostAndZone() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                    antiAffinity:
                        zone:
                            enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);
        final PodAffinityTerm term = client.getCreatedResource(Deployment.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getAffinity().getPodAntiAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .get(0);
        Assert.assertEquals(term.getTopologyKey(), "kubernetes.io/hostname");
        Assert.assertEquals(term.getLabelSelector().getMatchLabels(), Map.of(
                "app", "pulsar",
                "cluster", "pul",
                "component", "proxy"
        ));

        final WeightedPodAffinityTerm weightedPodAffinityTerm = client.getCreatedResource(Deployment.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getAffinity().getPodAntiAffinity()
                .getPreferredDuringSchedulingIgnoredDuringExecution()
                .get(0);
        Assert.assertEquals(weightedPodAffinityTerm.getWeight().intValue(), 100);
        Assert.assertEquals(weightedPodAffinityTerm
                .getPodAffinityTerm().getTopologyKey(), "failure-domain.beta.kubernetes.io/zone");
        Assert.assertEquals(weightedPodAffinityTerm.getPodAffinityTerm().getLabelSelector().getMatchLabels(), Map.of(
                "app", "pulsar",
                "cluster", "pul",
                "component", "proxy"
        ));
    }


    @Test
    public void testProbe() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    probes:
                        liveness:
                            enabled: false
                        readiness:
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
                    probes:
                        liveness:
                            enabled: true
                        readiness:
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
                    probes:
                        readiness:
                            periodSeconds: 50
                        liveness:
                            periodSeconds: 45
                            
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(Deployment.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertProbe(container.getLivenessProbe(), 5, 10, 45);
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
        Assert.assertEquals(ports.size(), 4);
        for (ServicePort port : ports) {
            switch (port.getName()) {
                case "http":
                    Assert.assertEquals((int) port.getPort(), 8080);
                    break;
                case "pulsar":
                    Assert.assertEquals((int) port.getPort(), 6650);
                    break;
                case "ws":
                    Assert.assertEquals((int) port.getPort(), 8000);
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
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-proxy");
        Assert.assertNotNull(checksum1);

        final String checksum1ws = depl.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-proxy-ws");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        depl = client.getCreatedResource(Deployment.class).getResource();
        Assert.assertEquals(depl.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-proxy"),
                checksum1);
        Assert.assertEquals(depl.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-proxy-ws"),
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
                    webSocket:
                        config:
                            PULSAR_ROOT_LOG_LEVEL: debug
                """;

        client = invokeController(spec);
        depl = client.getCreatedResource(Deployment.class).getResource();
        final String checksum2 = depl.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-proxy");
        Assert.assertNotNull(checksum2);
        Assert.assertNotEquals(checksum1, checksum2);

        final String checksum2ws = depl.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-proxy-ws");
        Assert.assertNotNull(checksum2ws);
        Assert.assertNotEquals(checksum1ws, checksum2ws);
    }

    private void assertProbe(Probe probe, int timeout, int initial, int period) {
        Assert.assertEquals(probe.getExec().getCommand(), List.of("sh", "-c",
                "curl -s --max-time %d --fail  http://localhost:8080/metrics/ > /dev/null".formatted(timeout)));

        Assert.assertEquals((int) probe.getInitialDelaySeconds(), initial);
        Assert.assertEquals((int) probe.getTimeoutSeconds(), timeout);
        Assert.assertEquals((int) probe.getPeriodSeconds(), period);
    }

    @Test
    public void testAdditionalVolumes() throws Exception {

        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
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

        final Deployment deployment = client.getCreatedResource(Deployment.class).getResource();

        final PodSpec podSpec = deployment.getSpec().getTemplate().getSpec();
        KubeTestUtil.assertVolumeFromSecret(podSpec.getVolumes(), "vol1", "mysecret");
        KubeTestUtil.assertVolumeMount(podSpec.getContainers().get(0)
                .getVolumeMounts(), "vol1", "/pulsar/custom", true);
    }


    @Test
    public void testKafka() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    kafka:
                        enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();

        expectedData.put("PULSAR_PREFIX_brokerServiceURL", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("PULSAR_PREFIX_brokerServiceURLTLS", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedData.put("PULSAR_PREFIX_brokerWebServiceURL", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("PULSAR_PREFIX_brokerWebServiceURLTLS", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");

        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_numHttpServerThreads", "10");

        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryEnable", "true");
        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryProxyPort", "8081");
        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryProxyEnableTls", "false");
        expectedData.put("PULSAR_PREFIX_proxyExtensions", "kafka");
        expectedData.put("PULSAR_PREFIX_kafkaTransactionCoordinatorEnabled", "true");
        expectedData.put("PULSAR_PREFIX_kafkaNamespace", "kafka");
        expectedData.put("PULSAR_PREFIX_kafkaListeners", "PLAINTEXT://0.0.0.0:9092");
        expectedData.put("PULSAR_PREFIX_kafkaAdvertisedListeners", "PLAINTEXT://pul-proxy:9092");


        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);

        Assert.assertEquals(client.getCreatedResource(Service.class)
                        .getResource()
                        .getSpec()
                        .getPorts()
                        .stream().filter(p -> p.getName().equals("kafkaplaintext"))
                        .findFirst()
                        .get()
                        .getPort(),
                9092);

        Assert.assertEquals(client.getCreatedResource(Service.class)
                        .getResource()
                        .getSpec()
                        .getPorts()
                        .stream().filter(p -> p.getName().equals("kafkaschemaregistry"))
                        .findFirst()
                        .get()
                        .getPort(),
                8081);

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                proxy:
                    kafka:
                        enabled: true
                        exposePorts: false
                """;
        client = invokeController(spec);

        Assert.assertFalse(client.getCreatedResource(Service.class)
                .getResource()
                .getSpec()
                .getPorts()
                .stream().filter(p -> p.getName().equals("kafkaplaintext"))
                .findFirst()
                .isPresent());

        Assert.assertFalse(client.getCreatedResource(Service.class)
                .getResource()
                .getSpec()
                .getPorts()
                .stream().filter(p -> p.getName().equals("kafkaschemaregistry"))
                .findFirst()
                .isPresent());
    }


    @Test
    public void testKafkaTls() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    tls:
                        proxy:
                            enabled: true
                            enabledWithBroker: true
                proxy:
                    kafka:
                        enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_brokerServiceURL", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("PULSAR_PREFIX_brokerServiceURLTLS", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedData.put("PULSAR_PREFIX_brokerWebServiceURL", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("PULSAR_PREFIX_brokerWebServiceURLTLS", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");

        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_numHttpServerThreads", "10");
        expectedData.put("PULSAR_PREFIX_tlsEnabledWithKeyStore", "true");
        expectedData.put("PULSAR_PREFIX_tlsKeyStore", "/pulsar/tls.keystore.jks");
        expectedData.put("PULSAR_PREFIX_tlsTrustStore", "/pulsar/tls.truststore.jks");
        expectedData.put("PULSAR_PREFIX_brokerClientTlsTrustStore", "/pulsar/tls.truststore.jks");
        expectedData.put("PULSAR_PREFIX_tlsEnabledInProxy", "true");
        expectedData.put("PULSAR_PREFIX_tlsCertificateFilePath", "/pulsar/certs/tls.crt");
        expectedData.put("PULSAR_PREFIX_tlsKeyFilePath", "/pulsar/tls-pk8.key");
        expectedData.put("PULSAR_PREFIX_tlsTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_brokerClientTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_brokerServicePortTls", "6651");
        expectedData.put("PULSAR_PREFIX_webServicePortTls", "8443");
        expectedData.put("PULSAR_PREFIX_servicePortTls", "6651");
        expectedData.put("PULSAR_PREFIX_tlsEnabledWithBroker", "true");
        expectedData.put("PULSAR_PREFIX_tlsHostnameVerificationEnabled", "true");

        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryEnable", "true");
        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryProxyPort", "8081");
        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryProxyEnableTls", "true");
        expectedData.put("PULSAR_PREFIX_proxyExtensions", "kafka");
        expectedData.put("PULSAR_PREFIX_kafkaTransactionCoordinatorEnabled", "true");
        expectedData.put("PULSAR_PREFIX_kafkaNamespace", "kafka");
        expectedData.put("PULSAR_PREFIX_kafkaListeners", "SASL_SSL://0.0.0.0:9093");
        expectedData.put("PULSAR_PREFIX_kafkaAdvertisedListeners", "SASL_SSL://pul-proxy:9093");
        expectedData.put("PULSAR_PREFIX_kopSslTruststoreLocation", "/pulsar/tls.truststore.jks");
        expectedData.put("PULSAR_PREFIX_kopTlsEnabledWithBroker", "true");

        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);

        Assert.assertEquals(client.getCreatedResource(Service.class)
                        .getResource()
                        .getSpec()
                        .getPorts()
                        .stream().filter(p -> p.getName().equals("kafkassl"))
                        .findFirst()
                        .get()
                        .getPort(),
                9093);

        Assert.assertEquals(client.getCreatedResource(Service.class)
                        .getResource()
                        .getSpec()
                        .getPorts()
                        .stream().filter(p -> p.getName().equals("kafkaschemaregistry"))
                        .findFirst()
                        .get()
                        .getPort(),
                8081);
    }


    @SneakyThrows
    private void invokeControllerAndAssertError(String spec, String expectedErrorMessage) {
        new ControllerTestUtil<ProxyFullSpec, Proxy>(NAMESPACE, CLUSTER_NAME)
                .invokeControllerAndAssertError(spec,
                        expectedErrorMessage,
                        Proxy.class,
                        ProxyFullSpec.class,
                        ProxyController.class);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return new ControllerTestUtil<ProxyFullSpec, Proxy>(NAMESPACE, CLUSTER_NAME)
                .invokeController(spec,
                        Proxy.class,
                        ProxyFullSpec.class,
                        ProxyController.class);
    }

}
