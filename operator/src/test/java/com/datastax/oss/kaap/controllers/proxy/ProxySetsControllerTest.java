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
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.proxy.Proxy;
import com.datastax.oss.kaap.crds.proxy.ProxyFullSpec;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
import com.datastax.oss.kaap.mocks.MockResourcesResolver;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.Test;

@JBossLog
public class ProxySetsControllerTest {

    static final String NAMESPACE = "ns";
    static final String CLUSTER_NAME = "pulsar-spec-1";
    private final ControllerTestUtil<ProxyFullSpec, Proxy> controllerTestUtil =
            new ControllerTestUtil<>(NAMESPACE, CLUSTER_NAME);

    @Test
    public void testProxySetsDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                proxy:
                    setsUpdateStrategy: Parallel
                    sets:
                      set1: {}
                      set2: {}
                """;
        MockKubernetesClient client = invokeController(spec);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 2);

        assertDeploymentEqualsDefault("set1",
                client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-set1").getResource());

        assertDeploymentEqualsDefault("set2",
                client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).size(), 4);
        assertConfigMapEqualsDefault("set1",
                client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-proxy-set1").getResource());
        assertConfigMapEqualsDefault("set2",
                client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-proxy-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(PodDisruptionBudget.class).size(), 2);

        assertPdbEqualsDefault("set1",
                client.getCreatedResource(PodDisruptionBudget.class, "pulsar-spec-1-proxy-set1").getResource());
        assertPdbEqualsDefault("set2",
                client.getCreatedResource(PodDisruptionBudget.class, "pulsar-spec-1-proxy-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(Service.class).size(), 3);

        assertServiceEqualsDefault("set1",
                client.getCreatedResource(Service.class, "pulsar-spec-1-proxy-set1").getResource());
        assertServiceEqualsDefault("set2",
                client.getCreatedResource(Service.class, "pulsar-spec-1-proxy-set2").getResource());
    }


    @Test
    public void testOverride() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                proxy:
                    setsUpdateStrategy: Parallel
                    replicas: 6
                    config:
                        common: commonvalue
                    sets:
                      set1:
                        service:
                            annotations:
                                externaldns: myset1
                            additionalPorts:
                              - name: customport
                                port: 8888
                        replicas: 3
                        config:
                            myname: set1
                        pdb:
                            maxUnavailable: 2
                      set2:
                        config:
                            common: override
                """;
        MockKubernetesClient client = invokeController(spec);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 2);

        Assert.assertEquals(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-set1")
                .getResource()
                .getSpec()
                .getReplicas(), 3);

        Assert.assertEquals(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-set2")
                .getResource()
                .getSpec()
                .getReplicas(), 6);

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-proxy-set1")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_common"), "commonvalue");

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-proxy-set2")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_common"), "override");

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-proxy-set1")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_myname"), "set1");

        Assert.assertNull(client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-proxy-set2")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_myname"));

        Assert.assertEquals(client.getCreatedResource(PodDisruptionBudget.class, "pulsar-spec-1-proxy-set1")
                .getResource()
                .getSpec()
                .getMaxUnavailable()
                .getIntVal(), 2);

        Assert.assertEquals(client.getCreatedResource(PodDisruptionBudget.class, "pulsar-spec-1-proxy-set2")
                .getResource()
                .getSpec()
                .getMaxUnavailable()
                .getIntVal(), 1);

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsar-spec-1-proxy-set1")
                .getResource()
                .getMetadata()
                .getAnnotations().get("externaldns"), "myset1");

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsar-spec-1-proxy-set1")
                .getResource()
                .getSpec()
                .getPorts().size(), 4);

        Assert.assertNull(client.getCreatedResource(Service.class, "pulsar-spec-1-proxy-set2")
                .getResource()
                .getMetadata()
                .getAnnotations());

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsar-spec-1-proxy-set2")
                .getResource()
                .getSpec()
                .getPorts().size(), 3);
    }

    private void assertDeploymentEqualsDefault(String setName, Deployment deployment) {
        final Deployment defaultSts = invokeController("""
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(Deployment.class).getResource();
        final String resourceName = CLUSTER_NAME + "-proxy-" + setName;
        Assert.assertEquals(deployment.getMetadata().getName(), resourceName);
        assertResourceSetLabel(deployment, setName);
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnvFrom().get(0)
                .getConfigMapRef().getName(), resourceName);
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(1).getEnvFrom().get(0)
                .getConfigMapRef().getName(), resourceName + "-ws");

        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getName(),
                resourceName);
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(1).getName(),
                resourceName + "-ws");

        deployment.getSpec().getSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);
        deployment.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        deployment.getSpec().getTemplate().getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        deployment.getSpec().getTemplate().getSpec().getAffinity().getPodAntiAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .get(0)
                .getLabelSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);


        final String defaultResourceName = defaultSts.getMetadata().getName();
        deployment.getMetadata().setName(defaultResourceName);
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setName(defaultResourceName);
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnvFrom().get(0)
                .getConfigMapRef().setName(defaultResourceName);

        deployment.getSpec().getTemplate().getSpec().getContainers().get(1).getEnvFrom().get(0)
                .getConfigMapRef().setName(defaultResourceName + "-ws");
        deployment.getSpec().getTemplate().getSpec().getContainers().get(1).setName(defaultResourceName + "-ws");

        defaultSts.getSpec().getSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);
        defaultSts.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        defaultSts.getSpec().getTemplate().getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);

        Assert.assertEquals(SerializationUtil.writeAsYaml(deployment), SerializationUtil.writeAsYaml(defaultSts));
    }

    private void assertConfigMapEqualsDefault(String setName, ConfigMap cmap) {
        final ConfigMap defaultCmap = invokeController("""
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(ConfigMap.class).getResource();
        final String resourceName = CLUSTER_NAME + "-proxy-" + setName;
        Assert.assertEquals(cmap.getMetadata().getName(), resourceName);
        Assert.assertEquals(cmap.getMetadata().getLabels().get(CRDConstants.LABEL_RESOURCESET), setName);
        cmap.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);

        final String defaultResourceName = defaultCmap.getMetadata().getName();
        cmap.getMetadata().setName(defaultResourceName);
        defaultCmap.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);


        Assert.assertEquals(SerializationUtil.writeAsYaml(cmap), SerializationUtil.writeAsYaml(defaultCmap));
    }

    private void assertPdbEqualsDefault(String setName, PodDisruptionBudget pdb) {
        final PodDisruptionBudget defaultPdb = invokeController("""
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(PodDisruptionBudget.class).getResource();
        final String resourceName = CLUSTER_NAME + "-proxy-" + setName;
        Assert.assertEquals(pdb.getMetadata().getName(), resourceName);
        Assert.assertEquals(pdb.getMetadata().getLabels().get(CRDConstants.LABEL_RESOURCESET), setName);
        Assert.assertEquals(pdb.getSpec().getSelector().getMatchLabels().get(CRDConstants.LABEL_RESOURCESET), setName);
        pdb.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        pdb.getSpec().getSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);

        final String defaultResourceName = defaultPdb.getMetadata().getName();
        pdb.getMetadata().setName(defaultResourceName);
        defaultPdb.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        defaultPdb.getSpec().getSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);

        Assert.assertEquals(SerializationUtil.writeAsYaml(pdb), SerializationUtil.writeAsYaml(defaultPdb));
    }

    private void assertServiceEqualsDefault(String setName, Service service) {
        final Service defaultService = invokeController("""
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(Service.class).getResource();
        final String resourceName = CLUSTER_NAME + "-proxy-" + setName;
        Assert.assertEquals(service.getMetadata().getName(), resourceName);
        Assert.assertEquals(service.getMetadata().getLabels().get(CRDConstants.LABEL_RESOURCESET), setName);
        Assert.assertEquals(service.getSpec().getSelector().get(CRDConstants.LABEL_RESOURCESET), setName);
        service.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        service.getSpec().getSelector().remove(CRDConstants.LABEL_RESOURCESET);

        final String defaultResourceName = defaultService.getMetadata().getName();
        service.getMetadata().setName(defaultResourceName);
        defaultService.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);

        Assert.assertEquals(SerializationUtil.writeAsYaml(service), SerializationUtil.writeAsYaml(defaultService));
    }

    private void assertResourceSetLabel(Deployment sts, String value) {
        Assert.assertEquals(sts.getMetadata().getLabels().get(CRDConstants.LABEL_RESOURCESET), value);
        Assert.assertEquals(
                sts.getSpec().getTemplate().getMetadata().getLabels().get(CRDConstants.LABEL_RESOURCESET), value);
        Assert.assertEquals(
                sts.getSpec().getSelector().getMatchLabels().get(CRDConstants.LABEL_RESOURCESET), value);
        Assert.assertEquals(sts.getSpec().getTemplate().getSpec().getAffinity().getPodAntiAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .get(0)
                .getLabelSelector().getMatchLabels().get(CRDConstants.LABEL_RESOURCESET), value);
    }


    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return controllerTestUtil
                .invokeController(spec,
                        Proxy.class,
                        ProxyFullSpec.class,
                        ProxyController.class);
    }

    @Test
    public void testRacks() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                    resourceSets:
                        set1norack: {}
                        set2norack: {}
                        setdefault:
                            rack: rdefault
                        set1:
                            rack: rack1
                        set2:
                            rack: rack2
                        set3:
                            rack: rack3
                    racks:
                        rdefault: {}
                        rack1:
                            zone:
                                enabled: true
                                requireRackAffinity: true
                                requireRackAntiAffinity: false
                            host:
                                enabled: false
                        rack2:
                            host:
                                enabled: true
                                requireRackAffinity: true
                                requireRackAntiAffinity: false
                            zone:
                                enabled: false
                        rack3:
                            host:
                                enabled: true
                                requireRackAffinity: false
                                requireRackAntiAffinity: false
                            zone:
                                enabled: true
                                requireRackAffinity: true
                                requireRackAntiAffinity: true
                proxy:
                    setsUpdateStrategy: Parallel
                    sets:
                      set1norack: {}
                      set2norack:
                        antiAffinity:
                            zone:
                                enabled: true
                            host:
                                enabled: true
                      setdefault: {}
                      set1: {}
                      set2: {}
                      set3: {}
                """;
        MockKubernetesClient client = invokeController(spec);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 6);

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-set1norack")
                                .getResource()
                                .getSpec()
                                .getTemplate().getSpec().getAffinity()
                ),
                """
                        ---
                        podAntiAffinity:
                          requiredDuringSchedulingIgnoredDuringExecution:
                          - labelSelector:
                              matchLabels:
                                app: pulsar
                                cluster: pulsar-spec-1
                                component: proxy
                                resource-set: set1norack
                            topologyKey: kubernetes.io/hostname
                        """);

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-set2norack")
                                .getResource()
                                .getSpec()
                                .getTemplate().getSpec().getAffinity()
                ),
                """
                        ---
                        podAntiAffinity:
                          preferredDuringSchedulingIgnoredDuringExecution:
                          - podAffinityTerm:
                              labelSelector:
                                matchLabels:
                                  app: pulsar
                                  cluster: pulsar-spec-1
                                  component: proxy
                                  resource-set: set2norack
                              topologyKey: kubernetes.io/hostname
                            weight: 100
                          - podAffinityTerm:
                              labelSelector:
                                matchLabels:
                                  app: pulsar
                                  cluster: pulsar-spec-1
                                  component: proxy
                                  resource-set: set2norack
                              topologyKey: failure-domain.beta.kubernetes.io/zone
                            weight: 100
                        """);

        Assert.assertNull(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-setdefault")
                .getResource()
                .getSpec()
                .getTemplate().getSpec().getAffinity()
        );

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-set1")
                                .getResource()
                                .getSpec()
                                .getTemplate().getSpec().getAffinity()
                ),
                """
                        ---
                        podAffinity:
                          requiredDuringSchedulingIgnoredDuringExecution:
                          - labelSelector:
                              matchExpressions:
                              - key: rack
                                operator: In
                                values:
                                - rack1
                            topologyKey: failure-domain.beta.kubernetes.io/zone
                        podAntiAffinity:
                          preferredDuringSchedulingIgnoredDuringExecution:
                          - podAffinityTerm:
                              labelSelector:
                                matchExpressions:
                                - key: rack
                                  operator: In
                                  values:
                                  - rdefault
                                  - rack3
                                  - rack2
                              topologyKey: failure-domain.beta.kubernetes.io/zone
                            weight: 100
                          requiredDuringSchedulingIgnoredDuringExecution:
                          - labelSelector:
                              matchLabels:
                                app: pulsar
                                cluster: pulsar-spec-1
                                component: proxy
                                rack: rack1
                                resource-set: set1
                            topologyKey: kubernetes.io/hostname
                        """);

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-set2")
                                .getResource()
                                .getSpec()
                                .getTemplate().getSpec().getAffinity()
                ),
                """
                        ---
                        podAffinity:
                          requiredDuringSchedulingIgnoredDuringExecution:
                          - labelSelector:
                              matchExpressions:
                              - key: rack
                                operator: In
                                values:
                                - rack2
                            topologyKey: kubernetes.io/hostname
                        podAntiAffinity:
                          preferredDuringSchedulingIgnoredDuringExecution:
                          - podAffinityTerm:
                              labelSelector:
                                matchExpressions:
                                - key: rack
                                  operator: In
                                  values:
                                  - rdefault
                                  - rack3
                                  - rack1
                              topologyKey: kubernetes.io/hostname
                            weight: 100
                        """);

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-set3")
                                .getResource()
                                .getSpec()
                                .getTemplate().getSpec().getAffinity()
                ),
                """
                        ---
                        podAffinity:
                          preferredDuringSchedulingIgnoredDuringExecution:
                          - podAffinityTerm:
                              labelSelector:
                                matchExpressions:
                                - key: rack
                                  operator: In
                                  values:
                                  - rack3
                              topologyKey: kubernetes.io/hostname
                            weight: 100
                          requiredDuringSchedulingIgnoredDuringExecution:
                          - labelSelector:
                              matchExpressions:
                              - key: rack
                                operator: In
                                values:
                                - rack3
                            topologyKey: failure-domain.beta.kubernetes.io/zone
                        podAntiAffinity:
                          preferredDuringSchedulingIgnoredDuringExecution:
                          - podAffinityTerm:
                              labelSelector:
                                matchExpressions:
                                - key: rack
                                  operator: In
                                  values:
                                  - rdefault
                                  - rack1
                                  - rack2
                              topologyKey: kubernetes.io/hostname
                            weight: 100
                          requiredDuringSchedulingIgnoredDuringExecution:
                          - labelSelector:
                              matchExpressions:
                              - key: rack
                                operator: In
                                values:
                                - rdefault
                                - rack1
                                - rack2
                            topologyKey: failure-domain.beta.kubernetes.io/zone
                          - labelSelector:
                              matchLabels:
                                app: pulsar
                                cluster: pulsar-spec-1
                                component: proxy
                                rack: rack3
                                resource-set: set3
                            topologyKey: kubernetes.io/hostname
                        """);
    }


    @SneakyThrows
    private UpdateControl<Proxy> invokeController(String spec, Proxy lastCr, MockKubernetesClient client) {
        return controllerTestUtil.invokeController(client, spec, lastCr, ProxyFullSpec.class, ProxyController.class);
    }

    @Test
    public void testRollingUpdate() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                proxy:
                    sets:
                      setz: {}
                      seta: {}
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<Proxy> proxyUpdateControl = invokeController(spec, new Proxy(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        ProxyController.ProxySetsLastApplied proxySetsLastApplied =
                SerializationUtil.readJson(proxyUpdateControl.getResource().get().getStatus().getLastApplied(),
                        ProxyController.ProxySetsLastApplied.class);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 1);
        // verify order of sets follows the order declared in the spec
        Assert.assertNotNull(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-setz"));
        Assert.assertNotNull(proxySetsLastApplied.getCommon());
        Assert.assertNotNull(proxySetsLastApplied.getSets().get("setz"));


        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        proxySetsLastApplied =
                SerializationUtil.readJson(proxyUpdateControl.getResource().get().getStatus().getLastApplied(),
                        ProxyController.ProxySetsLastApplied.class);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);
        Assert.assertNotNull(proxySetsLastApplied.getSets().get("setz"));

        resolver.putDeployment("pulsar-spec-1-proxy-setz", true);

        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        proxySetsLastApplied =
                SerializationUtil.readJson(proxyUpdateControl.getResource().get().getStatus().getLastApplied(),
                        ProxyController.ProxySetsLastApplied.class);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 1);
        Assert.assertNotNull(proxySetsLastApplied.getSets().get("seta"));

        resolver.putDeployment("pulsar-spec-1-proxy-seta", true);

        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlReady(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);
        Assert.assertNotNull(proxyUpdateControl.getResource().get().getStatus().getLastApplied());

        // now update

        resolver.putDeployment("pulsar-spec-1-proxy-setz", false);
        resolver.putDeployment("pulsar-spec-1-proxy-seta", false);

        spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                proxy:
                    config:
                        newvalue: true
                    sets:
                      setz: {}
                      seta: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 1);
        Assert.assertNotNull(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-setz"));


        resolver.putDeployment("pulsar-spec-1-proxy-setz", true);

        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 1);
        Assert.assertNotNull(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-seta"));

        resolver.putDeployment("pulsar-spec-1-proxy-seta", true);

        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlReady(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);
    }

    @Test
    public void testParallelUpdate() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                proxy:
                    setsUpdateStrategy: Parallel
                    sets:
                      setz: {}
                      seta: {}
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<Proxy> proxyUpdateControl = invokeController(spec, new Proxy(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 2);
        Assert.assertNotNull(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-setz"));
        Assert.assertNotNull(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-seta"));
        Assert.assertNotNull(proxyUpdateControl.getResource().get().getStatus().getLastApplied());


        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);
        Assert.assertNotNull(proxyUpdateControl.getResource().get().getStatus().getLastApplied());

        resolver.putDeployment("pulsar-spec-1-proxy-setz", true);

        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);
        Assert.assertNotNull(proxyUpdateControl.getResource().get().getStatus().getLastApplied());

        resolver.putDeployment("pulsar-spec-1-proxy-seta", true);

        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlReady(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);
        Assert.assertNotNull(proxyUpdateControl.getResource().get().getStatus().getLastApplied());

        // now update

        resolver.putDeployment("pulsar-spec-1-proxy-seta", false);
        resolver.putDeployment("pulsar-spec-1-proxy-setz", false);

        spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                proxy:
                    setsUpdateStrategy: Parallel
                    config:
                        newvalue: true
                    sets:
                      setz: {}
                      seta: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 2);
        Assert.assertNotNull(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-setz"));
        Assert.assertNotNull(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-seta"));
        Assert.assertNotNull(proxyUpdateControl.getResource().get().getStatus().getLastApplied());


        resolver.putDeployment("pulsar-spec-1-proxy-setz", true);

        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);
        Assert.assertNotNull(proxyUpdateControl.getResource().get().getStatus().getLastApplied());

        resolver.putDeployment("pulsar-spec-1-proxy-seta", true);

        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlReady(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);
        Assert.assertNotNull(proxyUpdateControl.getResource().get().getStatus().getLastApplied());
    }

    @Test
    public void testDefineProxySetWithDefaultName() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                proxy:
                    sets:
                      setz: {}
                      proxy: {}
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<Proxy> proxyUpdateControl = invokeController(spec, new Proxy(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 1);
        // verify order of sets follows the order declared in the spec
        Assert.assertNotNull(client.getCreatedResource(Deployment.class, "pulsar-spec-1-proxy-setz"));
        ProxyController.ProxySetsLastApplied proxySetsLastApplied =
                SerializationUtil.readJson(proxyUpdateControl.getResource().get().getStatus().getLastApplied(),
                        ProxyController.ProxySetsLastApplied.class);
        Assert.assertNotNull(proxySetsLastApplied.getSets().get("setz"));
        Assert.assertEquals(client.getDeletedResources().size(), 0);

        resolver.putDeployment("pulsar-spec-1-proxy-setz", true);

        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlInitializing(proxyUpdateControl);
        proxySetsLastApplied =
                SerializationUtil.readJson(proxyUpdateControl.getResource().get().getStatus().getLastApplied(),
                        ProxyController.ProxySetsLastApplied.class);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 1);
        Assert.assertNotNull(proxySetsLastApplied.getSets().get("proxy"));
        Assert.assertEquals(client.getDeletedResources().size(), 0);

        resolver.putDeployment("pulsar-spec-1-proxy", true);

        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlReady(proxyUpdateControl);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);

        spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                proxy:
                    sets:
                      setz: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlReady(proxyUpdateControl);
        proxySetsLastApplied =
                SerializationUtil.readJson(proxyUpdateControl.getResource().get().getStatus().getLastApplied(),
                        ProxyController.ProxySetsLastApplied.class);
        Assert.assertNull(proxySetsLastApplied.getSets().get("proxy"));
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);
        Assert.assertEquals(client.getCreatedResources(Service.class).size(), 1);
        Assert.assertNotNull(client.getCreatedResource(Service.class, "pulsar-spec-1-proxy"));
        Assert.assertEquals(client.getDeletedResources().size(), 4);
        Assert.assertNotNull(client.getDeletedResource(PodDisruptionBudget.class, "pulsar-spec-1-proxy"));
        Assert.assertNotNull(client.getDeletedResource(Deployment.class, "pulsar-spec-1-proxy"));
        Assert.assertNotNull(client.getDeletedResource(ConfigMap.class, "pulsar-spec-1-proxy"));
        Assert.assertNotNull(client.getDeletedResource(ConfigMap.class, "pulsar-spec-1-proxy-ws"));

        spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                proxy:
                    replicas: 0
                    sets: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        proxyUpdateControl = invokeController(spec, proxyUpdateControl.getResource().get(), client);
        KubeTestUtil.assertUpdateControlReady(proxyUpdateControl);
        proxySetsLastApplied =
                SerializationUtil.readJson(proxyUpdateControl.getResource().get().getStatus().getLastApplied(),
                        ProxyController.ProxySetsLastApplied.class);
        Assert.assertNull(proxySetsLastApplied.getSets().get("setz"));
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 0);
        Assert.assertEquals(client.getDeletedResources().size(), 6);
        Assert.assertNotNull(client.getDeletedResource(Service.class, "pulsar-spec-1-proxy-setz"));
        Assert.assertNotNull(client.getDeletedResource(PodDisruptionBudget.class, "pulsar-spec-1-proxy-setz"));
        Assert.assertNotNull(client.getDeletedResource(Deployment.class, "pulsar-spec-1-proxy-setz"));
        Assert.assertNotNull(client.getDeletedResource(ConfigMap.class, "pulsar-spec-1-proxy-setz"));
        Assert.assertNotNull(client.getDeletedResource(ConfigMap.class, "pulsar-spec-1-proxy-setz-ws"));
    }


}