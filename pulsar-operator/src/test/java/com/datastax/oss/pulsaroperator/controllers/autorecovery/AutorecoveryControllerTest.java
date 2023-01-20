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
package com.datastax.oss.pulsaroperator.controllers.autorecovery;

import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.controllers.ControllerTestUtil;
import com.datastax.oss.pulsaroperator.crds.autorecovery.Autorecovery;
import com.datastax.oss.pulsaroperator.crds.autorecovery.AutorecoveryFullSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
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
                            app: pulsarname
                            cluster: pulsarname
                            component: autorecovery
                          name: pulsarname-autorecovery
                          namespace: ns
                          ownerReferences:
                          - apiVersion: pulsar.oss.datastax.com/v1alpha1
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
                    app: pulsarname
                    cluster: pulsarname
                    component: autorecovery
                  name: pulsarname-autorecovery
                  namespace: ns
                  ownerReferences:
                  - apiVersion: pulsar.oss.datastax.com/v1alpha1
                    kind: Autorecovery
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsarname-cr
                spec:
                  replicas: 1
                  selector:
                    matchLabels:
                      app: pulsarname
                      component: autorecovery
                  template:
                    metadata:
                      annotations:
                        prometheus.io/port: 8080
                        prometheus.io/scrape: "true"
                      labels:
                        app: pulsarname
                        cluster: pulsarname
                        component: autorecovery
                    spec:
                      containers:
                      - args:
                        - "bin/apply-config-from-env.py conf/bookkeeper.conf && bin/apply-config-from-env.py conf/proxy.conf && OPTS=\\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\\" exec bin/bookkeeper autorecovery"
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
                        - containerPort: 8001
                          name: wss
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
                .getMetadata().getAnnotations().get("pulsar.oss.datastax.com/configmap-pul-autorecovery");
        Assert.assertNotNull(checksum1);

        client = invokeController(spec);
        depl = client.getCreatedResource(Deployment.class).getResource();
        Assert.assertEquals(depl.getSpec().getTemplate()
                        .getMetadata().getAnnotations().get("pulsar.oss.datastax.com/configmap-pul-autorecovery"),
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
                .getMetadata().getAnnotations().get("pulsar.oss.datastax.com/configmap-pul-autorecovery");
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