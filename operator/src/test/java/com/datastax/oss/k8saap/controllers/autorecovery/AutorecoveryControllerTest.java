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
package com.datastax.oss.k8saap.controllers.autorecovery;

import com.datastax.oss.k8saap.common.SerializationUtil;
import com.datastax.oss.k8saap.controllers.ControllerTestUtil;
import com.datastax.oss.k8saap.controllers.KubeTestUtil;
import com.datastax.oss.k8saap.crds.autorecovery.Autorecovery;
import com.datastax.oss.k8saap.crds.autorecovery.AutorecoveryFullSpec;
import com.datastax.oss.k8saap.mocks.MockKubernetesClient;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.NodeSelectorRequirement;
import io.fabric8.kubernetes.api.model.NodeSelectorTerm;
import io.fabric8.kubernetes.api.model.PodAffinityTerm;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.Test;

@JBossLog
public class AutorecoveryControllerTest {

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
                            app: pulsar
                            cluster: pulsarname
                            component: autorecovery
                          name: pulsarname-autorecovery
                          namespace: ns
                          ownerReferences:
                          - apiVersion: k8saap.oss.datastax.com/v1alpha1
                            kind: Autorecovery
                            blockOwnerDeletion: true
                            controller: true
                            name: pulsarname-cr
                        data:
                          BOOKIE_GC: -XX:+UseG1GC
                          BOOKIE_MEM: -Xms512m -Xmx512m -XX:+ExitOnOutOfMemoryError
                          PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                          PULSAR_LOG_LEVEL: info
                          PULSAR_LOG_ROOT_LEVEL: info
                          PULSAR_PREFIX_ensemblePlacementPolicy: org.apache.bookkeeper.client.RegionAwareEnsemblePlacementPolicy 
                          PULSAR_PREFIX_reppDnsResolverClass: org.apache.pulsar.zookeeper.ZkBookieRackAffinityMapping
                          PULSAR_PREFIX_zkServers: pulsarname-zookeeper-ca.ns.svc.cluster.local:2181
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
                    cluster: pulsarname
                    component: autorecovery
                  name: pulsarname-autorecovery
                  namespace: ns
                  ownerReferences:
                  - apiVersion: k8saap.oss.datastax.com/v1alpha1
                    kind: Autorecovery
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsarname-cr
                spec:
                  replicas: 1
                  selector:
                    matchLabels:
                      app: pulsar
                      cluster: pulsarname
                      component: autorecovery
                  template:
                    metadata:
                      annotations:
                        prometheus.io/port: 8080
                        prometheus.io/scrape: "true"
                      labels:
                        app: pulsar
                        cluster: pulsarname
                        component: autorecovery
                    spec:
                      affinity:
                        podAntiAffinity:
                          requiredDuringSchedulingIgnoredDuringExecution:
                          - labelSelector:
                              matchLabels:
                                app: pulsar
                                cluster: pulsarname
                                component: autorecovery
                            topologyKey: kubernetes.io/hostname
                      containers:
                      - args:
                        - "bin/apply-config-from-env.py conf/bookkeeper.conf && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/bookkeeper autorecovery"
                        command:
                        - sh
                        - -c
                        envFrom:
                        - configMapRef:
                            name: pulsarname-autorecovery
                        image: apachepulsar/pulsar:2.10.2
                        imagePullPolicy: IfNotPresent
                        name: pulsarname-autorecovery
                        ports:
                        - containerPort: 8000
                          name: http
                        resources:
                          requests:
                            cpu: 0.3
                            memory: 512Mi
                      dnsConfig:
                        options:
                        - name: ndots
                          value: 4
                      terminationGracePeriodSeconds: 60
                """);

    }

    @Test
    public void testConfig() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                autorecovery:
                    config:
                        PULSAR_LOG_LEVEL: debug
                        customConfig: customValue
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_reppDnsResolverClass",
                "org.apache.pulsar.zookeeper.ZkBookieRackAffinityMapping");
        expectedData.put("PULSAR_PREFIX_zkServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("BOOKIE_MEM", "-Xms512m -Xmx512m -XX:+ExitOnOutOfMemoryError");
        expectedData.put("BOOKIE_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "debug");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_customConfig", "customValue");
        expectedData.put("PULSAR_PREFIX_ensemblePlacementPolicy",
                "org.apache.bookkeeper.client.RegionAwareEnsemblePlacementPolicy");

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
                        enabled: true
                        bookkeeper:
                            enabled: true
                        autorecovery:
                            enabled: true
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<ConfigMap> createdResource =
                client.getCreatedResource(ConfigMap.class);

        Map<String, String> expectedData = new HashMap<>();
        expectedData.put("PULSAR_PREFIX_reppDnsResolverClass",
                "org.apache.pulsar.zookeeper.ZkBookieRackAffinityMapping");
        expectedData.put("PULSAR_PREFIX_zkServers", "pul-zookeeper-ca.ns.svc.cluster.local:2181");
        expectedData.put("BOOKIE_MEM", "-Xms512m -Xmx512m -XX:+ExitOnOutOfMemoryError");
        expectedData.put("BOOKIE_GC", "-XX:+UseG1GC");
        expectedData.put("PULSAR_LOG_LEVEL", "info");
        expectedData.put("PULSAR_LOG_ROOT_LEVEL", "info");
        expectedData.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        expectedData.put("PULSAR_PREFIX_tlsHostnameVerificationEnabled", "true");
        expectedData.put("PULSAR_PREFIX_tlsProvider", "OpenSSL");
        expectedData.put("PULSAR_PREFIX_tlsProviderFactoryClass", "org.apache.bookkeeper.tls.TLSContextFactory");
        expectedData.put("PULSAR_PREFIX_tlsCertificatePath", "/pulsar/certs/tls.crt");
        expectedData.put("PULSAR_PREFIX_tlsKeyStoreType", "PEM");
        expectedData.put("PULSAR_PREFIX_tlsKeyStore", "/pulsar/tls-pk8.key");
        expectedData.put("PULSAR_PREFIX_tlsTrustStoreType", "PEM");
        expectedData.put("PULSAR_PREFIX_tlsClientAuthentication", "true");
        expectedData.put("PULSAR_PREFIX_tlsTrustStore", "/etc/ssl/certs/ca-certificates.crt");
        expectedData.put("PULSAR_PREFIX_ensemblePlacementPolicy",
                "org.apache.bookkeeper.client.RegionAwareEnsemblePlacementPolicy");


        final Map<String, String> data = createdResource.getResource().getData();
        Assert.assertEquals(data, expectedData);

        final Deployment depl = client.getCreatedResource(Deployment.class).getResource();
        final String stsCommand = depl.getSpec().getTemplate().getSpec().getContainers().get(0)
                .getArgs().get(0);
        Assert.assertEquals(stsCommand, "bin/apply-config-from-env.py conf/bookkeeper.conf && openssl pkcs8 -topk8 "
                + "-inform PEM -outform PEM -in /pulsar/certs/tls.key -out /pulsar/tls-pk8.key -nocrypt && "
                + "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" "
                + "exec bin/bookkeeper autorecovery");
    }

    @Test
    public void testReplicas() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                autorecovery:
                    replicas: 3
                """;
        MockKubernetesClient client = invokeController(spec);

        MockKubernetesClient.ResourceInteraction<Deployment> createdResource =
                client.getCreatedResource(Deployment.class);

        Assert.assertEquals((int) createdResource.getResource().getSpec().getReplicas(), 3);
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
                autorecovery:
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
    public void testAnnotations() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                autorecovery:
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
                autorecovery:
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
                        "component", "autorecovery",
                        "label-2", "label2-value"
                )
        );
        Assert.assertEquals(
                client.getCreatedResource(Deployment.class).getResource().getMetadata().getLabels(),
                Map.of(
                        "cluster", "pul",
                        "app", "pulsar",
                        "component", "autorecovery",
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
                autorecovery:
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
                        "component", "autorecovery",
                        "custom", "customvalue"
                )
        );
    }

    @Test
    public void testImagePullSecrets() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                autorecovery:
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
    public void testEnv() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                autorecovery:
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
    public void testInitContainers() throws Exception {
        String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                autorecovery:
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
                autorecovery:
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
    public void testGracePeriod() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                autorecovery:
                    gracePeriod: -1
                """;
        invokeControllerAndAssertError(spec, "invalid configuration property \"autorecovery.gracePeriod\" for value "
                + "\"-1\": must be greater than or equal to 0");

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                autorecovery:
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
                autorecovery:
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
                autorecovery:
                    resources:
                        requests:
                          memory: 1.5Gi
                          cpu: 0.5
                        limits:
                          memory: 2Gi
                          cpu: 1
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
                autorecovery:
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
                autorecovery:
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
                autorecovery:
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
                "component", "autorecovery"
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
                "component", "autorecovery"
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
                "component", "autorecovery"
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
                "component", "autorecovery"
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
                "component", "autorecovery"
        ));
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
                .getMetadata().getAnnotations().get("k8saap.oss.datastax.com/configmap-pul-autorecovery");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        depl = client.getCreatedResource(Deployment.class).getResource();
        Assert.assertEquals(depl.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("k8saap.oss.datastax.com/configmap-pul-autorecovery"),
                checksum1);

        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                    restartOnConfigMapChange: true
                autorecovery:
                    config:
                        PULSAR_ROOT_LOG_LEVEL: debug
                """;

        client = invokeController(spec);
        depl = client.getCreatedResource(Deployment.class).getResource();
        final String checksum2 = depl.getSpec().getTemplate()
                .getMetadata().getAnnotations().get("k8saap.oss.datastax.com/configmap-pul-autorecovery");
        Assert.assertNotNull(checksum2);
        Assert.assertNotEquals(checksum1, checksum2);
    }


    @SneakyThrows
    private void invokeControllerAndAssertError(String spec, String expectedErrorMessage) {
        new ControllerTestUtil<AutorecoveryFullSpec, Autorecovery>(NAMESPACE, CLUSTER_NAME)
                .invokeControllerAndAssertError(spec,
                        expectedErrorMessage,
                        Autorecovery.class,
                        AutorecoveryFullSpec.class,
                        AutorecoveryController.class);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return new ControllerTestUtil<AutorecoveryFullSpec, Autorecovery>(NAMESPACE, CLUSTER_NAME)
                .invokeController(spec,
                        Autorecovery.class,
                        AutorecoveryFullSpec.class,
                        AutorecoveryController.class);
    }
}
