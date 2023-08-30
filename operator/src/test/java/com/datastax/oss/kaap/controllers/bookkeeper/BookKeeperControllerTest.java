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
package com.datastax.oss.kaap.controllers.bookkeeper;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.AbstractController;
import com.datastax.oss.kaap.controllers.ControllerTestUtil;
import com.datastax.oss.kaap.controllers.KubeTestUtil;
import com.datastax.oss.kaap.controllers.bookkeeper.racks.BookKeeperRackDaemon;
import com.datastax.oss.kaap.controllers.bookkeeper.racks.client.BkRackClient;
import com.datastax.oss.kaap.controllers.bookkeeper.racks.client.BkRackClientFactory;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeper;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperAutoRackConfig;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
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
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.client.KubernetesClient;
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
    static final String CLUSTER_NAME = "pulsar-spec-1";

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
                            component: bookkeeper
                            resource-set: bookkeeper
                          name: pulsar-spec-1-bookkeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: BookKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
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
                          PULSAR_PREFIX_zkServers: pulsar-spec-1-zookeeper-ca.ns.svc.cluster.local:2181
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
                    app: pulsar
                    cluster: pulsar-spec-1
                    component: bookkeeper
                    resource-set: bookkeeper
                  name: pulsar-spec-1-bookkeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
                    kind: BookKeeper
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-spec-1-cr
                spec:
                  clusterIP: None
                  ports:
                  - name: server
                    port: 3181
                  publishNotReadyAddresses: true
                  selector:
                    app: pulsar
                    cluster: pulsar-spec-1
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
                    app: pulsar
                    cluster: pulsar-spec-1
                    component: bookkeeper
                    resource-set: bookkeeper
                  name: pulsar-spec-1-bookkeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: kaap.oss.datastax.com/v1beta1
                    kind: BookKeeper
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
                      component: bookkeeper
                  serviceName: pulsar-spec-1-bookkeeper
                  template:
                    metadata:
                      annotations:
                        prometheus.io/port: 8000
                        prometheus.io/scrape: "true"
                      labels:
                        app: pulsar
                        cluster: pulsar-spec-1
                        component: bookkeeper
                        resource-set: bookkeeper
                    spec:
                      affinity:
                        podAntiAffinity:
                          requiredDuringSchedulingIgnoredDuringExecution:
                          - labelSelector:
                              matchLabels:
                                app: pulsar
                                cluster: pulsar-spec-1
                                component: bookkeeper
                            topologyKey: kubernetes.io/hostname
                      containers:
                      - args:
                        - "bin/apply-config-from-env.py conf/bookkeeper.conf && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar bookie"
                        command:
                        - sh
                        - -c
                        envFrom:
                        - configMapRef:
                            name: pulsar-spec-1-bookkeeper
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        livenessProbe:
                          httpGet:
                            path: /api/v1/bookie/is_ready
                            port: http
                          initialDelaySeconds: 10
                          periodSeconds: 30
                          timeoutSeconds: 5
                        name: pulsar-spec-1-bookkeeper
                        ports:
                        - containerPort: 3181
                          name: client
                        - containerPort: 8000
                          name: http
                        readinessProbe:
                          httpGet:
                            path: /api/v1/bookie/is_ready
                            port: http
                          initialDelaySeconds: 10
                          periodSeconds: 30
                          timeoutSeconds: 5
                        resources:
                          requests:
                            cpu: 1
                            memory: 2Gi
                        volumeMounts:
                        - mountPath: /pulsar/data/bookkeeper/journal
                          name: pulsar-spec-1-bookkeeper-journal
                        - mountPath: /pulsar/data/bookkeeper/ledgers
                          name: pulsar-spec-1-bookkeeper-ledgers
                      dnsConfig:
                        options:
                        - name: ndots
                          value: 4
                      initContainers:
                      - args:
                        - bin/apply-config-from-env.py conf/bookkeeper.conf && bin/bookkeeper shell metaformat --nonInteractive || true;
                        command:
                        - sh
                        - -c
                        envFrom:
                        - configMapRef:
                            name: pulsar-spec-1-bookkeeper
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        name: pulsar-spec-1-bookkeeper-metadata-format
                      securityContext:
                        fsGroup: 0
                      terminationGracePeriodSeconds: 60
                  updateStrategy:
                    type: RollingUpdate
                  volumeClaimTemplates:
                  - apiVersion: v1
                    kind: PersistentVolumeClaim
                    metadata:
                      labels:
                        app: pulsar
                        cluster: pulsar-spec-1
                        component: bookkeeper
                        resource-set: bookkeeper
                      name: pulsar-spec-1-bookkeeper-journal
                    spec:
                      accessModes:
                      - ReadWriteOnce
                      resources:
                        requests:
                          storage: 20Gi
                  - apiVersion: v1
                    kind: PersistentVolumeClaim
                    metadata:
                      labels:
                        app: pulsar
                        cluster: pulsar-spec-1
                        component: bookkeeper
                        resource-set: bookkeeper
                      name: pulsar-spec-1-bookkeeper-ledgers
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
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: bookkeeper
                            resource-set: bookkeeper
                          name: pulsar-spec-1-bookkeeper
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: BookKeeper
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          maxUnavailable: 1
                          selector:
                            matchLabels:
                              app: pulsar
                              cluster: pulsar-spec-1
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
        expectedData.put("PULSAR_PREFIX_useHostNameAsBookieID", "true");
        expectedData.put("PULSAR_PREFIX_httpServerEnabled", "true");
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
    public void testTlsEnabledOnBookKeeper() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    tls:
                        bookkeeper:
                            enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_autoRecoveryDaemonEnabled", "false");
        expectedData.put("PULSAR_PREFIX_useHostNameAsBookieID", "true");
        expectedData.put("PULSAR_PREFIX_httpServerEnabled", "true");
        expectedData.put("PULSAR_PREFIX_reppDnsResolverClass",
                "org.apache.pulsar.zookeeper.ZkBookieRackAffinityMapping");
        expectedData.put("BOOKIE_MEM",
                "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled "
                        + "-Dio.netty.recycler.linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        expectedData.put("BOOKIE_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_statsProviderClass",
                "org.apache.bookkeeper.stats.prometheus.PrometheusMetricsProvider");
        expectedData.put("PULSAR_PREFIX_zkServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("PULSAR_PREFIX_tlsProvider", "OpenSSL");
        expectedData.put("PULSAR_PREFIX_tlsProviderFactoryClass", "org.apache.bookkeeper.tls.TLSContextFactory");
        expectedData.put("PULSAR_PREFIX_tlsCertificatePath", "/pulsar/certs/tls.crt");
        expectedData.put("PULSAR_PREFIX_tlsKeyStoreType", "PEM");
        expectedData.put("PULSAR_PREFIX_tlsKeyStore", "/pulsar/tls-pk8.key");
        expectedData.put("PULSAR_PREFIX_tlsTrustStoreType", "PEM");
        expectedData.put("PULSAR_PREFIX_tlsHostnameVerificationEnabled", "true");
        expectedData.put("PULSAR_PREFIX_bookkeeperTLSClientAuthentication", "true");
        expectedData.put("PULSAR_PREFIX_bookkeeperTLSTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");


        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);

        final StatefulSet sts = client.getCreatedResource(StatefulSet.class).getResource();
        KubeTestUtil.assertTlsVolumesMounted(sts, GlobalSpec.DEFAULT_TLS_SECRET_NAME);
        final String stsCommand = sts.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getArgs().get(0);
        Assert.assertEquals(stsCommand, "bin/apply-config-from-env.py conf/bookkeeper.conf && openssl pkcs8 -topk8 "
                + "-inform PEM -outform PEM -in /pulsar/certs/tls.key -out /pulsar/tls-pk8.key -nocrypt && "
                + "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar bookie");
    }


    @Test
    public void testTlsEnabledOnZookeeper() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    tls:
                        zookeeper:
                            enabled: true
                        bookkeeper:
                            enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(client
                .getCreatedResource(StatefulSet.class)
                .getResource()
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getArgs()
                .get(0), """
                bin/apply-config-from-env.py conf/bookkeeper.conf && openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key -out /pulsar/tls-pk8.key -nocrypt && certconverter() {
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
                echo '' && OPTS="${OPTS} -Dlog4j2.formatMsgNoLookups=true" exec bin/pulsar bookie""");

        Assert.assertEquals(client
                .getCreatedResource(StatefulSet.class)
                .getResource()
                .getSpec()
                .getTemplate()
                .getSpec()
                .getInitContainers()
                .get(0)
                .getArgs()
                .get(0), """
                certconverter() {
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
                echo '' && bin/apply-config-from-env.py conf/bookkeeper.conf && bin/bookkeeper shell metaformat --nonInteractive || true;""");

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
                bookkeeper:
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
                        "component", "bookkeeper",
                        "resource-set", "bookkeeper",
                        "label-2", "label2-value"
                )
        );
        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class).getResource().getMetadata().getLabels(),
                Map.of(
                        "cluster", "pul",
                        "app", "pulsar",
                        "component", "bookkeeper",
                        "resource-set", "bookkeeper",
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
                bookkeeper:
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
                        "component", "bookkeeper",
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
                bookkeeper:
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
    public void testInitContainers() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                bookkeeper:
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
        Assert.assertEquals(initContainers.size(), 2);
        Assert.assertEquals(SerializationUtil.writeAsYaml(KubeTestUtil.getContainerByName(initContainers, "myinit")),
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
                bookkeeper:
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
                bookkeeper:
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
                bookkeeper:
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
            if (!"pul-bookkeeper-journal".equals(storageClass.getMetadata().getName())
                    && !"pul-bookkeeper-ledgers".equals(storageClass.getMetadata().getName())) {
                Assert.fail("unexpected storageClass " + storageClass.getMetadata().getName());
            }
            Assert.assertEquals(storageClass.getMetadata().getNamespace(), NAMESPACE);
            Assert.assertEquals(storageClass.getMetadata().getOwnerReferences().size(), 0);

            Assert.assertEquals(storageClass.getMetadata().getLabels().size(), 4);
            Assert.assertEquals(storageClass.getReclaimPolicy(), "Retain");
            Assert.assertEquals(storageClass.getProvisioner(), "kubernetes.io/aws-ebs");
            Assert.assertEquals(storageClass.getParameters().size(), 3);
            Assert.assertEquals(storageClass.getParameters().get("type"), "gp2");
            Assert.assertEquals(storageClass.getParameters().get("fsType"), "ext4");
            Assert.assertEquals(storageClass.getParameters().get("iopsPerGB"), "10");
        }
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
                bookkeeper:
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
                bookkeeper:
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
                "component", "bookkeeper"
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
                "component", "bookkeeper"
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
                "component", "bookkeeper"
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
                "component", "bookkeeper"
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
                "component", "bookkeeper"
        ));
    }


    @Test
    public void testProbe() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
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
                bookkeeper:
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
                bookkeeper:
                    probes:
                        liveness:
                            enabled: true
                            periodSeconds: 10
                        readiness:
                            enabled: true
                            periodSeconds: 11
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(StatefulSet.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertProbe(container.getLivenessProbe(), 5, 10, 10);
        assertProbe(container.getReadinessProbe(), 5, 10, 11);

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
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-bookkeeper");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        Assert.assertEquals(sts.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-bookkeeper"),
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
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-bookkeeper");
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
        Assert.assertEquals(probe.getHttpGet().getPort().getStrVal(), "http");
        Assert.assertEquals(probe.getHttpGet().getPath(), "/api/v1/bookie/is_ready");

        Assert.assertEquals((int) probe.getInitialDelaySeconds(), initial);
        Assert.assertEquals((int) probe.getTimeoutSeconds(), timeout);
        Assert.assertEquals((int) probe.getPeriodSeconds(), period);
    }


    @SneakyThrows
    private void invokeControllerAndAssertError(String spec, String expectedErrorMessage) {
        new ControllerTestUtil<BookKeeperFullSpec, BookKeeper>(NAMESPACE, CLUSTER_NAME, this::controllerConstructor)
                .invokeControllerAndAssertError(spec,
                        expectedErrorMessage,
                        BookKeeper.class,
                        BookKeeperFullSpec.class,
                        BookKeeperController.class);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return new ControllerTestUtil<>(NAMESPACE, CLUSTER_NAME, this::controllerConstructor)
                .invokeController(spec,
                        BookKeeper.class,
                        BookKeeperFullSpec.class,
                        BookKeeperController.class);
    }

    private AbstractController<BookKeeper> controllerConstructor(
            ControllerTestUtil<BookKeeperFullSpec, BookKeeper>.ControllerConstructorInput controllerConstructorInput) {
        return new BookKeeperController(controllerConstructorInput.getClient()) {
            @Override
            protected BookKeeperRackDaemon initBookKeeperRackDaemon(KubernetesClient client) {
                return new BookKeeperRackDaemon(
                        controllerConstructorInput.getClient(),
                        new BkRackClientFactory() {
                            @Override
                            public BkRackClient newBkRackClient(String namespace, BookKeeperFullSpec newSpec,
                                                                BookKeeperAutoRackConfig autoRackConfig) {
                                return new BkRackClient() {
                                    @Override
                                    public BookiesRackOp newBookiesRackOp() {
                                        return new BookiesRackOp() {
                                            @Override
                                            public BookiesRackConfiguration get() {
                                                return null;
                                            }

                                            @Override
                                            public void update(BookiesRackConfiguration newConfig) {

                                            }
                                        };
                                    }

                                    @Override
                                    public void close() throws Exception {
                                    }
                                };
                            }

                            @Override
                            public void close() throws Exception {
                            }
                        }
                );
            }
        };
    }
}
