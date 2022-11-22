package com.datastax.oss.pulsaroperator.controllers.function;

import static org.mockito.Mockito.mock;
import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.BaseComponentStatus;
import com.datastax.oss.pulsaroperator.crds.SerializationUtil;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorker;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorkerFullSpec;
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
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@JBossLog
public class FunctionsWorkerControllerTest {

    static final String NAMESPACE = "ns";
    static final String CLUSTER_NAME = "pulsarname";

    @Test
    public void testDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsarname
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
                            app: pulsarname
                            cluster: pulsarname
                            component: function
                          name: pulsarname-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        data:
                          functions_worker.yml: |
                            ---
                            assignmentWriteMaxRetries: 60
                            clusterCoordinationTopicName: coordinate
                            configurationStoreServers: pulsarname-zookeeper-ca.ns.svc.cluster.local:2181
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
                            pulsarFunctionsCluster: pulsarname
                            pulsarFunctionsNamespace: public/functions
                            pulsarServiceUrl: pulsar://pulsarname-broker.ns.svc.cluster.local:6650/
                            pulsarWebServiceUrl: http://pulsarname-broker.ns.svc.cluster.local:8080/
                            rescheduleTimeoutMs: 60000
                            schedulerClassName: org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler
                            topicCompactionFrequencySec: 1800
                            workerHostname: pulsarname-function
                            workerId: pulsarname-function
                            workerPort: 6750
                            zooKeeperSessionTimeoutMillis: 30000
                            zookeeperServers: pulsarname-zookeeper-ca.ns.svc.cluster.local:2181
                        """);


        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).get(1).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ConfigMap
                        metadata:
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: function
                          name: pulsarname-function-extra
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        data:
                          PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                          PULSAR_GC: -XX:+UseG1GC
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_MEM: -Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -XX:+ExitOnOutOfMemoryError
                        """);

        Assert.assertEquals(client.getCreatedResources(Service.class).get(0).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: Service
                        metadata:
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: function
                          name: pulsarname-function-ca
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          ports:
                          - name: http
                            port: 6750
                          - name: https
                            port: 6751
                          selector:
                            app: pulsarname
                            component: function
                        """);

        Assert.assertEquals(client.getCreatedResources(Service.class).get(1).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: Service
                        metadata:
                          labels:
                            app: pulsarname
                            cluster: pulsarname
                            component: function
                          name: pulsarname-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          clusterIP: None
                          ports:
                          - name: http
                            port: 6750
                          - name: https
                            port: 6751
                          selector:
                            app: pulsarname
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
                            app: pulsarname
                            cluster: pulsarname
                            component: function
                          name: pulsarname-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          podManagementPolicy: Parallel
                          replicas: 2
                          selector:
                            matchLabels:
                              app: pulsarname
                              component: function
                          serviceName: pulsarname-function
                          template:
                            metadata:
                              annotations:
                                prometheus.io/port: 8080
                                prometheus.io/scrape: "true"
                              labels:
                                app: pulsarname
                                cluster: pulsarname
                                component: function
                            spec:
                              containers:
                              - args:
                                - "bin/apply-config-from-env.py conf/broker.conf && cp -f funcconf/functions_worker.yml conf/functions_worker.yml && export PF_workerHostname=\\"${workerHostname}.pulsarname-function\\" && bin/gen-yml-from-env.py conf/functions_worker.yml && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/pulsar functions-worker"
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
                                    name: pulsarname-function-extra
                                image: apachepulsar/pulsar:2.10.2
                                imagePullPolicy: IfNotPresent
                                livenessProbe:
                                  exec:
                                    command:
                                    - sh
                                    - -c
                                    - curl -s --max-time 5 --fail http://localhost:6750/metrics/ > /dev/null
                                  initialDelaySeconds: 10
                                  periodSeconds: 30
                                  timeoutSeconds: 5
                                name: pulsarname-function
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
                                  name: pulsarname-function-logs
                              securityContext:
                                fsGroup: 0
                              serviceAccountName: pulsarname-function
                              terminationGracePeriodSeconds: 60
                              volumes:
                              - configMap:
                                  name: pulsarname-function
                                name: config-volume
                          updateStrategy:
                            type: RollingUpdate
                          volumeClaimTemplates:
                          - apiVersion: v1
                            kind: PersistentVolumeClaim
                            metadata:
                              name: pulsarname-function-logs
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
                            app: pulsarname
                            cluster: pulsarname
                            component: function
                          name: pulsarname-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        spec:
                          maxUnavailable: 1
                          selector:
                            matchLabels:
                              app: pulsarname
                              component: function
                        """);

        Assert.assertEquals(client.getCreatedResource(ServiceAccount.class).getResourceYaml(),
                """
                        ---
                        apiVersion: v1
                        kind: ServiceAccount
                        metadata:
                          name: pulsarname-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        """);
        Assert.assertEquals(client.getCreatedResource(Role.class).getResourceYaml(),
                """
                        ---
                        apiVersion: rbac.authorization.k8s.io/v1
                        kind: Role
                        metadata:
                          name: pulsarname-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
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
                          name: pulsarname-function
                          namespace: ns
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        roleRef:
                          kind: Role
                          name: pulsarname-function
                        subjects:
                        - kind: ServiceAccount
                          name: ns
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
                    name: pulsarname
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
                    functionConfig:
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
    public void testRuntimeK8s() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
                    runtime: kubernetes
                    runtimeResources:
                        cpu: 0.5
                        ram: 1Gi
                        disk: 5Gi
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
        functionRuntimeFactoryConfigs.put("pulsarAdminUrl", "https://pul-function.ns.svc.cluster.local:6750/");
        functionRuntimeFactoryConfigs.put("pulsarDockerImageName", "apachepulsar/pulsar:global");
        functionRuntimeFactoryConfigs.put("pulsarRootDir", "/pulsar");
        functionRuntimeFactoryConfigs.put("pulsarServiceUrl", "pulsar://pul-broker.ns.svc.cluster.local:6650/");
        functionRuntimeFactoryConfigs.put("submittingInsidePod", true);
        expectedData.put("functionRuntimeFactoryConfigs", functionRuntimeFactoryConfigs);
        LinkedHashMap<String, Object> functionInstanceMinResources = new LinkedHashMap<>();
        functionInstanceMinResources.put("cpu", 0.5);
        functionInstanceMinResources.put("disk", 1073741824);
        functionInstanceMinResources.put("ram", 1073741824);
        expectedData.put("functionInstanceMinResources", functionInstanceMinResources);

        final Map<String, Object> data = (Map<String, Object>) SerializationUtil
                .readYaml(createdResource.getResource().getData().get("functions_worker.yml"), Map.class);
        Assert.assertEquals(data, expectedData);
    }

    @Test
    public void testExtraConfig() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
                    config:
                        PULSAR_LOG_LEVEL: debug
                        myconfig: myvalue
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResources(ConfigMap.class).get(1);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_MEM", "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -XX:+ExitOnOutOfMemoryError");
        expectedData.put("PULSAR_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "debug");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("myconfig", "myvalue");

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
    public void testProbe() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                functionsWorker:
                    replicas: 1
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
                functionsWorker:
                    replicas: 1
                    probe:
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
                    probe:
                        enabled: true
                        period: 50
                """;

        client = invokeController(spec);


        createdResource =
                client.getCreatedResource(StatefulSet.class);

        container = createdResource.getResource().getSpec().getTemplate()
                .getSpec().getContainers().get(0);

        assertLivenessProbe(container.getLivenessProbe(), 5, 10, 50);
        assertReadinessProbe(container.getReadinessProbe(), 5, 10, 50);

    }

    private void assertLivenessProbe(Probe probe, int timeout, int initial, int period) {
        Assert.assertEquals(probe.getExec().getCommand(),
                List.of("sh", "-c",
                        "curl -s --max-time %d --fail http://localhost:6750/metrics/ > /dev/null".formatted(timeout)));

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

        Assert.assertEquals(getVolumeMountByName(container.getVolumeMounts(), "pul-function-fnlogs").getMountPath(),
                "/pulsar/logs");
        Assert.assertNotNull(getVolumeByName(podSpec.getVolumes(), "pul-function-fnlogs").getEmptyDir());
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

        Assert.assertEquals(getVolumeMountByName(container.getVolumeMounts(), "pul-function-logs").getMountPath(),
                "/pulsar/logs");
        Assert.assertNull(getVolumeByName(podSpec.getVolumes(), "pul-zookeeper-logs"));

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

        Assert.assertEquals(getVolumeMountByName(container.getVolumeMounts(), "pul-function-logs").getMountPath(),
                "/pulsar/logs");
        Assert.assertNull(getVolumeByName(podSpec.getVolumes(), "pul-function-logs"));

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

        Assert.assertEquals(getVolumeMountByName(container.getVolumeMounts(), "pul-function-logs").getMountPath(),
                "/pulsar/logs");
        Assert.assertNull(getVolumeByName(podSpec.getVolumes(), "pul-function-logs"));

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
        Assert.assertEquals(storageClass.getMetadata().getOwnerReferences().get(0).getKind(), "FunctionsWorker");

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
            if (metadata.getName().equals("pul-function-ca")) {
                isCaService = true;
            } else if (!metadata.getName().equals("pul-function")) {
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
        System.out.println(sts.getSpec().getTemplate()
                .getMetadata().getAnnotations());
        final String checksum1 = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-function");
        Assert.assertNotNull(checksum1);

        final String checksum1extra = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-function-extra");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        Assert.assertEquals(sts.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-function"),
                checksum1);
        Assert.assertEquals(sts.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-function-extra"),
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
                    functionConfig:
                        PULSAR_ROOT_LOG_LEVEL: debug
                """;

        client = invokeController(spec);
        sts = client.getCreatedResource(StatefulSet.class).getResource();
        final String checksum2 = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-function");
        Assert.assertNotNull(checksum2);
        Assert.assertNotEquals(checksum1, checksum2);

        final String checksum2extra = sts.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("com.datastax.oss/configmap-pul-function-extra");
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
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        """);
        Assert.assertEquals(client.getCreatedResource(ClusterRole.class).getResourceYaml(),
                """
                        ---
                        apiVersion: rbac.authorization.k8s.io/v1
                        kind: ClusterRole
                        metadata:
                          name: pul-function
                          ownerReferences:
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
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
                          - apiVersion: com.datastax.oss/v1alpha1
                            kind: FunctionsWorker
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        roleRef:
                          kind: ClusterRole
                          name: pul-function
                        subjects:
                        - kind: ServiceAccount
                          name: ns
                          """);
    }


    @SneakyThrows
    private void invokeControllerAndAssertError(String spec, String expectedErrorMessage) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<FunctionsWorker> result = invokeController(mockKubernetesClient, spec);
        Assert.assertTrue(result.isUpdateStatus());
        Assert.assertEquals(result.getResource().getStatus().getMessage(),
                expectedErrorMessage);
        Assert.assertEquals(result.getResource().getStatus().getReason(),
                BaseComponentStatus.Reason.ErrorConfig);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<FunctionsWorker>
                result = invokeController(mockKubernetesClient, spec);
        Assert.assertTrue(result.isUpdateStatus());
        return mockKubernetesClient;
    }

    private UpdateControl<FunctionsWorker> invokeController(MockKubernetesClient mockKubernetesClient, String spec)
            throws Exception {
        final FunctionsWorkerController controller = new FunctionsWorkerController(mockKubernetesClient.getClient());

        final FunctionsWorker fn = new FunctionsWorker();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(CLUSTER_NAME + "-cr");
        meta.setNamespace(NAMESPACE);
        fn.setMetadata(meta);

        final FunctionsWorkerFullSpec fullSpec = MockKubernetesClient.readYaml(spec, FunctionsWorkerFullSpec.class);
        fn.setSpec(fullSpec);

        final UpdateControl<FunctionsWorker> result = controller.reconcile(fn, mock(Context.class));
        return result;
    }
}