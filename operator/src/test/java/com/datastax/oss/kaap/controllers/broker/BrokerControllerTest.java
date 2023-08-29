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
package com.datastax.oss.kaap.controllers.broker;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.ControllerTestUtil;
import com.datastax.oss.kaap.controllers.KubeTestUtil;
import com.datastax.oss.kaap.crds.BaseComponentStatus;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.broker.Broker;
import com.datastax.oss.kaap.crds.broker.BrokerFullSpec;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
import com.datastax.oss.kaap.mocks.MockResourcesResolver;
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
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
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
    static final String CLUSTER_NAME = "pulsar-spec-1";
    private final ControllerTestUtil<BrokerFullSpec, Broker> controllerTestUtil =
            new ControllerTestUtil<>(NAMESPACE, CLUSTER_NAME);

    @Test
    public void testDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
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
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: broker
                            resource-set: broker
                          name: pulsar-spec-1-broker
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1alpha1
                            kind: Broker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        data:
                          PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_MEM: -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler.linkCapacity=1024 -XX:+ExitOnOutOfMemoryError
                          PULSAR_PREFIX_allowAutoTopicCreationType: non-partitioned
                          PULSAR_PREFIX_backlogQuotaDefaultRetentionPolicy: producer_exception
                          PULSAR_PREFIX_bookkeeperClientRegionawarePolicyEnabled: "true"
                          PULSAR_PREFIX_brokerDeduplicationEnabled: "false"
                          PULSAR_PREFIX_clusterName: pulsar-spec-1
                          PULSAR_PREFIX_configurationStoreServers: pulsar-spec-1-zookeeper-ca.ns.svc.cluster.local:2181
                          PULSAR_PREFIX_exposeConsumerLevelMetricsInPrometheus: "false"
                          PULSAR_PREFIX_exposeTopicLevelMetricsInPrometheus: "true"
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
                    component: broker
                    resource-set: broker
                  name: pulsar-spec-1-broker
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1alpha1
                    kind: Broker
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-spec-1-cr
                spec:
                  clusterIP: None
                  ports:
                  - name: http
                    port: 8080
                  - name: pulsar
                    port: 6650
                  selector:
                    app: pulsar
                    cluster: pulsar-spec-1
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
                    app: pulsar
                    cluster: pulsar-spec-1
                    component: broker
                    resource-set: broker
                  name: pulsar-spec-1-broker
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1alpha1
                    kind: Broker
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-spec-1-cr
                spec:
                  replicas: 3
                  selector:
                    matchLabels:
                      app: pulsar
                      cluster: pulsar-spec-1
                      component: broker
                  serviceName: pulsar-spec-1-broker
                  template:
                    metadata:
                      annotations:
                        prometheus.io/port: 8080
                        prometheus.io/scrape: "true"
                      labels:
                        app: pulsar
                        cluster: pulsar-spec-1
                        component: broker
                        resource-set: broker
                    spec:
                      affinity:
                        podAntiAffinity:
                          requiredDuringSchedulingIgnoredDuringExecution:
                          - labelSelector:
                              matchLabels:
                                app: pulsar
                                cluster: pulsar-spec-1
                                component: broker
                            topologyKey: kubernetes.io/hostname
                      containers:
                      - args:
                        - "bin/apply-config-from-env.py conf/broker.conf && bin/apply-config-from-env.py conf/client.conf && bin/gen-yml-from-env.py conf/functions_worker.yml && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar broker"
                        command:
                        - sh
                        - -c
                        envFrom:
                        - configMapRef:
                            name: pulsar-spec-1-broker
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
                        name: pulsar-spec-1-broker
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
                            component: broker
                            resource-set: broker
                          name: pulsar-spec-1-broker
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1alpha1
                            kind: Broker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          maxUnavailable: 1
                          selector:
                            matchLabels:
                              app: pulsar
                              cluster: pulsar-spec-1
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
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");
        expectedData.put("PULSAR_PREFIX_allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_brokerDeduplicationEnabled", "false");
        expectedData.put("PULSAR_PREFIX_exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("PULSAR_PREFIX_exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("PULSAR_PREFIX_backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("PULSAR_PREFIX_functionsWorkerEnabled", "true");
        expectedData.put("PULSAR_PREFIX_pulsarFunctionsCluster", "pul");
        expectedData.put("PULSAR_PREFIX_pulsarServiceUrl", "pulsar://localhost:6650");
        expectedData.put("PULSAR_PREFIX_pulsarWebServiceUrl", "http://localhost:8080");
        expectedData.put("PULSAR_PREFIX_bookkeeperClientRegionawarePolicyEnabled", "true");


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
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");
        expectedData.put("PULSAR_PREFIX_allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_brokerDeduplicationEnabled", "false");
        expectedData.put("PULSAR_PREFIX_exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("PULSAR_PREFIX_exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("PULSAR_PREFIX_backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("PULSAR_PREFIX_transactionCoordinatorEnabled", "true");
        expectedData.put("PULSAR_PREFIX_bookkeeperClientRegionawarePolicyEnabled", "true");

        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);


        final Broker brokerCr =
                controllerTestUtil.createCustomResource(Broker.class, BrokerFullSpec.class, spec);
        final BrokerController.BrokerSetsLastApplied statusLastApplied =
                new BrokerController.BrokerSetsLastApplied();
        statusLastApplied.setCommon(brokerCr.getSpec());
        statusLastApplied.getSets().put(BrokerResourcesFactory.BROKER_DEFAULT_SET, brokerCr.getSpec());
        brokerCr.setStatus(
                new BaseComponentStatus(List.of(), SerializationUtil.writeAsJson(statusLastApplied))
        );
        client = new MockKubernetesClient(NAMESPACE, new MockResourcesResolver() {
            @Override
            public StatefulSet statefulSetWithName(String name) {
                return newStatefulSetBuilder(name, true).build();
            }
        });
        invokeController(brokerCr, client);


        Assert.assertEquals(client.getCreatedResource(Job.class).getResourceYaml(),
                """
                        ---
                        apiVersion: batch/v1
                        kind: Job
                        metadata:
                          labels:
                            app: pulsar
                            cluster: pul
                            component: broker
                            resource-set: broker
                          name: pul-broker
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1alpha1
                            kind: Broker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          template:
                            metadata:
                              labels:
                                app: pulsar
                                cluster: pul
                                component: broker
                                resource-set: broker
                            spec:
                              containers:
                              - args:
                                - |
                                  bin/pulsar initialize-transaction-coordinator-metadata --cluster pul \\
                                      --configuration-store pul-zookeeper-ca.ns.svc.cluster.local:2181 \\
                                      --initial-num-transaction-coordinators 16
                                command:
                                - sh
                                - -c
                                image: apachepulsar/pulsar:global
                                imagePullPolicy: IfNotPresent
                                name: pul-broker
                              dnsConfig:
                                options:
                                - name: ndots
                                  value: 4
                              restartPolicy: OnFailure
                        """);
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
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");
        expectedData.put("PULSAR_PREFIX_allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_LOG_LEVEL", "debug");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_brokerDeduplicationEnabled", "false");
        expectedData.put("PULSAR_PREFIX_exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("PULSAR_PREFIX_exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("PULSAR_PREFIX_backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("PULSAR_PREFIX_customConfig", "customValue");
        expectedData.put("PULSAR_PREFIX_bookkeeperClientRegionawarePolicyEnabled", "true");


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
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");
        expectedData.put("PULSAR_PREFIX_allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_brokerDeduplicationEnabled", "false");
        expectedData.put("PULSAR_PREFIX_exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("PULSAR_PREFIX_exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("PULSAR_PREFIX_backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("PULSAR_PREFIX_bookkeeperClientRegionawarePolicyEnabled", "true");
        expectedData.put("PULSAR_PREFIX_authParams", "file:///pulsar/token-superuser-stripped.jwt");
        expectedData.put("PULSAR_PREFIX_authPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
        expectedData.put("PULSAR_PREFIX_authorizationEnabled", "true");
        expectedData.put("PULSAR_PREFIX_authenticationEnabled", "true");
        expectedData.put("PULSAR_PREFIX_authenticationProviders",
                "org.apache.pulsar.broker.authentication.AuthenticationProviderToken");
        expectedData.put("PULSAR_PREFIX_proxyRoles", "proxy");
        expectedData.put("PULSAR_PREFIX_superUserRoles", "admin,proxy,superuser,websocket");
        expectedData.put("PULSAR_PREFIX_tokenPublicKey", "file:///pulsar/token-public-key/my-public.key");
        expectedData.put("PULSAR_PREFIX_brokerClientAuthenticationPlugin",
                "org.apache.pulsar.client.impl.auth.AuthenticationToken");
        expectedData.put("PULSAR_PREFIX_brokerClientAuthenticationParameters",
                "file:///pulsar/token-superuser/superuser.jwt");

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
        Assert.assertTrue(cmdArg.contains(
                "cat /pulsar/token-superuser/superuser.jwt | tr -d '\\n' > /pulsar/token-superuser-stripped"
                        + ".jwt && "));

        Assert.assertEquals(sts.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getLivenessProbe().getExec().getCommand()
                .get(2), "curl -s --max-time 5 --fail -H \"Authorization: Bearer $(cat "
                + "/pulsar/token-superuser/superuser.jwt | tr -d '\\r')\" "
                + "http://localhost:8080/admin/v2/brokers/health > /dev/null");
    }

    @Test
    public void testTlsEnabledOnBroker() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    tls:
                        broker:
                            enabled: true
                broker:
                    transactions:
                        enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");
        expectedData.put("PULSAR_PREFIX_allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_brokerDeduplicationEnabled", "false");
        expectedData.put("PULSAR_PREFIX_exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("PULSAR_PREFIX_exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("PULSAR_PREFIX_backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("PULSAR_PREFIX_tlsEnabled", "true");
        expectedData.put("PULSAR_PREFIX_tlsCertificateFilePath", "/pulsar/certs/tls.crt");
        expectedData.put("PULSAR_PREFIX_tlsKeyFilePath", " /pulsar/tls-pk8.key");
        expectedData.put("PULSAR_PREFIX_tlsTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_brokerServicePortTls", "6651");
        expectedData.put("PULSAR_PREFIX_brokerClientTlsEnabled", "true");
        expectedData.put("PULSAR_PREFIX_webServicePortTls", "8443");
        expectedData.put("PULSAR_PREFIX_brokerClientTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_brokerClient_tlsHostnameVerificationEnable", "true");
        expectedData.put("PULSAR_PREFIX_transactionCoordinatorEnabled", "true");
        expectedData.put("PULSAR_PREFIX_bookkeeperClientRegionawarePolicyEnabled", "true");


        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);


        final StatefulSet sts = client.getCreatedResource(StatefulSet.class).getResource();
        KubeTestUtil.assertTlsVolumesMounted(sts, GlobalSpec.DEFAULT_TLS_SECRET_NAME);
        final String stsCommand = sts.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getArgs().get(0);
        Assert.assertEquals(stsCommand, """
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
                echo '' && bin/apply-config-from-env.py conf/broker.conf && bin/apply-config-from-env.py conf/client.conf && bin/gen-yml-from-env.py conf/functions_worker.yml && OPTS="${OPTS} -Dlog4j2.formatMsgNoLookups=true" exec bin/pulsar broker""");


        final Service service = client.getCreatedResource(Service.class).getResource();
        final List<ServicePort> ports = service.getSpec().getPorts();
        Assert.assertEquals(ports.size(), 4);
        for (ServicePort port : ports) {
            switch (port.getName()) {
                case "http":
                    Assert.assertEquals((int) port.getPort(), 8080);
                    break;
                case "pulsar":
                    Assert.assertEquals((int) port.getPort(), 6650);
                    break;
                case "https":
                    Assert.assertEquals((int) port.getPort(), 8443);
                    break;
                case "pulsarssl":
                    Assert.assertEquals((int) port.getPort(), 6651);
                    break;
                default:
                    Assert.fail("unexpected port " + port.getName());
                    break;
            }
        }

        final Broker brokerCr =
                controllerTestUtil.createCustomResource(Broker.class, BrokerFullSpec.class, spec);
        final BrokerController.BrokerSetsLastApplied statusLastApplied =
                new BrokerController.BrokerSetsLastApplied();
        statusLastApplied.setCommon(brokerCr.getSpec());
        statusLastApplied.getSets().put(BrokerResourcesFactory.BROKER_DEFAULT_SET, brokerCr.getSpec());
        brokerCr.setStatus(
                new BaseComponentStatus(List.of(), SerializationUtil.writeAsJson(statusLastApplied))
        );
        client = new MockKubernetesClient(NAMESPACE, new MockResourcesResolver() {
            @Override
            public StatefulSet statefulSetWithName(String name) {
                return newStatefulSetBuilder(name, true).build();
            }
        });
        invokeController(brokerCr, client);

        final Job job = client.getCreatedResource(Job.class).getResource();
        KubeTestUtil.assertTlsVolumesMounted(
                job.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts(),
                job.getSpec().getTemplate().getSpec().getVolumes(),
                GlobalSpec.DEFAULT_TLS_SECRET_NAME
        );
        final String jobCommand = sts.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getArgs().get(0);
        Assert.assertEquals(jobCommand, """
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
                echo '' && bin/apply-config-from-env.py conf/broker.conf && bin/apply-config-from-env.py conf/client.conf && bin/gen-yml-from-env.py conf/functions_worker.yml && OPTS="${OPTS} -Dlog4j2.formatMsgNoLookups=true" exec bin/pulsar broker""");
    }

    @Test
    public void testTlsEnabledOnBookKeeper() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    tls:
                        broker:
                            enabled: true
                        bookkeeper:
                            enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");
        expectedData.put("PULSAR_PREFIX_allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_brokerDeduplicationEnabled", "false");
        expectedData.put("PULSAR_PREFIX_exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("PULSAR_PREFIX_exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("PULSAR_PREFIX_backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("PULSAR_PREFIX_tlsEnabled", "true");
        expectedData.put("PULSAR_PREFIX_tlsCertificateFilePath", "/pulsar/certs/tls.crt");
        expectedData.put("PULSAR_PREFIX_tlsKeyFilePath", " /pulsar/tls-pk8.key");
        expectedData.put("PULSAR_PREFIX_tlsTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_brokerServicePortTls", "6651");
        expectedData.put("PULSAR_PREFIX_brokerClientTlsEnabled", "true");
        expectedData.put("PULSAR_PREFIX_webServicePortTls", "8443");
        expectedData.put("PULSAR_PREFIX_brokerClientTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_brokerClient_tlsHostnameVerificationEnable", "true");
        expectedData.put("PULSAR_PREFIX_bookkeeperTLSClientAuthentication", "true");
        expectedData.put("PULSAR_PREFIX_bookkeeperTLSKeyFileType", "PEM");
        expectedData.put("PULSAR_PREFIX_bookkeeperTLSKeyFilePath", "/pulsar/tls-pk8.key");
        expectedData.put("PULSAR_PREFIX_bookkeeperTLSCertificateFilePath", "/pulsar/certs/tls.crt");
        expectedData.put("PULSAR_PREFIX_bookkeeperTLSTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_bookkeeperTLSTrustCertTypes", "PEM");
        expectedData.put("PULSAR_PREFIX_bookkeeperUseV2WireProtocol", "false");
        expectedData.put("PULSAR_PREFIX_bookkeeperClientRegionawarePolicyEnabled", "true");


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
                    name: pulsar-spec-1
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
                    name: pulsar-spec-1
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
                    podAnnotations:
                        annotation-2: ann2-value
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
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
                broker:
                    labels:
                        label-1: label1-value
                    podLabels:
                        label-2: label2-value
                """;
        MockKubernetesClient client = invokeController(spec);


        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
                        .getResource().getSpec().getTemplate().getMetadata().getLabels(),
                Map.of(
                        "cluster", "pul",
                        "app", "pulsar",
                        "component", "broker",
                        "label-2", "label2-value",
                        "resource-set", "broker"
                )
        );
        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class).getResource().getMetadata().getLabels(),
                Map.of(
                        "cluster", "pul",
                        "app", "pulsar",
                        "component", "broker",
                        "label-1", "label1-value",
                        "resource-set", "broker"
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
                broker:
                    matchLabels:
                        cluster: ""
                        app: another-app
                        custom: customvalue
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
                        .getResource().getSpec().getSelector().getMatchLabels(),
                Map.of(
                        "app", "another-app",
                        "component", "broker",
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
                broker:
                    env:
                    - name: env1
                      value: env1-value
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
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
                broker:
                    overrideResourceName: broker-override
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "broker-override"));
        Assert.assertNotNull(client.getCreatedResource(ConfigMap.class, "broker-override"));
        Assert.assertNotNull(client.getCreatedResource(Service.class, "broker-override"));
        Assert.assertNotNull(client.getCreatedResource(PodDisruptionBudget.class, "broker-override"));
    }

    @Test
    public void testInitContainers() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                broker:
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

        final List<Container> initContainers = client.getCreatedResource(StatefulSet.class)
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
                broker:
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

        final List<Container> containers = client.getCreatedResource(StatefulSet.class)
                .getResource().getSpec().getTemplate().getSpec().getContainers();
        Assert.assertEquals(containers.size(), 2);
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
                broker:
                    imagePullSecrets:
                        - secret1
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
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
                broker:
                    serviceAccountName: my-service-account
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
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
    public void testPriorityClassNameOnJob() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    priorityClassName: pulsar-priority
                broker:
                    transactions:
                        enabled: true
                """;

        final Broker brokerCr =
                controllerTestUtil
                        .createCustomResource(Broker.class, BrokerFullSpec.class, spec);
        final BrokerController.BrokerSetsLastApplied statusLastApplied =
                new BrokerController.BrokerSetsLastApplied();
        statusLastApplied.setCommon(brokerCr.getSpec());
        statusLastApplied.getSets().put(BrokerResourcesFactory.BROKER_DEFAULT_SET, brokerCr.getSpec());
        brokerCr.setStatus(
                new BaseComponentStatus(List.of(), SerializationUtil.writeAsJson(statusLastApplied))
        );
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, new MockResourcesResolver() {
            @Override
            public StatefulSet statefulSetWithName(String name) {
                return newStatefulSetBuilder(name, true).build();
            }
        });
        invokeController(brokerCr, client);
        Assert.assertEquals(client.getCreatedResource(Job.class)
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


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);
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
                broker:
                    tolerations:
                        - key: "app"
                          operator: "Equal"
                          value: "pulsar"
                          effect: "NoSchedule"
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);
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
                broker:
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


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);
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
        final PodAffinityTerm term = client.getCreatedResource(StatefulSet.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getAffinity().getPodAntiAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .get(0);
        Assert.assertEquals(term.getTopologyKey(), "kubernetes.io/hostname");
        Assert.assertEquals(term.getLabelSelector().getMatchLabels(), Map.of(
                "app", "pulsar",
                "cluster", "pul",
                "component", "broker"
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
        final WeightedPodAffinityTerm weightedPodAffinityTerm = client.getCreatedResource(StatefulSet.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getAffinity().getPodAntiAffinity()
                .getPreferredDuringSchedulingIgnoredDuringExecution()
                .get(0);
        Assert.assertEquals(weightedPodAffinityTerm.getWeight().intValue(), 100);
        Assert.assertEquals(weightedPodAffinityTerm.getPodAffinityTerm().getTopologyKey(), "kubernetes.io/hostname");
        Assert.assertEquals(weightedPodAffinityTerm.getPodAffinityTerm().getLabelSelector().getMatchLabels(), Map.of(
                "app", "pulsar",
                "cluster", "pul",
                "component", "broker"
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
        Assert.assertNull(client.getCreatedResource(StatefulSet.class)
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
        final WeightedPodAffinityTerm weightedPodAffinityTerm = client.getCreatedResource(StatefulSet.class)
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
                "component", "broker"
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
        final PodAffinityTerm term = client.getCreatedResource(StatefulSet.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getAffinity().getPodAntiAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .get(0);
        Assert.assertEquals(term.getTopologyKey(), "kubernetes.io/hostname");
        Assert.assertEquals(term.getLabelSelector().getMatchLabels(), Map.of(
                "app", "pulsar",
                "cluster", "pul",
                "component", "broker"
        ));

        final WeightedPodAffinityTerm weightedPodAffinityTerm = client.getCreatedResource(StatefulSet.class)
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
                "component", "broker"
        ));
    }

    @Test
    public void testTransactionsJobTolerations() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                broker:
                    transactions:
                        enabled: true
                    tolerations:
                        - key: "app"
                          operator: "Equal"
                          value: "pulsar"
                          effect: "NoSchedule"
                """;

        final Broker brokerCr =
                controllerTestUtil.createCustomResource(Broker.class, BrokerFullSpec.class, spec);
        final BrokerController.BrokerSetsLastApplied statusLastApplied =
                new BrokerController.BrokerSetsLastApplied();
        statusLastApplied.setCommon(brokerCr.getSpec());
        statusLastApplied.getSets().put(BrokerResourcesFactory.BROKER_DEFAULT_SET, brokerCr.getSpec());
        brokerCr.setStatus(
                new BaseComponentStatus(List.of(), SerializationUtil.writeAsJson(statusLastApplied))
        );
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, new MockResourcesResolver() {
            @Override
            public StatefulSet statefulSetWithName(String name) {
                return newStatefulSetBuilder(name, true).build();
            }
        });
        invokeController(brokerCr, client);

        MockKubernetesClient.ResourceInteraction<Job> createdResource =
                client.getCreatedResource(Job.class);
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
    public void testProbe() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                broker:
                    probes:
                        liveness:
                            enabled: false
                        readiness:
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
                    probes:
                        liveness:
                            enabled: true
                        readiness:
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
                    probes:
                        liveness:
                            enabled: true
                            initialDelaySeconds: 90
                        readiness:
                            enabled: true
                            timeoutSeconds: 10
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(StatefulSet.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertProbe(container.getLivenessProbe(), 5, 90, 30);
        assertProbe(container.getReadinessProbe(), 10, 10, 30);

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
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-broker");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        Assert.assertEquals(sts.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-broker"),
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
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-broker");
        Assert.assertNotNull(checksum2);
        Assert.assertNotEquals(checksum1, checksum2);
    }

    private void assertProbe(Probe probe, int timeout, int initial, int period) {
        Assert.assertEquals(probe.getExec().getCommand(), List.of("sh", "-c",
                "curl -s --max-time %d --fail  http://localhost:8080/admin/v2/brokers/health > /dev/null".formatted(
                        timeout)));

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
                broker:
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


    @Test
    public void testKafka() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                broker:
                    kafka:
                        enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");
        expectedData.put("PULSAR_PREFIX_allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_brokerDeduplicationEnabled", "false");
        expectedData.put("PULSAR_PREFIX_exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("PULSAR_PREFIX_exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("PULSAR_PREFIX_backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("PULSAR_PREFIX_bookkeeperClientRegionawarePolicyEnabled", "true");

        expectedData.put("PULSAR_PREFIX_protocolHandlerDirectory", "./protocols");
        expectedData.put("PULSAR_PREFIX_messagingProtocols", "kafka");
        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryEnable", "true");
        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryPort", "8081");
        expectedData.put("PULSAR_PREFIX_kafkaTransactionCoordinatorEnabled", "true");
        expectedData.put("PULSAR_PREFIX_kafkaNamespace", "kafka");
        expectedData.put("PULSAR_PREFIX_kafkaListeners", "PLAINTEXT://0.0.0.0:9092");
        expectedData.put("PULSAR_PREFIX_kafkaAdvertisedListeners", "PLAINTEXT://advertisedAddress:9092");
        expectedData.put("PULSAR_PREFIX_brokerEntryMetadataInterceptors",
                "org.apache.pulsar.common.intercept.AppendIndexMetadataInterceptor,org.apache.pulsar.common"
                        + ".intercept.AppendBrokerTimestampMetadataInterceptor");
        expectedData.put("PULSAR_PREFIX_brokerDeleteInactiveTopicsEnabled", "false");

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
                broker:
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
                        broker:
                            enabled: true
                broker:
                    kafka:
                        enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_clusterName", "pul");
        expectedData.put("PULSAR_PREFIX_allowAutoTopicCreationType", "non-partitioned");
        expectedData.put("PULSAR_MEM",
                "-Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_brokerDeduplicationEnabled", "false");
        expectedData.put("PULSAR_PREFIX_exposeTopicLevelMetricsInPrometheus", "true");
        expectedData.put("PULSAR_PREFIX_exposeConsumerLevelMetricsInPrometheus", "false");
        expectedData.put("PULSAR_PREFIX_backlogQuotaDefaultRetentionPolicy", "producer_exception");
        expectedData.put("PULSAR_PREFIX_tlsEnabled", "true");
        expectedData.put("PULSAR_PREFIX_tlsCertificateFilePath", "/pulsar/certs/tls.crt");
        expectedData.put("PULSAR_PREFIX_tlsKeyFilePath", " /pulsar/tls-pk8.key");
        expectedData.put("PULSAR_PREFIX_tlsTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_brokerServicePortTls", "6651");
        expectedData.put("PULSAR_PREFIX_brokerClientTlsEnabled", "true");
        expectedData.put("PULSAR_PREFIX_webServicePortTls", "8443");
        expectedData.put("PULSAR_PREFIX_brokerClientTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_brokerClient_tlsHostnameVerificationEnable", "true");
        expectedData.put("PULSAR_PREFIX_bookkeeperClientRegionawarePolicyEnabled", "true");

        expectedData.put("PULSAR_PREFIX_protocolHandlerDirectory", "./protocols");
        expectedData.put("PULSAR_PREFIX_messagingProtocols", "kafka");
        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryEnable", "true");
        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryPort", "8081");
        expectedData.put("PULSAR_PREFIX_kafkaTransactionCoordinatorEnabled", "true");
        expectedData.put("PULSAR_PREFIX_kafkaNamespace", "kafka");
        expectedData.put("PULSAR_PREFIX_kafkaListeners", "SASL_SSL://0.0.0.0:9093");
        expectedData.put("PULSAR_PREFIX_kafkaAdvertisedListeners", "SASL_SSL://advertisedAddress:9093");
        expectedData.put("PULSAR_PREFIX_brokerEntryMetadataInterceptors",
                "org.apache.pulsar.common.intercept.AppendIndexMetadataInterceptor,org.apache.pulsar.common"
                        + ".intercept.AppendBrokerTimestampMetadataInterceptor");
        expectedData.put("PULSAR_PREFIX_brokerDeleteInactiveTopicsEnabled", "false");
        expectedData.put("PULSAR_PREFIX_kopSchemaRegistryEnableTls", "true");
        expectedData.put("PULSAR_PREFIX_kopSslTruststoreLocation", "/pulsar/tls.truststore.jks");



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
        controllerTestUtil
                .invokeControllerAndAssertError(spec,
                        expectedErrorMessage,
                        Broker.class,
                        BrokerFullSpec.class,
                        BrokerController.class);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return controllerTestUtil
                .invokeController(spec,
                        Broker.class,
                        BrokerFullSpec.class,
                        BrokerController.class);
    }

    @SneakyThrows
    private void invokeController(Broker broker, MockKubernetesClient client) {
        controllerTestUtil.invokeController(client, broker, BrokerController.class);
    }

}
