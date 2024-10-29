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
package com.datastax.oss.kaap.controllers.zookeeper;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.ControllerTestUtil;
import com.datastax.oss.kaap.controllers.KubeTestUtil;
import com.datastax.oss.kaap.crds.BaseComponentStatus;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeper;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeperFullSpec;
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
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodAffinityTerm;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
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
    static final String CLUSTER_NAME = "pulsar-spec-1";

    @Test
    public void testDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
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
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: zookeeper
                          name: pulsar-spec-1-zookeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: ZooKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        data:
                          PULSAR_EXTRA_OPTS: -Dzookeeper.tcpKeepAlive=true -Dzookeeper.clientTcpKeepAlive=true -Dpulsar.log.root.level=info
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_MEM: -Dcom.sun.management.jmxremote -Djute.maxbuffer=10485760
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
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: zookeeper
                          name: pulsar-spec-1-zookeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: ZooKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          podManagementPolicy: Parallel
                          replicas: 3
                          selector:
                            matchLabels:
                              app: pulsar
                              cluster: pulsar-spec-1
                              component: zookeeper
                          serviceName: pulsar-spec-1-zookeeper
                          template:
                            metadata:
                              annotations:
                                prometheus.io/port: 8000
                                prometheus.io/scrape: "true"
                              labels:
                                app: pulsar
                                cluster: pulsar-spec-1
                                component: zookeeper
                            spec:
                              affinity:
                                podAntiAffinity:
                                  requiredDuringSchedulingIgnoredDuringExecution:
                                  - labelSelector:
                                      matchLabels:
                                        app: pulsar
                                        cluster: pulsar-spec-1
                                        component: zookeeper
                                    topologyKey: kubernetes.io/hostname
                              containers:
                              - args:
                                - "bin/apply-config-from-env.py conf/zookeeper.conf && bin/generate-zookeeper-config.sh conf/zookeeper.conf && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar zookeeper"
                                command:
                                - sh
                                - -c
                                env:
                                - name: ZOOKEEPER_SERVERS
                                  value: "pulsar-spec-1-zookeeper-0,pulsar-spec-1-zookeeper-1,pulsar-spec-1-zookeeper-2"
                                envFrom:
                                - configMapRef:
                                    name: pulsar-spec-1-zookeeper
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
                                name: pulsar-spec-1-zookeeper
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
                                  name: pulsar-spec-1-zookeeper-data
                              dnsConfig:
                                options:
                                - name: ndots
                                  value: 4
                              securityContext:
                                fsGroup: 0
                              terminationGracePeriodSeconds: 60
                          updateStrategy:
                            type: RollingUpdate
                          volumeClaimTemplates:
                          - apiVersion: v1
                            kind: PersistentVolumeClaim
                            metadata:
                              name: pulsar-spec-1-zookeeper-data
                            spec:
                              accessModes:
                              - ReadWriteOnce
                              resources:
                                requests:
                                  storage: 5Gi
                        """);

        final MockKubernetesClient.ResourceInteraction<Service> serviceInt =
                client.getCreatedResources(Service.class).get(0);
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
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: zookeeper
                          name: pulsar-spec-1-zookeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: ZooKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
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
                            app: pulsar
                            cluster: pulsar-spec-1
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
                          labels:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: zookeeper
                          name: pulsar-spec-1-zookeeper-ca
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: ZooKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          ports:
                          - name: server
                            port: 2888
                          - name: leader-election
                            port: 3888
                          - name: client
                            port: 2181
                          selector:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: zookeeper
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
                            component: zookeeper
                          name: pulsar-spec-1-zookeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: ZooKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          maxUnavailable: 1
                          selector:
                            matchLabels:
                              app: pulsar
                              cluster: pulsar-spec-1
                              component: zookeeper
                            """);
    }

    @Test
    public void testImage() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
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
                    name: pulsar-spec-1
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
                .filter(e -> "ZOOKEEPER_SERVERS".equals(e.getName()))
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
        expectedData.put("PULSAR_MEM", "-Dcom.sun.management.jmxremote -Djute.maxbuffer=10485760");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS",
                "-Dzookeeper.tcpKeepAlive=true -Dzookeeper.clientTcpKeepAlive=true -Dpulsar.log"
                        + ".root.level=info");
        expectedData.put("PULSAR_PREFIX_serverCnxnFactory", "my.class.MyClass");

        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);
    }

    @Test
    public void testConfigTlsOnZookeeper() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    tls:
                        zookeeper:
                            enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_MEM", "-Dcom.sun.management.jmxremote -Djute.maxbuffer=10485760");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS",
                "-Dzookeeper.tcpKeepAlive=true -Dzookeeper.clientTcpKeepAlive=true -Dpulsar.log"
                        + ".root.level=info");
        expectedData.put("PULSAR_PREFIX_serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");
        expectedData.put("PULSAR_PREFIX_secureClientPort", "2281");
        expectedData.put("PULSAR_PREFIX_sslQuorum", "true");
        expectedData.put("PULSAR_PREFIX_ssl.protocol", "TLSv1.2");
        expectedData.put("PULSAR_PREFIX_ssl.quorum.protocol", "TLSv1.2");

        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);

        Assert.assertEquals(client.getCreatedResource(Service.class, "pul-zookeeper").getResource()
                .getSpec().getPorts().stream().filter(p -> "client-tls".equals(p.getName())).findFirst()
                .get()
                .getPort().intValue(), 2281);

        Assert.assertEquals(client.getCreatedResource(Service.class, "pul-zookeeper-ca").getResource()
                .getSpec().getPorts().stream().filter(p -> "client-tls".equals(p.getName())).findFirst()
                .get()
                .getPort().intValue(), 2281);

        final StatefulSet sts = client.getCreatedResource(StatefulSet.class).getResource();
        KubeTestUtil.assertTlsVolumesMounted(sts, GlobalSpec.DEFAULT_TLS_SECRET_NAME);

        Assert.assertEquals(sts.getSpec().getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getArgs()
                .get(0), """
                bin/apply-config-from-env.py conf/zookeeper.conf && certconverter() {
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
                (
                cat >> conf/pulsar_env.sh << EOF
                                
                PULSAR_EXTRA_OPTS="\\${PULSAR_EXTRA_OPTS} -Dzookeeper.clientCnxnSocket=org.apache.zookeeper.ClientCnxnSocketNetty -Dzookeeper.client.secure=true -Dzookeeper.ssl.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.keyStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.trustStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.hostnameVerification=true -Dzookeeper.sslQuorum=true -Dzookeeper.serverCnxnFactory=org.apache.zookeeper.server.NettyServerCnxnFactory -Dzookeeper.ssl.quorum.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.quorum.keyStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.quorum.trustStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.quorum.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.quorum.hostnameVerification=true"
                EOF
                ) && (
                cat >> conf/bkenv.sh << EOF
                                
                BOOKIE_EXTRA_OPTS="\\${BOOKIE_EXTRA_OPTS} -Dzookeeper.clientCnxnSocket=org.apache.zookeeper.ClientCnxnSocketNetty -Dzookeeper.client.secure=true -Dzookeeper.ssl.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.keyStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.trustStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.hostnameVerification=true"
                EOF
                ) &&
                echo '' && bin/generate-zookeeper-config.sh conf/zookeeper.conf && OPTS="${OPTS} -Dlog4j2.formatMsgNoLookups=true" exec bin/pulsar zookeeper""");
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
                    podAnnotations:
                        annotation-2: ann2-value
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
                        .getResource().getSpec().getTemplate().getMetadata().getAnnotations(),
                Map.of(
                        "prometheus.io/scrape", "true",
                        "prometheus.io/port", "8000",
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
                zookeeper:
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
                        "component", "zookeeper",
                        "label-2", "label2-value"
                )
        );
        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class).getResource().getMetadata().getLabels(),
                Map.of(
                        "cluster", "pul",
                        "app", "pulsar",
                        "component", "zookeeper",
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
                zookeeper:
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
                        "component", "zookeeper",
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
                zookeeper:
                    env:
                    - name: env1
                      value: env1-value
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
                        .getResource().getSpec().getTemplate().getSpec().getContainers().get(0)
                        .getEnv(),
                List.of(new EnvVar("env1", "env1-value", null),
                        new EnvVar(ZooKeeperResourcesFactory.ENV_ZOOKEEPER_SERVERS,
                                "pul-zookeeper-0,pul-zookeeper-1,pul-zookeeper-2", null))
        );
    }

    @Test
    public void testEnvEmpty() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                zookeeper:
                    env: []
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<ZooKeeper> zooKeeperUpdateControl = invokeController(spec, client);
        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
                        .getResource().getSpec().getTemplate().getSpec().getContainers().get(0)
                        .getEnv().size(),
                1
        );

        final ZooKeeperFullSpec lastApplied = SerializationUtil.readJson(zooKeeperUpdateControl
                .getResource().getStatus().getLastApplied(), ZooKeeperFullSpec.class);
        Assert.assertTrue(lastApplied.getZookeeper().getEnv().isEmpty());
    }

    @Test
    public void testInitContainers() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                zookeeper:
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
                zookeeper:
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
    public void testSidecarsEmpty() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                zookeeper:
                    sidecars: []
                """;
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<ZooKeeper> zooKeeperUpdateControl = invokeController(spec, client);
        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
                        .getResource().getSpec().getTemplate().getSpec().getContainers().size(),
                1
        );

        final ZooKeeperFullSpec lastApplied = SerializationUtil.readJson(zooKeeperUpdateControl
                .getResource().getStatus().getLastApplied(), ZooKeeperFullSpec.class);
        Assert.assertTrue(lastApplied.getZookeeper().getSidecars().isEmpty());
    }

    @Test
    public void testImagePullSecrets() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
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
                zookeeper:
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
    public void testPriorityClassName() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    priorityClassName: pulsar-priority
                """;

        MockKubernetesClient client = invokeController(spec);
        Assert.assertEquals(client.getCreatedResource(StatefulSet.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getPriorityClassName(), "pulsar-priority");
    }

    @Test
    public void testPriorityClassNameOnJob() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    priorityClassName: pulsar-priority
                """;

        final ZooKeeper zkCr =
                new ControllerTestUtil<ZooKeeperFullSpec, ZooKeeper>(NAMESPACE, CLUSTER_NAME)
                        .createCustomResource(ZooKeeper.class, ZooKeeperFullSpec.class, spec);
        zkCr.setStatus(
                new BaseComponentStatus(List.of(), SerializationUtil.writeAsJson(zkCr.getSpec()))
        );
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, new MockResourcesResolver() {
            @Override
            public StatefulSet statefulSetWithName(String name) {
                return newStatefulSetBuilder(name, true).build();
            }
        });
        invokeController(zkCr, client);
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
        Assert.assertEquals(dnsConfig.getSearches(),
                List.of("ns1.svc.cluster-domain.example", "my.dns.search.suffix"));
    }

    @Test
    public void testTolerations() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                zookeeper:
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
    public void testInitMetadataJobTolerations() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                zookeeper:
                    tolerations:
                        - key: "app"
                          operator: "Equal"
                          value: "pulsar"
                          effect: "NoSchedule"
                """;

        final ZooKeeper zkCr =
                new ControllerTestUtil<ZooKeeperFullSpec, ZooKeeper>(NAMESPACE, CLUSTER_NAME)
                        .createCustomResource(ZooKeeper.class, ZooKeeperFullSpec.class, spec);
        zkCr.setStatus(
                new BaseComponentStatus(List.of(), SerializationUtil.writeAsJson(zkCr.getSpec()))
        );
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, new MockResourcesResolver() {
            @Override
            public StatefulSet statefulSetWithName(String name) {
                return newStatefulSetBuilder(name, true).build();
            }
        });
        invokeController(zkCr, client);

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
    public void testNodeAffinity() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                zookeeper:
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

        final NodeSelectorRequirement nodeSelectorRequirement =
                nodeSelectorTerms.get(0).getMatchExpressions().get(0);
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
                "component", "zookeeper"
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
        Assert.assertEquals(weightedPodAffinityTerm.getPodAffinityTerm().getTopologyKey(),
                "kubernetes.io/hostname");
        Assert.assertEquals(weightedPodAffinityTerm.getPodAffinityTerm().getLabelSelector().getMatchLabels(),
                Map.of(
                        "app", "pulsar",
                        "cluster", "pul",
                        "component", "zookeeper"
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
        Assert.assertEquals(weightedPodAffinityTerm.getPodAffinityTerm().getLabelSelector().getMatchLabels(),
                Map.of(
                        "app", "pulsar",
                        "cluster", "pul",
                        "component", "zookeeper"
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
                "component", "zookeeper"
        ));

        final WeightedPodAffinityTerm weightedPodAffinityTerm = client.getCreatedResource(StatefulSet.class)
                .getResource().getSpec().getTemplate()
                .getSpec().getAffinity().getPodAntiAffinity()
                .getPreferredDuringSchedulingIgnoredDuringExecution()
                .get(0);
        Assert.assertEquals(weightedPodAffinityTerm.getWeight().intValue(), 100);
        Assert.assertEquals(weightedPodAffinityTerm
                .getPodAffinityTerm().getTopologyKey(), "failure-domain.beta.kubernetes.io/zone");
        Assert.assertEquals(weightedPodAffinityTerm.getPodAffinityTerm().getLabelSelector().getMatchLabels(),
                Map.of(
                        "app", "pulsar",
                        "cluster", "pul",
                        "component", "zookeeper"
                ));
    }


    @Test
    public void testProbe() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
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
                zookeeper:
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

        assertProbe(container.getLivenessProbe(), 30, 20, 30);
        assertProbe(container.getReadinessProbe(), 30, 20, 30);


        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
                    probes:
                        liveness:
                            enabled: true
                            periodSeconds: 11
                        readiness:
                            enabled: true
                            periodSeconds: 10
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(StatefulSet.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertProbe(container.getLivenessProbe(), 30, 20, 11);
        assertProbe(container.getReadinessProbe(), 30, 20, 10);

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

        Assert.assertEquals(KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-zookeeper-myvol")
                        .getMountPath(),
                "/pulsar/data");
        Assert.assertNotNull(
                KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-zookeeper-myvol").getEmptyDir());
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

        Assert.assertEquals(KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-zookeeper-myvol")
                        .getMountPath(),
                "/pulsar/data");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-zookeeper-myvol"));

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
        return new Object[][]{{"""
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                zookeeper:
                    dataVolume:
                        name: myvol
                        size: 1Gi
                        existingStorageClassName: mystorage-class
                """},
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

        Assert.assertEquals(KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-zookeeper-myvol")
                        .getMountPath(),
                "/pulsar/data");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-zookeeper-myvol"));

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
        return new Object[][]{{"""
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
                zookeeper:
                    dataVolume:
                        name: myvol
                        size: 1Gi
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

        Assert.assertEquals(KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-zookeeper-myvol")
                        .getMountPath(),
                "/pulsar/data");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-zookeeper-myvol"));

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
        Assert.assertEquals(storageClass.getMetadata().getOwnerReferences().size(), 0);

        Assert.assertEquals(storageClass.getMetadata().getLabels().size(), 3);
        Assert.assertEquals(storageClass.getReclaimPolicy(), "Retain");
        Assert.assertEquals(storageClass.getProvisioner(), "kubernetes.io/aws-ebs");
        Assert.assertEquals(storageClass.getParameters().size(), 3);
        Assert.assertEquals(storageClass.getParameters().get("type"), "gp2");
        Assert.assertEquals(storageClass.getParameters().get("fsType"), "ext4");
        Assert.assertEquals(storageClass.getParameters().get("iopsPerGB"), "10");
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
            if ("pul-zookeeper-ca".equals(metadata.getName())) {
                isCaService = true;
            } else if (!"pul-zookeeper".equals(metadata.getName())) {
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
                Assert.assertEquals(annotations.get("service.alpha.kubernetes.io/tolerate-unready-endpoints"),
                        "true");
                Assert.assertEquals(annotations.get("myann"), "myann-value");

                Assert.assertEquals(service.getResource().getSpec().getClusterIP(), "None");
                Assert.assertEquals((boolean) service.getResource().getSpec().getPublishNotReadyAddresses(), true);

            }
        }
    }

    @Test
    public void testPdbMaxUnavailable() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
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
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-zookeeper");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        Assert.assertEquals(sts.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-zookeeper"),
                checksum1);

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    restartOnConfigMapChange: true
                zookeeper:
                    config:
                        PULSAR_ROOT_LOG_LEVEL: debug
                """;

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        final String checksum2 = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-zookeeper");
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
                zookeeper:
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


    @SneakyThrows
    private void invokeControllerAndAssertError(String spec, String expectedErrorMessage) {
        new ControllerTestUtil<ZooKeeperFullSpec, ZooKeeper>(NAMESPACE, CLUSTER_NAME)
                .invokeControllerAndAssertError(spec,
                        expectedErrorMessage,
                        ZooKeeper.class,
                        ZooKeeperFullSpec.class,
                        ZooKeeperController.class);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return new ControllerTestUtil<ZooKeeperFullSpec, ZooKeeper>(NAMESPACE, CLUSTER_NAME)
                .invokeController(spec,
                        ZooKeeper.class,
                        ZooKeeperFullSpec.class,
                        ZooKeeperController.class);
    }

    @SneakyThrows
    private UpdateControl<ZooKeeper> invokeController(String spec, MockKubernetesClient client) {
        return new ControllerTestUtil<ZooKeeperFullSpec, ZooKeeper>(NAMESPACE, CLUSTER_NAME)
                .invokeController(client, spec,
                        ZooKeeper.class,
                        ZooKeeperFullSpec.class,
                        ZooKeeperController.class);
    }

    @SneakyThrows
    private void invokeController(ZooKeeper zooKeeper, MockKubernetesClient client) {
        new ControllerTestUtil<ZooKeeperFullSpec, ZooKeeper>(NAMESPACE, CLUSTER_NAME)
                .invokeController(client, zooKeeper, ZooKeeperController.class);
    }
}
