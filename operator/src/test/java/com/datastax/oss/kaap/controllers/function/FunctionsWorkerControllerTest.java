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
package com.datastax.oss.kaap.controllers.function;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.ControllerTestUtil;
import com.datastax.oss.kaap.controllers.KubeTestUtil;
import com.datastax.oss.kaap.crds.function.FunctionsWorker;
import com.datastax.oss.kaap.crds.function.FunctionsWorkerFullSpec;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
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
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@JBossLog
public class FunctionsWorkerControllerTest {

    static final String NAMESPACE = "ns";
    static final String CLUSTER_NAME = "pulsar-spec-1";

    @Test
    public void testDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:2.10.2
                functionsWorker:
                    replicas: 2
                """;

        final MockKubernetesClient client = invokeController(spec);

        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).get(0).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ConfigMap
                        metadata:
                          labels:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: function
                          name: pulsar-spec-1-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        data:
                          functions_worker.yml: |
                            ---
                            assignmentWriteMaxRetries: 60
                            clusterCoordinationTopicName: coordinate
                            configurationStoreServers: pulsar-spec-1-zookeeper-ca.ns.svc.cluster.local:2181
                            connectorsDirectory: ./connectors
                            downloadDirectory: /tmp/pulsar_functions
                            failureCheckFreqMs: 30000
                            functionAssignmentTopicName: assignments
                            functionMetadataTopicName: metadata
                            functionRuntimeFactoryClassName: org.apache.pulsar.functions.runtime.process.ProcessRuntimeFactory
                            functionRuntimeFactoryConfigs: {}
                            includeStandardPrometheusMetrics: "true"
                            initialBrokerReconnectMaxRetries: 60
                            instanceLivenessCheckFreqMs: 30000
                            numFunctionPackageReplicas: 2
                            numHttpServerThreads: 16
                            pulsarFunctionsCluster: pulsar-spec-1
                            pulsarFunctionsNamespace: public/functions
                            pulsarServiceUrl: pulsar://pulsar-spec-1-broker.ns.svc.cluster.local:6650/
                            pulsarWebServiceUrl: http://pulsar-spec-1-broker.ns.svc.cluster.local:8080/
                            rescheduleTimeoutMs: 60000
                            schedulerClassName: org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler
                            topicCompactionFrequencySec: 1800
                            workerHostname: pulsar-spec-1-function
                            workerId: pulsar-spec-1-function
                            workerPort: 6750
                            zooKeeperSessionTimeoutMillis: 30000
                            zookeeperServers: pulsar-spec-1-zookeeper-ca.ns.svc.cluster.local:2181
                                                """);

        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).get(1).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ConfigMap
                        metadata:
                          labels:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: function
                          name: pulsar-spec-1-function-extra
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        data:
                          PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_MEM: -XX:+ExitOnOutOfMemoryError
                                                """);

        Assert.assertEquals(client.getCreatedResources(Service.class).get(0).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: Service
                        metadata:
                          labels:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: function
                          name: pulsar-spec-1-function-ca
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          ports:
                          - name: http
                            port: 6750
                          - name: https
                            port: 6751
                          selector:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: function
                        """);

        Assert.assertEquals(client.getCreatedResources(Service.class).get(1).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: Service
                        metadata:
                          labels:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: function
                          name: pulsar-spec-1-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          clusterIP: None
                          ports:
                          - name: http
                            port: 6750
                          - name: https
                            port: 6751
                          selector:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: function
                          type: ClusterIP
                        """);

        Assert.assertEquals(client.getCreatedResource(StatefulSet.class).getResourceYaml(),
                """
                        ---
                        apiVersion: apps/v1
                        kind: StatefulSet
                        metadata:
                          labels:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: function
                          name: pulsar-spec-1-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          podManagementPolicy: Parallel
                          replicas: 2
                          selector:
                            matchLabels:
                              app: pulsar
                              cluster: pulsar-spec-1
                              component: function
                          serviceName: pulsar-spec-1-function
                          template:
                            metadata:
                              annotations:
                                prometheus.io/port: 8080
                                prometheus.io/scrape: "true"
                              labels:
                                app: pulsar
                                cluster: pulsar-spec-1
                                component: function
                            spec:
                              affinity:
                                podAntiAffinity:
                                  requiredDuringSchedulingIgnoredDuringExecution:
                                  - labelSelector:
                                      matchLabels:
                                        app: pulsar
                                        cluster: pulsar-spec-1
                                        component: function
                                    topologyKey: kubernetes.io/hostname
                              containers:
                              - args:
                                - "bin/apply-config-from-env.py conf/broker.conf && cp -f funcconf/functions_worker.yml conf/functions_worker.yml && export PF_workerHostname=\\"${workerHostname}.pulsar-spec-1-function\\" && bin/gen-yml-from-env.py conf/functions_worker.yml && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar functions-worker"
                                command:
                                - sh
                                - -c
                                env:
                                - name: workerHostname
                                  valueFrom:
                                    fieldRef:
                                      fieldPath: metadata.name
                                - name: PF_workerId
                                  valueFrom:
                                    fieldRef:
                                      fieldPath: metadata.name
                                envFrom:
                                - configMapRef:
                                    name: pulsar-spec-1-function-extra
                                image: apachepulsar/pulsar:2.10.2
                                imagePullPolicy: IfNotPresent
                                livenessProbe:
                                  exec:
                                    command:
                                    - sh
                                    - -c
                                    - curl -s --max-time 5 --fail  http://localhost:6750/metrics/ > /dev/null
                                  initialDelaySeconds: 10
                                  periodSeconds: 30
                                  timeoutSeconds: 5
                                name: pulsar-spec-1-function
                                ports:
                                - containerPort: 6750
                                  name: http
                                - containerPort: 6751
                                  name: https
                                readinessProbe:
                                  initialDelaySeconds: 10
                                  periodSeconds: 30
                                  tcpSocket:
                                    port: 6750
                                  timeoutSeconds: 5
                                resources:
                                  requests:
                                    cpu: 1
                                    memory: 4Gi
                                volumeMounts:
                                - mountPath: /pulsar/funcconf/functions_worker.yml
                                  name: config-volume
                                  subPath: functions_worker.yml
                                - mountPath: /pulsar/logs
                                  name: pulsar-spec-1-function-logs
                              dnsConfig:
                                options:
                                - name: ndots
                                  value: 4
                              securityContext:
                                fsGroup: 0
                              serviceAccountName: pulsar-spec-1-function
                              terminationGracePeriodSeconds: 60
                              volumes:
                              - configMap:
                                  name: pulsar-spec-1-function
                                name: config-volume
                          updateStrategy:
                            type: RollingUpdate
                          volumeClaimTemplates:
                          - apiVersion: v1
                            kind: PersistentVolumeClaim
                            metadata:
                              labels:
                                app: pulsar
                                cluster: pulsar-spec-1
                                component: function
                              name: pulsar-spec-1-function-logs
                            spec:
                              accessModes:
                              - ReadWriteOnce
                              resources:
                                requests:
                                  storage: 5Gi
                        """);

        Assert.assertEquals(client.getCreatedResource(PodDisruptionBudget.class).getResourceYaml(),
                """
                        ---
                        apiVersion: policy/v1
                        kind: PodDisruptionBudget
                        metadata:
                          labels:
                            app: pulsar
                            cluster: pulsar-spec-1
                            component: function
                          name: pulsar-spec-1-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        spec:
                          maxUnavailable: 1
                          selector:
                            matchLabels:
                              app: pulsar
                              cluster: pulsar-spec-1
                              component: function
                        """);

        Assert.assertEquals(client.getCreatedResource(ServiceAccount.class).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ServiceAccount
                        metadata:
                          name: pulsar-spec-1-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        """);
        Assert.assertEquals(client.getCreatedResource(Role.class).getResourceYaml(),
                """
                        ---
                        apiVersion: rbac.authorization.k8s.io/v1
                        kind: Role
                        metadata:
                          name: pulsar-spec-1-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        rules:
                        - apiGroups:
                          - ""
                          resources:
                          - pods
                          verbs:
                          - list
                        - apiGroups:
                          - ""
                          resources:
                          - secrets
                          verbs:
                          - '*'
                        - apiGroups:
                          - ""
                          resources:
                          - services
                          verbs:
                          - get
                          - create
                          - delete
                        - apiGroups:
                          - apps
                          resources:
                          - statefulsets
                          verbs:
                          - get
                          - create
                          - delete
                        """);
        Assert.assertEquals(client.getCreatedResource(RoleBinding.class).getResourceYaml(),
                """
                        ---
                        apiVersion: rbac.authorization.k8s.io/v1
                        kind: RoleBinding
                        metadata:
                          name: pulsar-spec-1-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        roleRef:
                          kind: Role
                          name: pulsar-spec-1-function
                        subjects:
                        - kind: ServiceAccount
                          name: pulsar-spec-1-function
                          namespace: ns
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
                    image: apachepulsar/pulsar:fn
                    imagePullPolicy: Always
                """;
        client = invokeController(spec);
        createdResource =
                client.getCreatedResource(StatefulSet.class);


        Assert.assertEquals(createdResource.getResource().getSpec()
                .getTemplate()
                .getSpec().getContainers().get(0)
                .getImage(), "apachepulsar/pulsar:fn");
        Assert.assertEquals(createdResource.getResource().getSpec()
                .getTemplate()
                .getSpec().getContainers().get(0)
                .getImagePullPolicy(), "Always");

    }


    @Test
    public void testTlsEnabledOnFunctionsWorker() throws Exception {
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
                        functionsWorker:
                            enabled: true
                            enabledWithBroker: true
                functionsWorker:
                    replicas: 1
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);
        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("zooKeeperSessionTimeoutMillis", 30000);
        expectedData.put("pulsarFunctionsCluster", "pul");
        expectedData.put("workerId", "pul-function");
        expectedData.put("workerHostname", "pul-function");
        expectedData.put("workerPort", 6750);

        expectedData.put("pulsarServiceUrl", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedData.put("pulsarWebServiceUrl", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedData.put("downloadDirectory", "/tmp/pulsar_functions");
        expectedData.put("pulsarFunctionsNamespace", "public/functions");
        expectedData.put("functionMetadataTopicName", "metadata");
        expectedData.put("clusterCoordinationTopicName", "coordinate");
        expectedData.put("numHttpServerThreads", 16);
        expectedData.put("schedulerClassName", "org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler");
        expectedData.put("functionAssignmentTopicName", "assignments");
        expectedData.put("failureCheckFreqMs", 30000);
        expectedData.put("rescheduleTimeoutMs", 60000);
        expectedData.put("initialBrokerReconnectMaxRetries", 60);
        expectedData.put("assignmentWriteMaxRetries", 60);
        expectedData.put("instanceLivenessCheckFreqMs", 30000);
        expectedData.put("topicCompactionFrequencySec", 1800);
        expectedData.put("includeStandardPrometheusMetrics", "true");
        expectedData.put("connectorsDirectory", "./connectors");
        expectedData.put("numFunctionPackageReplicas", 2);
        expectedData.put("functionRuntimeFactoryClassName",
                "org.apache.pulsar.functions.runtime.process.ProcessRuntimeFactory");
        expectedData.put("functionRuntimeFactoryConfigs", Map.of());
        expectedData.put("tlsEnabled", "true");
        expectedData.put("workerPortTls", 6751);
        expectedData.put("tlsCertificateFilePath", "/pulsar/certs/tls.crt");
        expectedData.put("tlsTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("brokerClientTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("tlsKeyFilePath", "/pulsar/tls-pk8.key");
        expectedData.put("useTls", "true");
        expectedData.put("tlsEnabledWithKeyStore", "true");
        expectedData.put("tlsKeyStore", "/pulsar/tls.keystore.jks");
        expectedData.put("tlsTrustStore", "/pulsar/tls.truststore.jks");
        expectedData.put("tlsEnableHostnameVerification", "true");

        final Map<String, Object> data = (Map<String, Object>) SerializationUtil
                .readYaml(createdResource.getResource().getData().get("functions_worker.yml"), Map.class);
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
                        broker:
                            enabled: true
                        bookkeeper:
                            enabled: true
                        functionsWorker:
                            enabled: true
                            enabledWithBroker: true
                functionsWorker:
                    replicas: 1
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);
        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("zooKeeperSessionTimeoutMillis", 30000);
        expectedData.put("pulsarFunctionsCluster", "pul");
        expectedData.put("workerId", "pul-function");
        expectedData.put("workerHostname", "pul-function");
        expectedData.put("workerPort", 6750);

        expectedData.put("pulsarServiceUrl", "pulsar+ssl://pul-broker.ns.svc.cluster.local:6651/");
        expectedData.put("pulsarWebServiceUrl", "https://pul-broker.ns.svc.cluster.local:8443/");
        expectedData.put("downloadDirectory", "/tmp/pulsar_functions");
        expectedData.put("pulsarFunctionsNamespace", "public/functions");
        expectedData.put("functionMetadataTopicName", "metadata");
        expectedData.put("clusterCoordinationTopicName", "coordinate");
        expectedData.put("numHttpServerThreads", 16);
        expectedData.put("schedulerClassName", "org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler");
        expectedData.put("functionAssignmentTopicName", "assignments");
        expectedData.put("failureCheckFreqMs", 30000);
        expectedData.put("rescheduleTimeoutMs", 60000);
        expectedData.put("initialBrokerReconnectMaxRetries", 60);
        expectedData.put("assignmentWriteMaxRetries", 60);
        expectedData.put("instanceLivenessCheckFreqMs", 30000);
        expectedData.put("topicCompactionFrequencySec", 1800);
        expectedData.put("includeStandardPrometheusMetrics", "true");
        expectedData.put("connectorsDirectory", "./connectors");
        expectedData.put("numFunctionPackageReplicas", 2);
        expectedData.put("functionRuntimeFactoryClassName",
                "org.apache.pulsar.functions.runtime.process.ProcessRuntimeFactory");
        expectedData.put("functionRuntimeFactoryConfigs", Map.of());
        expectedData.put("tlsEnabled", "true");
        expectedData.put("workerPortTls", 6751);
        expectedData.put("tlsCertificateFilePath", "/pulsar/certs/tls.crt");
        expectedData.put("tlsTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("brokerClientTrustCertsFilePath", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("tlsKeyFilePath", "/pulsar/tls-pk8.key");
        expectedData.put("useTls", "true");
        expectedData.put("tlsEnabledWithKeyStore", "true");
        expectedData.put("tlsKeyStore", "/pulsar/tls.keystore.jks");
        expectedData.put("tlsTrustStore", "/pulsar/tls.truststore.jks");
        expectedData.put("tlsEnableHostnameVerification", "true");
        expectedData.put("bookkeeperTLSClientAuthentication", "true");

        final Map<String, Object> data = (Map<String, Object>) SerializationUtil
                .readYaml(createdResource.getResource().getData().get("functions_worker.yml"), Map.class);
        Assert.assertEquals(data, expectedData);
    }

    @Test
    public void testCerts() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    tls:
                        broker:
                            enabled: true
                        functionsWorker:
                            enabled: false
                            enabledWithBroker: true
                functionsWorker:
                    replicas: 1
                """;
        MockKubernetesClient client = invokeController(spec);
        KubeTestUtil.assertTlsVolumesMounted(
                client.getCreatedResource(StatefulSet.class).getResource(), "pulsar-tls");

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    tls:
                        broker:
                            enabled: false
                        functionsWorker:
                            enabled: true
                functionsWorker:
                    replicas: 1
                """;
        client = invokeController(spec);
        KubeTestUtil.assertTlsVolumesMounted(
                client.getCreatedResource(StatefulSet.class).getResource(), "pulsar-tls");


        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    tls:
                        broker:
                            enabled: false
                        functionsWorker:
                            enabled: false
                functionsWorker:
                    replicas: 1
                """;
        client = invokeController(spec);
        Assert.assertNull(KubeTestUtil.getVolumeByName(client
                .getCreatedResource(StatefulSet.class).getResource().getSpec().getTemplate().getSpec()
                        .getVolumes(), "pulsar-tls"));

    }




    @Test
    public void testReplicas() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 5
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);


        Assert.assertEquals((int) createdResource.getResource().getSpec().getReplicas(), 5);
    }


    @Test
    public void testConfig() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 2
                    config:
                        numFunctionPackageReplicas: 5
                        myconfig: myvalue
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResources(ConfigMap.class).get(0);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("zooKeeperSessionTimeoutMillis", 30000);
        expectedData.put("pulsarFunctionsCluster", "pul");
        expectedData.put("workerId", "pul-function");
        expectedData.put("workerHostname", "pul-function");
        expectedData.put("workerPort", 6750);

        expectedData.put("pulsarServiceUrl", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("pulsarWebServiceUrl", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("downloadDirectory", "/tmp/pulsar_functions");
        expectedData.put("pulsarFunctionsNamespace", "public/functions");
        expectedData.put("functionMetadataTopicName", "metadata");
        expectedData.put("clusterCoordinationTopicName", "coordinate");
        expectedData.put("numHttpServerThreads", 16);
        expectedData.put("schedulerClassName", "org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler");
        expectedData.put("functionAssignmentTopicName", "assignments");
        expectedData.put("failureCheckFreqMs", 30000);
        expectedData.put("rescheduleTimeoutMs", 60000);
        expectedData.put("initialBrokerReconnectMaxRetries", 60);
        expectedData.put("assignmentWriteMaxRetries", 60);
        expectedData.put("instanceLivenessCheckFreqMs", 30000);
        expectedData.put("topicCompactionFrequencySec", 1800);
        expectedData.put("includeStandardPrometheusMetrics", "true");
        expectedData.put("connectorsDirectory", "./connectors");
        expectedData.put("numFunctionPackageReplicas", 5);
        expectedData.put("myconfig", "myvalue");
        expectedData.put("functionRuntimeFactoryClassName",
                "org.apache.pulsar.functions.runtime.process.ProcessRuntimeFactory");
        expectedData.put("functionRuntimeFactoryConfigs", Map.of());

        final Map<String, Object> data = (Map<String, Object>) SerializationUtil
                .readYaml(createdResource.getResource().getData().get("functions_worker.yml"), Map.class);
        Assert.assertEquals(data, expectedData);
    }

    @Test
    public void testExtraConfigMap() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 2
                    config:
                        PULSAR_LOG_ROOT_LEVEL: debug
                        PULSAR_EXTRA_CLASSPATH: "/pulsar/custom-classpath"
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResources(ConfigMap.class).get(1);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("PULSAR_MEM", "-XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "debug");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_EXTRA_CLASSPATH", "/pulsar/custom-classpath");

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
                functionsWorker:
                    replicas: 1
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("zooKeeperSessionTimeoutMillis", 30000);
        expectedData.put("pulsarFunctionsCluster", "pul");
        expectedData.put("workerId", "pul-function");
        expectedData.put("workerHostname", "pul-function");
        expectedData.put("workerPort", 6750);

        expectedData.put("pulsarServiceUrl", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("pulsarWebServiceUrl", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("downloadDirectory", "/tmp/pulsar_functions");
        expectedData.put("pulsarFunctionsNamespace", "public/functions");
        expectedData.put("functionMetadataTopicName", "metadata");
        expectedData.put("clusterCoordinationTopicName", "coordinate");
        expectedData.put("numHttpServerThreads", 16);
        expectedData.put("schedulerClassName", "org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler");
        expectedData.put("functionAssignmentTopicName", "assignments");
        expectedData.put("failureCheckFreqMs", 30000);
        expectedData.put("rescheduleTimeoutMs", 60000);
        expectedData.put("initialBrokerReconnectMaxRetries", 60);
        expectedData.put("assignmentWriteMaxRetries", 60);
        expectedData.put("instanceLivenessCheckFreqMs", 30000);
        expectedData.put("topicCompactionFrequencySec", 1800);
        expectedData.put("includeStandardPrometheusMetrics", "true");
        expectedData.put("connectorsDirectory", "./connectors");
        expectedData.put("numFunctionPackageReplicas", 2);
        expectedData.put("functionRuntimeFactoryClassName",
                "org.apache.pulsar.functions.runtime.process.ProcessRuntimeFactory");
        expectedData.put("functionRuntimeFactoryConfigs", Map.of());
        expectedData.put("authenticationEnabled", "true");
        expectedData.put("authenticationProviders",
                List.of("org.apache.pulsar.broker.authentication.AuthenticationProviderToken",
                        "org.apache.pulsar.broker.authentication.AuthenticationProviderTls"));
        expectedData.put("authorizationEnabled", "true");
        expectedData.put("authorizationProvider", "org.apache.pulsar.broker.authorization.PulsarAuthorizationProvider");
        expectedData.put("clientAuthenticationPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
        expectedData.put("clientAuthenticationParameters", "file:///pulsar/token-superuser/superuser.jwt");
        expectedData.put("proxyRoles", List.of("proxy"));
        expectedData.put("superUserRoles", List.of("admin", "proxy", "superuser", "websocket"));
        expectedData.put("properties", Map.of(
                "tokenPublicKey", "file:///pulsar/token-public-key/my-public.key")
        );

        final Map<String, Object> data = (Map<String, Object>) SerializationUtil
                .readYaml(client.getCreatedResources(ConfigMap.class).get(0).getResource().getData()
                        .get("functions_worker.yml"), Map.class);
        Assert.assertEquals(data, expectedData);


        Map<String, Object> expectedDataForExtra = new HashMap<>();
        expectedDataForExtra.put("PULSAR_MEM", "-XX:+ExitOnOutOfMemoryError");
        expectedDataForExtra.put("PULSAR_LOG_LEVEL", "info");
        expectedDataForExtra.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedDataForExtra.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedDataForExtra.put("PULSAR_PREFIX_authorizationEnabled", "true");
        expectedDataForExtra.put("PULSAR_PREFIX_authenticationEnabled", "true");
        expectedDataForExtra.put("PULSAR_PREFIX_brokerClientAuthenticationPlugin",
                "org.apache.pulsar.client.impl.auth.AuthenticationToken");
        expectedDataForExtra.put("PULSAR_PREFIX_brokerClientAuthenticationParameters",
                "file:///pulsar/token-superuser/superuser.jwt");
        expectedDataForExtra.put("PULSAR_PREFIX_superUserRoles", "admin,proxy,superuser,websocket");
        expectedDataForExtra.put("PULSAR_PREFIX_tokenPublicKey", "file:///pulsar/token-public-key/my-public.key");
        expectedDataForExtra.put("PULSAR_PREFIX_authenticationProviders",
                "org.apache.pulsar.broker.authentication.AuthenticationProviderToken,"
                        + "org.apache.pulsar.broker.authentication.AuthenticationProviderTls");

        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).get(1).getResource().getData(),
                expectedDataForExtra);

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
                + "/pulsar/token-superuser/superuser.jwt | tr -d '\\r')\" http://localhost:6750/metrics/ > /dev/null");
    }


    @Test
    public void testRuntimeK8s() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
                    runtime: kubernetes
                    config:
                        functionInstanceMinResources:
                            cpu: 0.5
                            ram: 100000000
                            disk: 500000000
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResources(ConfigMap.class).get(0);

        Map<String, Object> expectedData = new HashMap<>();
        expectedData.put("configurationStoreServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("zookeeperServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("zooKeeperSessionTimeoutMillis", 30000);
        expectedData.put("pulsarFunctionsCluster", "pul");
        expectedData.put("workerId", "pul-function");
        expectedData.put("workerHostname", "pul-function");
        expectedData.put("workerPort", 6750);

        expectedData.put("pulsarServiceUrl", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        expectedData.put("pulsarWebServiceUrl", "http://pul-broker.ns.svc.cluster.local:8080/");
        expectedData.put("downloadDirectory", "/tmp/pulsar_functions");
        expectedData.put("pulsarFunctionsNamespace", "public/functions");
        expectedData.put("functionMetadataTopicName", "metadata");
        expectedData.put("clusterCoordinationTopicName", "coordinate");
        expectedData.put("numHttpServerThreads", 16);
        expectedData.put("schedulerClassName", "org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler");
        expectedData.put("functionAssignmentTopicName", "assignments");
        expectedData.put("failureCheckFreqMs", 30000);
        expectedData.put("rescheduleTimeoutMs", 60000);
        expectedData.put("initialBrokerReconnectMaxRetries", 60);
        expectedData.put("assignmentWriteMaxRetries", 60);
        expectedData.put("instanceLivenessCheckFreqMs", 30000);
        expectedData.put("topicCompactionFrequencySec", 1800);
        expectedData.put("includeStandardPrometheusMetrics", "true");
        expectedData.put("connectorsDirectory", "./connectors");
        expectedData.put("numFunctionPackageReplicas", 2);
        expectedData.put("functionRuntimeFactoryClassName",
                "org.apache.pulsar.functions.runtime.kubernetes.KubernetesRuntimeFactory");
        LinkedHashMap<String, Object> functionRuntimeFactoryConfigs = new LinkedHashMap<>();
        functionRuntimeFactoryConfigs.put("jobNamespace", "ns");
        functionRuntimeFactoryConfigs.put("percentMemoryPadding", 10);
        functionRuntimeFactoryConfigs.put("pulsarAdminUrl", "http://pul-function-ca.ns.svc.cluster.local:6750");
        functionRuntimeFactoryConfigs.put("pulsarDockerImageName", "apachepulsar/pulsar:global");
        functionRuntimeFactoryConfigs.put("pulsarRootDir", "/pulsar");
        functionRuntimeFactoryConfigs.put("pulsarServiceUrl", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        functionRuntimeFactoryConfigs.put("submittingInsidePod", true);
        expectedData.put("functionRuntimeFactoryConfigs", functionRuntimeFactoryConfigs);
        LinkedHashMap<String, Object> functionInstanceMinResources = new LinkedHashMap<>();
        functionInstanceMinResources.put("cpu", 0.5d);
        functionInstanceMinResources.put("disk", 500000000);
        functionInstanceMinResources.put("ram", 100000000);
        expectedData.put("functionInstanceMinResources", functionInstanceMinResources);

        final Map<String, Object> data = (Map<String, Object>) SerializationUtil
                .readYaml(createdResource.getResource().getData().get("functions_worker.yml"), Map.class);
        Assert.assertEquals(data, expectedData);
    }

    @Test
    public void testUpdateStrategy() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
                    rbac: 
                        create: false
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
                functionsWorker:
                    replicas: 1
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
                        "component", "function",
                        "label-2", "label2-value"
                )
        );
        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class).getResource().getMetadata().getLabels(),
                Map.of(
                        "cluster", "pul",
                        "app", "pulsar",
                        "component", "function",
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
                functionsWorker:
                    replicas: 1
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
                        "component", "function",
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
                functionsWorker:
                    replicas: 1
                    env:
                    - name: env1
                      value: env1-value
                """;
        MockKubernetesClient client = invokeController(spec);

        List<EnvVar> expectedEnv = new ArrayList<>();
        expectedEnv.add(new EnvVar("env1", "env1-value", null));
        expectedEnv.add(new EnvVarBuilder()
                .withName(FunctionsWorkerResourcesFactory.ENV_WORKER_HOSTNAME)
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath("metadata.name")
                .endFieldRef()
                .endValueFrom()
                .build());
        expectedEnv.add(new EnvVarBuilder()
                .withName(FunctionsWorkerResourcesFactory.ENV_WORKER_ID)
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath("metadata.name")
                .endFieldRef()
                .endValueFrom()
                .build());

        Assert.assertEquals(
                client.getCreatedResource(StatefulSet.class)
                        .getResource().getSpec().getTemplate().getSpec().getContainers().get(0)
                        .getEnv(), expectedEnv);
    }


    @Test
    public void testInitContainers() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
                    gracePeriod: -1
                """;
        invokeControllerAndAssertError(spec, "invalid configuration property \"functionsWorker.gracePeriod\" for value "
                + "\"-1\": must be greater than or equal to 0");

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                "component", "function"
        ));

        spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                    antiAffinity:
                        host:
                            required: false
                functionsWorker:
                    replicas: 1
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
                "component", "function"
        ));

        spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                    antiAffinity:
                        host:
                            enabled: false
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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
                "component", "function"
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
                functionsWorker:
                    replicas: 1
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
                "component", "function"
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
                "component", "function"
        ));
    }


    @Test
    public void testProbe() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
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

        assertLivenessProbe(container.getLivenessProbe(), 5, 10, 30);
        assertReadinessProbe(container.getReadinessProbe(), 5, 10, 30);


        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
                    probes:
                        liveness:
                            enabled: true
                            periodSeconds: 12
                        readiness:
                            enabled: true
                            periodSeconds: 11
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(StatefulSet.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertLivenessProbe(container.getLivenessProbe(), 5, 10, 12);
        assertReadinessProbe(container.getReadinessProbe(), 5, 10, 11);

    }

    private void assertLivenessProbe(Probe probe, int timeout, int initial, int period) {
        Assert.assertEquals(probe.getExec().getCommand(),
                List.of("sh", "-c",
                        "curl -s --max-time %d --fail  http://localhost:6750/metrics/ > /dev/null".formatted(timeout)));

        Assert.assertEquals((int) probe.getInitialDelaySeconds(), initial);
        Assert.assertEquals((int) probe.getTimeoutSeconds(), timeout);
        Assert.assertEquals((int) probe.getPeriodSeconds(), period);
    }

    private void assertReadinessProbe(Probe probe, int timeout, int initial, int period) {
        Assert.assertEquals(probe.getTcpSocket().getPort().getIntVal().intValue(), 6750);

        Assert.assertEquals((int) probe.getInitialDelaySeconds(), initial);
        Assert.assertEquals((int) probe.getTimeoutSeconds(), timeout);
        Assert.assertEquals((int) probe.getPeriodSeconds(), period);
    }


    @Test
    public void testLogsVolumeNoPersistence() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
                    logsVolume:
                        name: fnlogs
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        final PodSpec podSpec = createdResource.getResource().getSpec().getTemplate()
                .getSpec();
        final Container container = podSpec.getContainers().get(0);

        Assert.assertEquals(
                KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-function-fnlogs").getMountPath(),
                "/pulsar/logs");
        Assert.assertNotNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-function-fnlogs").getEmptyDir());
        Assert.assertNull(client.getCreatedResource(StorageClass.class));

    }

    @Test
    public void testDataVolumePersistenceDefaultExistingStorageClass() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: true
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
                    logsVolume:
                        size: 1Gi
                        existingStorageClassName: default
                """;

        MockKubernetesClient client = invokeController(spec);


        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);

        final PodSpec podSpec = createdResource.getResource().getSpec().getTemplate()
                .getSpec();
        final Container container = podSpec.getContainers().get(0);

        Assert.assertEquals(
                KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-function-logs").getMountPath(),
                "/pulsar/logs");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-zookeeper-logs"));

        final PersistentVolumeClaim persistentVolumeClaim = createdResource.getResource().getSpec()
                .getVolumeClaimTemplates().get(0);
        Assert.assertEquals(persistentVolumeClaim.getMetadata().getName(), "pul-function-logs");

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
                functionsWorker:
                    replicas: 1
                    logsVolume:
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
                functionsWorker:
                    replicas: 1
                    logsVolume:
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

        Assert.assertEquals(
                KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-function-logs").getMountPath(),
                "/pulsar/logs");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-function-logs"));

        final PersistentVolumeClaim persistentVolumeClaim = createdResource.getResource().getSpec()
                .getVolumeClaimTemplates().get(0);
        Assert.assertEquals(persistentVolumeClaim.getMetadata().getName(), "pul-function-logs");

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
                functionsWorker:
                    replicas: 1
                    logsVolume:
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
                functionsWorker:
                    replicas: 1
                    logsVolume:
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

        Assert.assertEquals(
                KubeTestUtil.getVolumeMountByName(container.getVolumeMounts(), "pul-function-logs").getMountPath(),
                "/pulsar/logs");
        Assert.assertNull(KubeTestUtil.getVolumeByName(podSpec.getVolumes(), "pul-function-logs"));

        final PersistentVolumeClaim persistentVolumeClaim = createdResource.getResource().getSpec()
                .getVolumeClaimTemplates().get(0);
        Assert.assertEquals(persistentVolumeClaim.getMetadata().getName(), "pul-function-logs");

        Assert.assertEquals(persistentVolumeClaim.getSpec().getAccessModes(), List.of("ReadWriteOnce"));
        Assert.assertEquals(persistentVolumeClaim.getSpec().getResources().getRequests().get("storage"),
                Quantity.parse("1Gi"));
        Assert.assertEquals(persistentVolumeClaim.getSpec().getStorageClassName(), "pul-function-logs");


        final MockKubernetesClient.ResourceInteraction<StorageClass> createdStorageClass =
                client.getCreatedResource(StorageClass.class);

        final StorageClass storageClass = createdStorageClass.getResource();
        Assert.assertEquals(storageClass.getMetadata().getName(), "pul-function-logs");
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
                functionsWorker:
                    replicas: 1
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
            if ("pul-function-ca".equals(metadata.getName())) {
                isCaService = true;
            } else if (!"pul-function".equals(metadata.getName())) {
                Assert.fail("unexpected service " + metadata.getName());
            }
            Assert.assertEquals(metadata.getNamespace(), NAMESPACE);

            final List<ServicePort> ports = service.getResource().getSpec().getPorts();
            Assert.assertEquals(ports.size(), 3);
            for (ServicePort port : ports) {
                switch (port.getName()) {
                    case "http":
                        Assert.assertEquals((int) port.getPort(), 6750);
                        break;
                    case "https":
                        Assert.assertEquals((int) port.getPort(), 6751);
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
                Assert.assertNull(service.getResource().getSpec().getType());
            } else {
                Assert.assertEquals(annotations.size(), 0);

                Assert.assertEquals(service.getResource().getSpec().getClusterIP(), "None");
                Assert.assertEquals(service.getResource().getSpec().getType(), "ClusterIP");

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
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
                """;

        MockKubernetesClient client = invokeController(spec);


        StatefulSet sts = client.getCreatedResource(StatefulSet.class).getResource();
        final String checksum1 = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-function");
        Assert.assertNotNull(checksum1);

        final String checksum1extra = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-function-extra");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        Assert.assertEquals(sts.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-function"),
                checksum1);
        Assert.assertEquals(sts.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-function-extra"),
                checksum1extra);

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    restartOnConfigMapChange: true
                functionsWorker:
                    replicas: 1
                    config:
                        PULSAR_ROOT_LOG_LEVEL: debug
                        numHttpServerThreads: 8
                """;

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        final String checksum2 = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-function");
        Assert.assertNotNull(checksum2);
        Assert.assertNotEquals(checksum1, checksum2);

        final String checksum2extra = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("kaap.oss.datastax.com/configmap-pul-function-extra");
        Assert.assertNotNull(checksum2extra);
        Assert.assertNotEquals(checksum1extra, checksum2extra);
    }

    @Test
    public void testRbac() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    rbac:
                        create: false
                """;

        MockKubernetesClient client = invokeController(spec);
        Assert.assertNull(client.getCreatedResource(Role.class));
        Assert.assertNull(client.getCreatedResource(RoleBinding.class));
        Assert.assertNull(client.getCreatedResource(ServiceAccount.class));
        Assert.assertNull(client.getCreatedResource(ClusterRole.class));
        Assert.assertNull(client.getCreatedResource(ClusterRoleBinding.class));

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
                    rbac:
                        namespaced: false
                """;

        client = invokeController(spec);
        Assert.assertNull(client.getCreatedResource(Role.class));
        Assert.assertNull(client.getCreatedResource(RoleBinding.class));

        Assert.assertEquals(client.getCreatedResource(ServiceAccount.class).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ServiceAccount
                        metadata:
                          name: pul-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        """);
        Assert.assertEquals(client.getCreatedResource(ClusterRole.class).getResourceYaml(),
                """
                        ---
                        apiVersion: rbac.authorization.k8s.io/v1
                        kind: ClusterRole
                        metadata:
                          name: pul-function
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        rules:
                        - apiGroups:
                          - ""
                          resources:
                          - pods
                          verbs:
                          - list
                        - apiGroups:
                          - ""
                          resources:
                          - secrets
                          verbs:
                          - '*'
                        - apiGroups:
                          - ""
                          resources:
                          - services
                          verbs:
                          - get
                          - create
                          - delete
                        - apiGroups:
                          - apps
                          resources:
                          - statefulsets
                          verbs:
                          - get
                          - create
                          - delete
                        """);
        Assert.assertEquals(client.getCreatedResource(ClusterRoleBinding.class).getResourceYaml(),
                """
                        ---
                        apiVersion: rbac.authorization.k8s.io/v1
                        kind: ClusterRoleBinding
                        metadata:
                          name: pul-function
                          ownerReferences:
                          - apiVersion: kaap.oss.datastax.com/v1beta1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsar-spec-1-cr
                        roleRef:
                          kind: ClusterRole
                          name: pul-function
                        subjects:
                        - kind: ServiceAccount
                          name: pul-function
                          namespace: ns
                          """);
    }

    @Test
    public void testAdditionalVolumes() throws Exception {

        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
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
        new ControllerTestUtil<FunctionsWorkerFullSpec, FunctionsWorker>(NAMESPACE, CLUSTER_NAME)
                .invokeControllerAndAssertError(spec,
                        expectedErrorMessage,
                        FunctionsWorker.class,
                        FunctionsWorkerFullSpec.class,
                        FunctionsWorkerController.class);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return new ControllerTestUtil<FunctionsWorkerFullSpec, FunctionsWorker>(NAMESPACE, CLUSTER_NAME)
                .invokeController(spec,
                        FunctionsWorker.class,
                        FunctionsWorkerFullSpec.class,
                        FunctionsWorkerController.class);
    }

    @SneakyThrows
    private UpdateControl<FunctionsWorker> invokeController(MockKubernetesClient client, String spec) {
        return new ControllerTestUtil<FunctionsWorkerFullSpec, FunctionsWorker>(NAMESPACE, CLUSTER_NAME)
                .invokeController(client, spec,
                        FunctionsWorker.class,
                        FunctionsWorkerFullSpec.class,
                        FunctionsWorkerController.class);
    }

}
