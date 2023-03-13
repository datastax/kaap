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
package com.datastax.oss.pulsaroperator.controllers.broker;

import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import com.datastax.oss.pulsaroperator.controllers.ControllerTestUtil;
import com.datastax.oss.pulsaroperator.controllers.KubeTestUtil;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerFullSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import com.datastax.oss.pulsaroperator.mocks.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.mocks.MockResourcesResolver;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.LinkedHashMap;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.Test;

@JBossLog
public class BrokerSetsControllerTest {

    static final String NAMESPACE = "ns";
    static final String CLUSTER_NAME = "pulsarname";
    private final ControllerTestUtil<BrokerFullSpec, Broker> controllerTestUtil =
            new ControllerTestUtil<>(NAMESPACE, CLUSTER_NAME);

    @Test
    public void testBrokerSetsDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                broker:
                    setsUpdateStrategy: Parallel
                    sets:
                      set1: {}
                      set2: {}
                """;
        MockKubernetesClient client = invokeController(spec);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 2);

        assertStsEqualsDefault("set1",
                client.getCreatedResource(StatefulSet.class, "pulsarname-broker-set1").getResource());

        assertStsEqualsDefault("set2",
                client.getCreatedResource(StatefulSet.class, "pulsarname-broker-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).size(), 2);
        assertConfigMapEqualsDefault("set1",
                client.getCreatedResource(ConfigMap.class, "pulsarname-broker-set1").getResource());
        assertConfigMapEqualsDefault("set2",
                client.getCreatedResource(ConfigMap.class, "pulsarname-broker-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(PodDisruptionBudget.class).size(), 2);

        assertPdbEqualsDefault("set1",
                client.getCreatedResource(PodDisruptionBudget.class, "pulsarname-broker-set1").getResource());
        assertPdbEqualsDefault("set2",
                client.getCreatedResource(PodDisruptionBudget.class, "pulsarname-broker-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(Service.class).size(), 3);

        assertServiceEqualsDefault("set1",
                client.getCreatedResource(Service.class, "pulsarname-broker-set1").getResource());
        assertServiceEqualsDefault("set2",
                client.getCreatedResource(Service.class, "pulsarname-broker-set2").getResource());
    }


    @Test
    public void testOverride() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                broker:
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
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 2);

        Assert.assertEquals(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-set1")
                .getResource()
                .getSpec()
                .getReplicas(), 3);

        Assert.assertEquals(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-set2")
                .getResource()
                .getSpec()
                .getReplicas(), 6);

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsarname-broker-set1")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_common"), "commonvalue");

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsarname-broker-set2")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_common"), "override");

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsarname-broker-set1")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_myname"), "set1");

        Assert.assertNull(client.getCreatedResource(ConfigMap.class, "pulsarname-broker-set2")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_myname"));

        Assert.assertEquals(client.getCreatedResource(PodDisruptionBudget.class, "pulsarname-broker-set1")
                .getResource()
                .getSpec()
                .getMaxUnavailable()
                .getIntVal(), 2);

        Assert.assertEquals(client.getCreatedResource(PodDisruptionBudget.class, "pulsarname-broker-set2")
                .getResource()
                .getSpec()
                .getMaxUnavailable()
                .getIntVal(), 1);

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsarname-broker-set1")
                .getResource()
                .getMetadata()
                .getAnnotations().get("externaldns"), "myset1");

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsarname-broker-set1")
                .getResource()
                .getSpec()
                .getPorts().size(), 3);

        Assert.assertNull(client.getCreatedResource(Service.class, "pulsarname-broker-set2")
                .getResource()
                .getMetadata()
                .getAnnotations());

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsarname-broker-set2")
                .getResource()
                .getSpec()
                .getPorts().size(), 2);
    }

    private void assertStsEqualsDefault(String setName, StatefulSet sts) {
        final StatefulSet defaultSts = invokeController("""
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(StatefulSet.class).getResource();
        final String resourceName = CLUSTER_NAME + "-broker-" + setName;
        Assert.assertEquals(sts.getMetadata().getName(), resourceName);
        assertResourceSetLabel(sts, setName);
        Assert.assertEquals(sts.getSpec().getServiceName(), resourceName);
        Assert.assertEquals(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getEnvFrom().get(0)
                .getConfigMapRef().getName(), resourceName);

        Assert.assertEquals(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getName(), resourceName);

        sts.getSpec().getSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);
        sts.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        sts.getSpec().getTemplate().getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        sts.getSpec().getTemplate().getSpec().getAffinity().getPodAntiAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .get(0)
                .getLabelSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);


        final String defaultResourceName = defaultSts.getMetadata().getName();
        sts.getMetadata().setName(defaultResourceName);
        sts.getSpec().setServiceName(defaultResourceName);
        sts.getSpec().getTemplate().getSpec().getContainers().get(0).setName(defaultResourceName);
        sts.getSpec().getTemplate().getSpec().getContainers().get(0).getEnvFrom().get(0)
                .getConfigMapRef().setName(defaultResourceName);

        defaultSts.getSpec().getSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);
        defaultSts.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        defaultSts.getSpec().getTemplate().getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        defaultSts.getSpec().getTemplate().getSpec().getAffinity().getPodAntiAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .get(0)
                .getLabelSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);

        Assert.assertEquals(SerializationUtil.writeAsYaml(sts), SerializationUtil.writeAsYaml(defaultSts));
    }

    private void assertConfigMapEqualsDefault(String setName, ConfigMap cmap) {
        final ConfigMap defaultCmap = invokeController("""
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(ConfigMap.class).getResource();
        final String resourceName = CLUSTER_NAME + "-broker-" + setName;
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
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(PodDisruptionBudget.class).getResource();
        final String resourceName = CLUSTER_NAME + "-broker-" + setName;
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
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(Service.class).getResource();
        final String resourceName = CLUSTER_NAME + "-broker-" + setName;
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

    private void assertResourceSetLabel(StatefulSet sts, String value) {
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
    private UpdateControl<Broker> invokeController(String spec, Broker lastCr, MockKubernetesClient client) {
        return controllerTestUtil.invokeController(client, spec, lastCr, BrokerFullSpec.class, BrokerController.class);
    }


    @Test
    public void testGetBrokerSetSpecs() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                broker:
                    replicas: 6
                    config:
                        common: commonvalue
                    sets:
                      set1:
                        replicas: 3
                        config:
                            myname: set1
                        pdb:
                            maxUnavailable: 2
                      set2:
                        config:
                            common: override
                """;

        final BrokerFullSpec brokerFullSpec = SerializationUtil.readYaml(spec, BrokerFullSpec.class);

        final LinkedHashMap<String, BrokerSetSpec> sets = BrokerController.getBrokerSetSpecs(brokerFullSpec);
        Assert.assertEquals(SerializationUtil.writeAsYaml(sets),
                """
                        ---
                        set1:
                          replicas: 3
                          pdb:
                            maxUnavailable: 2
                          config:
                            common: commonvalue
                            myname: set1
                        set2:
                          replicas: 6
                          config:
                            common: override
                        """);
    }


    @Test
    public void testRacks() throws Exception {
        String spec = """
                global:
                    name: pulsarname
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
                broker:
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
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 6);

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(StatefulSet.class, "pulsarname-broker-set1norack")
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
                                cluster: pulsarname
                                component: broker
                                resource-set: set1norack
                            topologyKey: kubernetes.io/hostname
                        """);

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(StatefulSet.class, "pulsarname-broker-set2norack")
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
                                  cluster: pulsarname
                                  component: broker
                                  resource-set: set2norack
                              topologyKey: kubernetes.io/hostname
                            weight: 100
                          - podAffinityTerm:
                              labelSelector:
                                matchLabels:
                                  app: pulsar
                                  cluster: pulsarname
                                  component: broker
                                  resource-set: set2norack
                              topologyKey: failure-domain.beta.kubernetes.io/zone
                            weight: 100
                        """);

        Assert.assertNull(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-setdefault")
                .getResource()
                .getSpec()
                .getTemplate().getSpec().getAffinity()
        );

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(StatefulSet.class, "pulsarname-broker-set1")
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
                                cluster: pulsarname
                                component: broker
                                rack: rack1
                                resource-set: set1
                            topologyKey: kubernetes.io/hostname
                        """);

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(StatefulSet.class, "pulsarname-broker-set2")
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
                        client.getCreatedResource(StatefulSet.class, "pulsarname-broker-set3")
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
                                cluster: pulsarname
                                component: broker
                                rack: rack3
                                resource-set: set3
                            topologyKey: kubernetes.io/hostname
                        """);
    }


    @Test
    public void testRollingUpdate() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                broker:
                    sets:
                      setz: {}
                      seta: {}
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<Broker> brokerUpdateControl = invokeController(spec, new Broker(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        BrokerController.BrokerSetsLastApplied setsLastApplied =
                SerializationUtil.readJson(brokerUpdateControl.getResource().getStatus().getLastApplied(),
                        BrokerController.BrokerSetsLastApplied.class);
        Assert.assertNotNull(setsLastApplied.getSets().get("setz"));
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        // verify order of sets follows the order declared in the spec
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-setz"));


        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);

        resolver.putResource("pulsarname-broker-setz",
                resolver.newStatefulSetBuilder("pulsarname-broker-setz", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);

        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        setsLastApplied =
                SerializationUtil.readJson(brokerUpdateControl.getResource().getStatus().getLastApplied(),
                        BrokerController.BrokerSetsLastApplied.class);
        Assert.assertNotNull(setsLastApplied.getSets().get("seta"));

        resolver.putResource("pulsarname-broker-seta",
                resolver.newStatefulSetBuilder("pulsarname-broker-seta", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(brokerUpdateControl.getResource().getStatus().getLastApplied());

        // now update

        resolver.putResource("pulsarname-broker-seta",
                resolver.newStatefulSetBuilder("pulsarname-broker-seta", false).build());
        resolver.putResource("pulsarname-broker-setz",
                resolver.newStatefulSetBuilder("pulsarname-broker-setz", false).build());

        spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                broker:
                    config:
                        newvalue: true
                    sets:
                      setz: {}
                      seta: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-setz"));



        resolver.putResource("pulsarname-broker-setz",
                resolver.newStatefulSetBuilder("pulsarname-broker-setz", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-seta"));

        resolver.putResource("pulsarname-broker-seta",
                resolver.newStatefulSetBuilder("pulsarname-broker-seta", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);

    }

    @Test
    public void testParallelUpdate() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                broker:
                    setsUpdateStrategy: Parallel
                    sets:
                      setz: {}
                      seta: {}
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<Broker> brokerUpdateControl = invokeController(spec, new Broker(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 2);
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-setz"));
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-seta"));
        Assert.assertNotNull(brokerUpdateControl.getResource().getStatus().getLastApplied());


        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(brokerUpdateControl.getResource().getStatus().getLastApplied());

        resolver.putResource("pulsarname-broker-setz",
                resolver.newStatefulSetBuilder("pulsarname-broker-setz", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(brokerUpdateControl.getResource().getStatus().getLastApplied());

        resolver.putResource("pulsarname-broker-seta",
                resolver.newStatefulSetBuilder("pulsarname-broker-seta", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(brokerUpdateControl.getResource().getStatus().getLastApplied());

        // now update

        resolver.putResource("pulsarname-broker-seta",
                resolver.newStatefulSetBuilder("pulsarname-broker-seta", false).build());
        resolver.putResource("pulsarname-broker-setz",
                resolver.newStatefulSetBuilder("pulsarname-broker-setz", false).build());

        spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                broker:
                    setsUpdateStrategy: Parallel
                    config:
                        newvalue: true
                    sets:
                      setz: {}
                      seta: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 2);
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-setz"));
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-seta"));
        Assert.assertNotNull(brokerUpdateControl.getResource().getStatus().getLastApplied());


        resolver.putResource("pulsarname-broker-setz",
                resolver.newStatefulSetBuilder("pulsarname-broker-setz", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(brokerUpdateControl.getResource().getStatus().getLastApplied());

        resolver.putResource("pulsarname-broker-seta",
                resolver.newStatefulSetBuilder("pulsarname-broker-seta", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(brokerUpdateControl.getResource().getStatus().getLastApplied());
    }

    @Test
    public void testDefineBrokerSetWithDefaultName() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                broker:
                    sets:
                      setz: {}
                      broker: {}
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<Broker> brokerUpdateControl = invokeController(spec, new Broker(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        BrokerController.BrokerSetsLastApplied setsLastApplied =
                SerializationUtil.readJson(brokerUpdateControl.getResource().getStatus().getLastApplied(),
                        BrokerController.BrokerSetsLastApplied.class);
        Assert.assertNotNull(setsLastApplied.getSets().get("setz"));
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        // verify order of sets follows the order declared in the spec
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsarname-broker-setz"));
        Assert.assertEquals(client.getDeletedResources().size(), 0);

        resolver.putResource("pulsarname-broker-setz",
                resolver.newStatefulSetBuilder("pulsarname-broker-setz", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(brokerUpdateControl);
        setsLastApplied =
                SerializationUtil.readJson(brokerUpdateControl.getResource().getStatus().getLastApplied(),
                        BrokerController.BrokerSetsLastApplied.class);
        Assert.assertNotNull(setsLastApplied.getSets().get("broker"));
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        Assert.assertEquals(client.getDeletedResources().size(), 0);

        resolver.putResource("pulsarname-broker",
                resolver.newStatefulSetBuilder("pulsarname-broker", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(brokerUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(brokerUpdateControl.getResource().getStatus().getLastApplied());
        Assert.assertEquals(client.getDeletedResources().size(), 0);

        spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                broker:
                    sets:
                      setz: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(brokerUpdateControl);
        setsLastApplied =
                SerializationUtil.readJson(brokerUpdateControl.getResource().getStatus().getLastApplied(),
                        BrokerController.BrokerSetsLastApplied.class);
        Assert.assertNull(setsLastApplied.getSets().get("broker"));
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertEquals(client.getCreatedResources(Service.class).size(), 1);
        Assert.assertNotNull(client.getCreatedResource(Service.class, "pulsarname-broker"));
        Assert.assertEquals(client.getDeletedResources().size(), 3);
        Assert.assertNotNull(client.getDeletedResource(PodDisruptionBudget.class, "pulsarname-broker"));
        Assert.assertNotNull(client.getDeletedResource(StatefulSet.class, "pulsarname-broker"));
        Assert.assertNotNull(client.getDeletedResource(ConfigMap.class, "pulsarname-broker"));

        spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                broker:
                    replicas: 0
                    sets: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        brokerUpdateControl = invokeController(spec, brokerUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(brokerUpdateControl);
        setsLastApplied =
                SerializationUtil.readJson(brokerUpdateControl.getResource().getStatus().getLastApplied(),
                        BrokerController.BrokerSetsLastApplied.class);
        Assert.assertNull(setsLastApplied.getSets().get("setz"));
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);

        Assert.assertEquals(client.getDeletedResources().size(), 5);
        Assert.assertNotNull(client.getDeletedResource(Service.class, "pulsarname-broker-setz"));
        Assert.assertNotNull(client.getDeletedResource(PodDisruptionBudget.class, "pulsarname-broker-setz"));
        Assert.assertNotNull(client.getDeletedResource(StatefulSet.class, "pulsarname-broker-setz"));
        Assert.assertNotNull(client.getDeletedResource(ConfigMap.class, "pulsarname-broker-setz"));
    }
}
