package com.datastax.oss.pulsaroperator.controllers;

import static org.mockito.Mockito.mock;
import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

@JBossLog
public class ZooKeeperControllerTest {

    static final String NAMESPACE = "ns";
    static final String CLUSTER_NAME = "pulsarname";

    @Test
    public void testDefaults() throws Exception {
        final MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        String spec = """
                global:
                    name: pulsarname
                    persistence: true
                    image: apachepulsar/pulsar:2.10.2
                zookeeper:
                    dataVolume:
                        name: volume-name
                        size: 1g
                        storageClass: default
                   
                """;

        invokeReconcilier(client, spec);


        final MockKubernetesClient.ResourceInteraction configMapInt = client.getCreatedResources().get(0);
        Mockito.verify(configMapInt.getInteraction()).createOrReplace();
        final String configMap = configMapInt.getResourceYaml();
        log.info(configMap);
        Assert.assertEquals(configMap,
                """
                        ---
                        apiVersion: "v1"
                        kind: "ConfigMap"
                        metadata:
                          labels:
                            app: "pulsarname"
                            cluster: "pulsarname"
                            component: "zookeeper"
                          name: "pulsarname-zookeeper"
                          namespace: "ns"
                        data: {}
                        """);


        final MockKubernetesClient.ResourceInteraction statefulSetInt = client.getCreatedResources().get(1);
        Mockito.verify(statefulSetInt.getInteraction()).createOrReplace();
        final String statefulSet = statefulSetInt.getResourceYaml();
        Assert.assertEquals(statefulSet,
                """
                        ---
                        apiVersion: "apps/v1"
                        kind: "StatefulSet"
                        metadata:
                          labels:
                            app: "pulsarname"
                            cluster: "pulsarname"
                            component: "zookeeper"
                          name: "pulsarname-zookeeper"
                          namespace: "ns"
                        spec:
                          podManagementPolicy: "Parallel"
                          replicas: 1
                          selector:
                            matchLabels:
                              app: "pulsarname"
                              component: "zookeeper"
                          serviceName: "pulsarname-zookeeper"
                          template:
                            metadata:
                              annotations: {}
                              labels:
                                app: "pulsarname"
                                cluster: "pulsarname"
                                component: "zookeeper"
                            spec:
                              affinity: {}
                              containers:
                              - args:
                                - "bin/apply-config-from-env.py conf/zookeeper.conf && bin/generate-zookeeper-config.sh\\
                                  \\ conf/zookeeper.conf && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\"\\
                                  \\ exec bin/pulsar zookeeper"
                                command:
                                - "sh"
                                - "-c"
                                env:
                                - name: "ZOOKEEPER_SERVERS"
                                  value: "pulsarname-zookeeper-0"
                                envFrom:
                                - configMapRef:
                                    name: "pulsarname-zookeeper"
                                image: "apachepulsar/pulsar:2.10.2"
                                imagePullPolicy: "IfNotPresent"
                                name: "pulsarname-zookeeper"
                                ports:
                                - containerPort: 2181
                                  name: "client"
                                - containerPort: 2888
                                  name: "server"
                                - containerPort: 3888
                                  name: "leader-election"
                              securityContext:
                                fsGroup: 0
                              terminationGracePeriodSeconds: 0
                          volumeClaimTemplates:
                          - apiVersion: "v1"
                            kind: "PersistentVolumeClaim"
                            metadata:
                              name: "pulsarname-zookeeper-volume-name"
                            spec:
                              accessModes:
                              - "ReadWriteOnce"
                              resources:
                                requests:
                                  storage: "1g"
                              storageClassName: "pulsarname-zookeeper-volume-name"
                        """);

        final MockKubernetesClient.ResourceInteraction serviceInt = client.getCreatedResources().get(2);
        Mockito.verify(serviceInt.getInteraction()).createOrReplace();
        final String service = serviceInt.getResourceYaml();
        Assert.assertEquals(service,
                """
                        ---
                        apiVersion: "v1"
                        kind: "Service"
                        metadata:
                          annotations:
                            service.alpha.kubernetes.io/tolerate-unready-endpoints: "true"
                          labels:
                            app: "pulsarname"
                            cluster: "pulsarname"
                            component: "zookeeper"
                          name: "pulsarname-zookeeper"
                          namespace: "ns"
                        spec:
                          clusterIP: "None"
                          ports:
                          - name: "server"
                            port: 2888
                          - name: "leader-election"
                            port: 3888
                          - name: "client"
                            port: 2181
                          publishNotReadyAddresses: true
                          selector:
                            app: "pulsarname"
                            component: "zookeeper"
                        """);

        final MockKubernetesClient.ResourceInteraction serviceIntCa = client.getCreatedResources().get(3);
        Mockito.verify(serviceIntCa.getInteraction()).createOrReplace();
        final String serviceCa = serviceIntCa.getResourceYaml();
        Assert.assertEquals(serviceCa,
                """
                    ---
                    apiVersion: "v1"
                    kind: "Service"
                    metadata:
                      annotations: {}
                      labels:
                        app: "pulsarname"
                        cluster: "pulsarname"
                        component: "zookeeper"
                      name: "pulsarname-zookeeper-ca"
                      namespace: "ns"
                    spec:
                      ports:
                      - name: "server"
                        port: 2888
                      - name: "leader-election"
                        port: 3888
                      - name: "client"
                        port: 2181
                      selector:
                        app: "pulsarname"
                        component: "zookeeper"
                        """);
    }

    @Test
    public void testOverrideImage() throws Exception {
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        String spec = """
                global:
                    name: pulsarname
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper: 
                    dataVolume:
                        name: volume-name
                        size: 1g
                        storageClass: default
                """;
        invokeReconcilier(client, spec);

        MockKubernetesClient.ResourceInteraction<StatefulSet> createdResource =
                client.getCreatedResource(StatefulSet.class);
        Mockito.verify(createdResource.getInteraction()).createOrReplace();


        Assert.assertEquals(createdResource.getResource().getSpec()
                .getTemplate()
                .getSpec().getContainers().get(0)
                .getImage(), "apachepulsar/pulsar:global");

        client = new MockKubernetesClient(NAMESPACE);
        spec = """
                global:
                    name: pulsarname
                    persistence: false
                    image: apachepulsar/pulsar:global
                zookeeper:
                    image: apachepulsar/pulsar:zk
                    dataVolume:
                        name: volume-name
                        size: 1g
                        storageClass: default
                """;
        invokeReconcilier(client, spec);

        createdResource =
                client.getCreatedResource(StatefulSet.class);
        Mockito.verify(createdResource.getInteraction()).createOrReplace();


        Assert.assertEquals(createdResource.getResource().getSpec()
                .getTemplate()
                .getSpec().getContainers().get(0)
                .getImage(), "apachepulsar/pulsar:zk");

    }

    @SneakyThrows
    private void invokeReconcilier(MockKubernetesClient client, String spec) {
        final ZooKeeperController reconcilier = new ZooKeeperController(client.getClient());

        final ZooKeeper zooKeeper = new ZooKeeper();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(CLUSTER_NAME + "-cr");
        meta.setNamespace(NAMESPACE);
        zooKeeper.setMetadata(meta);

        final ZooKeeperFullSpec zooKeeperFullSpec = MockKubernetesClient.readYaml(spec, ZooKeeperFullSpec.class);
        zooKeeper.setSpec(zooKeeperFullSpec);

        final UpdateControl<ZooKeeper> result = reconcilier.reconcile(zooKeeper, mock(Context.class));
        Assert.assertTrue(result.isUpdateResource());
    }
}