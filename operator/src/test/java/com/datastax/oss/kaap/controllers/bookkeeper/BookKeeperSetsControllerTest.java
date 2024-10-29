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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.datastax.oss.kaap.autoscaler.bookkeeper.BookieAdminClient;
import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.AbstractController;
import com.datastax.oss.kaap.controllers.ControllerTestUtil;
import com.datastax.oss.kaap.controllers.KubeTestUtil;
import com.datastax.oss.kaap.controllers.bookkeeper.racks.BookKeeperRackDaemon;
import com.datastax.oss.kaap.controllers.bookkeeper.racks.client.BkRackClient;
import com.datastax.oss.kaap.controllers.bookkeeper.racks.client.BkRackClientFactory;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeper;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperAutoRackConfig;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
import com.datastax.oss.kaap.mocks.MockResourcesResolver;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@JBossLog
public class BookKeeperSetsControllerTest {

    static final String NAMESPACE = "ns";
    static final String CLUSTER_NAME = "pulsar-spec-1";
    private BookieAdminClient bookieAdminClient;
    private final ControllerTestUtil<BookKeeperFullSpec, BookKeeper> controllerTestUtil =
            new ControllerTestUtil<>(NAMESPACE, CLUSTER_NAME, this::controllerConstructor);

    @BeforeMethod(alwaysRun = true)
    public void beforeMethod() {
        bookieAdminClient = Mockito.mock(BookieAdminClient.class);
    }

    @Test
    public void testBookKeeperSetsDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    setsUpdateStrategy: Parallel
                    sets:
                      set1: {}
                      set2: {}
                """;
        MockKubernetesClient client = invokeController(spec);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 2);

        assertStsEqualsDefault("set1",
                client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-set1").getResource());

        assertStsEqualsDefault("set2",
                client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).size(), 2);
        assertConfigMapEqualsDefault("set1",
                client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-bookkeeper-set1").getResource());
        assertConfigMapEqualsDefault("set2",
                client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-bookkeeper-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(PodDisruptionBudget.class).size(), 2);

        assertPdbEqualsDefault("set1",
                client.getCreatedResource(PodDisruptionBudget.class, "pulsar-spec-1-bookkeeper-set1").getResource());
        assertPdbEqualsDefault("set2",
                client.getCreatedResource(PodDisruptionBudget.class, "pulsar-spec-1-bookkeeper-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(Service.class).size(), 3);

        assertServiceEqualsDefault("set1",
                client.getCreatedResource(Service.class, "pulsar-spec-1-bookkeeper-set1").getResource());
        assertServiceEqualsDefault("set2",
                client.getCreatedResource(Service.class, "pulsar-spec-1-bookkeeper-set2").getResource());
    }


    @Test
    public void testBookKeeperSetsDefaultName() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    setsUpdateStrategy: Parallel
                    sets:
                      bookkeeper:
                        config:
                            myconfig: thevalue
                """;
        MockKubernetesClient client = invokeController(spec);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).size(), 1);
        Assert.assertEquals(client.getCreatedResource(ConfigMap.class).getResource()
                        .getData().get("PULSAR_PREFIX_myconfig"), "thevalue");
        Assert.assertEquals(client.getCreatedResources(PodDisruptionBudget.class).size(), 1);
        System.out.println(client.getCreatedResources(Service.class).stream().map(d -> d.getResource().getMetadata().getName()).collect(
                Collectors.toList()));
        Assert.assertEquals(client.getCreatedResources(Service.class).size(), 2);
    }


    @Test
    public void testOverride() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
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

        Assert.assertEquals(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-set1")
                .getResource()
                .getSpec()
                .getReplicas(), 3);

        Assert.assertEquals(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-set2")
                .getResource()
                .getSpec()
                .getReplicas(), 6);

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-bookkeeper-set1")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_common"), "commonvalue");

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-bookkeeper-set2")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_common"), "override");

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-bookkeeper-set1")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_myname"), "set1");

        Assert.assertNull(client.getCreatedResource(ConfigMap.class, "pulsar-spec-1-bookkeeper-set2")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_myname"));

        Assert.assertEquals(client.getCreatedResource(PodDisruptionBudget.class, "pulsar-spec-1-bookkeeper-set1")
                .getResource()
                .getSpec()
                .getMaxUnavailable()
                .getIntVal(), 2);

        Assert.assertEquals(client.getCreatedResource(PodDisruptionBudget.class, "pulsar-spec-1-bookkeeper-set2")
                .getResource()
                .getSpec()
                .getMaxUnavailable()
                .getIntVal(), 1);

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsar-spec-1-bookkeeper-set1")
                .getResource()
                .getMetadata()
                .getAnnotations().get("externaldns"), "myset1");

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsar-spec-1-bookkeeper-set1")
                .getResource()
                .getSpec()
                .getPorts().size(), 2);

        Assert.assertNull(client.getCreatedResource(Service.class, "pulsar-spec-1-bookkeeper-set2")
                .getResource()
                .getMetadata()
                .getAnnotations().get("externaldns"));

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsar-spec-1-bookkeeper-set2")
                .getResource()
                .getSpec()
                .getPorts().size(), 1);
    }

    private void assertStsEqualsDefault(String setName, StatefulSet sts) {
        final StatefulSet defaultSts = invokeController("""
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(StatefulSet.class).getResource();
        final String resourceName = CLUSTER_NAME + "-bookkeeper-" + setName;
        Assert.assertEquals(sts.getMetadata().getName(), resourceName);
        assertResourceSetLabel(sts, setName);
        Assert.assertEquals(sts.getSpec().getServiceName(), resourceName);
        Assert.assertEquals(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getEnvFrom().get(0)
                .getConfigMapRef().getName(), resourceName);

        Assert.assertEquals(sts.getSpec().getTemplate().getSpec().getInitContainers().get(0).getEnvFrom().get(0)
                .getConfigMapRef().getName(), resourceName);


        Assert.assertNotNull(KubeTestUtil.getVolumeMountByName(
                sts.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts(),
                resourceName + "-journal"
        ), resourceName);

        Assert.assertNotNull(KubeTestUtil.getVolumeMountByName(
                sts.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts(),
                resourceName + "-ledgers"
        ), resourceName);

        sts.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().clear();

        Assert.assertEquals(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getName(),
                CLUSTER_NAME + "-bookkeeper");

        Assert.assertEquals(sts.getSpec().getVolumeClaimTemplates().get(0).getMetadata().getName(),
                resourceName + "-journal");
        Assert.assertEquals(sts.getSpec().getVolumeClaimTemplates().get(1).getMetadata().getName(),
                resourceName + "-ledgers");

        sts.getSpec().getSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);
        sts.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        sts.getSpec().getTemplate().getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        sts.getSpec().getTemplate().getSpec().getAffinity().getPodAntiAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .get(0)
                .getLabelSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);
        sts.getSpec().getVolumeClaimTemplates()
                .forEach(v -> v.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET));


        final String defaultResourceName = defaultSts.getMetadata().getName();
        sts.getMetadata().setName(defaultResourceName);
        sts.getSpec().setServiceName(defaultResourceName);
        sts.getSpec().getTemplate().getSpec().getContainers().get(0).setName(defaultResourceName);
        sts.getSpec().getTemplate().getSpec().getContainers().get(0).getEnvFrom().get(0)
                .getConfigMapRef().setName(defaultResourceName);

        sts.getSpec().getTemplate().getSpec().getInitContainers().get(0).getEnvFrom().get(0)
                .getConfigMapRef().setName(defaultResourceName);
        sts.getSpec().getVolumeClaimTemplates().get(0).getMetadata().setName(defaultResourceName + "-journal");
        sts.getSpec().getVolumeClaimTemplates().get(1).getMetadata().setName(defaultResourceName + "-ledgers");

        defaultSts.getSpec().getSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);
        defaultSts.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        defaultSts.getSpec().getTemplate().getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET);
        defaultSts.getSpec().getTemplate().getSpec().getAffinity().getPodAntiAffinity()
                .getRequiredDuringSchedulingIgnoredDuringExecution()
                .get(0)
                .getLabelSelector().getMatchLabels().remove(CRDConstants.LABEL_RESOURCESET);
        defaultSts.getSpec().getVolumeClaimTemplates()
                .forEach(v -> v.getMetadata().getLabels().remove(CRDConstants.LABEL_RESOURCESET));

        defaultSts.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().clear();

        Assert.assertEquals(SerializationUtil.writeAsYaml(sts), SerializationUtil.writeAsYaml(defaultSts));
    }

    private void assertConfigMapEqualsDefault(String setName, ConfigMap cmap) {
        final ConfigMap defaultCmap = invokeController("""
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(ConfigMap.class).getResource();
        final String resourceName = CLUSTER_NAME + "-bookkeeper-" + setName;
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
        final String resourceName = CLUSTER_NAME + "-bookkeeper-" + setName;
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
        final String resourceName = CLUSTER_NAME + "-bookkeeper-" + setName;
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
                        BookKeeper.class,
                        BookKeeperFullSpec.class,
                        BookKeeperController.class);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        return controllerTestUtil
                .invokeController(spec,
                        BookKeeper.class,
                        BookKeeperFullSpec.class,
                        BookKeeperController.class);
    }

    @SneakyThrows
    private UpdateControl<BookKeeper> invokeController(String spec, BookKeeper lastCr, MockKubernetesClient client) {
        return controllerTestUtil.invokeController(client, spec, lastCr, BookKeeperFullSpec.class,
                BookKeeperController.class);
    }


    @Test
    public void testGetBookKeeperSetSpecs() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
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

        final BookKeeperFullSpec bookkeeperFullSpec = SerializationUtil.readYaml(spec, BookKeeperFullSpec.class);

        final LinkedHashMap<String, BookKeeperSetSpec> sets =
                BookKeeperController.getBookKeeperSetSpecs(bookkeeperFullSpec);
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
                bookkeeper:
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
                        client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-set1norack")
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
                                component: bookkeeper
                                resource-set: set1norack
                            topologyKey: kubernetes.io/hostname
                        """);

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-set2norack")
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
                                  component: bookkeeper
                                  resource-set: set2norack
                              topologyKey: kubernetes.io/hostname
                            weight: 100
                          - podAffinityTerm:
                              labelSelector:
                                matchLabels:
                                  app: pulsar
                                  cluster: pulsar-spec-1
                                  component: bookkeeper
                                  resource-set: set2norack
                              topologyKey: failure-domain.beta.kubernetes.io/zone
                            weight: 100
                        """);

        Assert.assertNull(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-setdefault")
                .getResource()
                .getSpec()
                .getTemplate().getSpec().getAffinity()
        );

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-set1")
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
                                component: bookkeeper
                                rack: rack1
                                resource-set: set1
                            topologyKey: kubernetes.io/hostname
                        """);

        Assert.assertEquals(SerializationUtil.writeAsYaml(
                        client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-set2")
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
                        client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-set3")
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
                                component: bookkeeper
                                rack: rack3
                                resource-set: set3
                            topologyKey: kubernetes.io/hostname
                        """);
    }


    @Test
    public void testRollingUpdate() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    sets:
                      setz: {}
                      seta: {}
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<BookKeeper> bookkeeperUpdateControl = invokeController(spec, new BookKeeper(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        // verify order of sets follows the order declared in the spec
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-setz"));
        BookKeeperController.BookKeeperSetsLastApplied setsLastApplied =
                SerializationUtil.readJson(bookkeeperUpdateControl.getResource().getStatus().getLastApplied(),
                        BookKeeperController.BookKeeperSetsLastApplied.class);
        Assert.assertNotNull(setsLastApplied.getSets().get("setz"));


        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        setsLastApplied =
                SerializationUtil.readJson(bookkeeperUpdateControl.getResource().getStatus().getLastApplied(),
                        BookKeeperController.BookKeeperSetsLastApplied.class);
        Assert.assertNotNull(setsLastApplied.getSets().get("setz"));


        resolver.putResource("pulsar-spec-1-bookkeeper-setz",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-setz", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        setsLastApplied =
                SerializationUtil.readJson(bookkeeperUpdateControl.getResource().getStatus().getLastApplied(),
                        BookKeeperController.BookKeeperSetsLastApplied.class);
        Assert.assertNotNull(setsLastApplied.getSets().get("seta"));

        resolver.putResource("pulsar-spec-1-bookkeeper-seta",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-seta", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(bookkeeperUpdateControl.getResource().getStatus().getLastApplied());

        // now update

        resolver.putResource("pulsar-spec-1-bookkeeper-seta",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-seta", false).build());
        resolver.putResource("pulsar-spec-1-bookkeeper-setz",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-setz", false).build());

        spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    config:
                        newvalue: true
                    sets:
                      setz: {}
                      seta: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-setz"));


        resolver.putResource("pulsar-spec-1-bookkeeper-setz",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-setz", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-seta"));

        resolver.putResource("pulsar-spec-1-bookkeeper-seta",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-seta", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);

    }

    @Test
    public void testParallelUpdate() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    setsUpdateStrategy: Parallel
                    sets:
                      setz: {}
                      seta: {}
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<BookKeeper> bookkeeperUpdateControl = invokeController(spec, new BookKeeper(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 2);
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-setz"));
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-seta"));
        Assert.assertNotNull(bookkeeperUpdateControl.getResource().getStatus().getLastApplied());


        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(bookkeeperUpdateControl.getResource().getStatus().getLastApplied());

        resolver.putResource("pulsar-spec-1-bookkeeper-setz",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-setz", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(bookkeeperUpdateControl.getResource().getStatus().getLastApplied());

        resolver.putResource("pulsar-spec-1-bookkeeper-seta",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-seta", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(bookkeeperUpdateControl.getResource().getStatus().getLastApplied());

        // now update

        resolver.putResource("pulsar-spec-1-bookkeeper-seta",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-seta", false).build());
        resolver.putResource("pulsar-spec-1-bookkeeper-setz",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-setz", false).build());

        spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    setsUpdateStrategy: Parallel
                    config:
                        newvalue: true
                    sets:
                      setz: {}
                      seta: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 2);
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-setz"));
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-seta"));
        Assert.assertNotNull(bookkeeperUpdateControl.getResource().getStatus().getLastApplied());


        resolver.putResource("pulsar-spec-1-bookkeeper-setz",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-setz", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(bookkeeperUpdateControl.getResource().getStatus().getLastApplied());

        resolver.putResource("pulsar-spec-1-bookkeeper-seta",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-seta", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertNotNull(bookkeeperUpdateControl.getResource().getStatus().getLastApplied());
    }

    @Test
    public void testDefineBookKeeperSetWithDefaultName() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    sets:
                      setz: {}
                      bookkeeper: {}
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<BookKeeper> bookkeeperUpdateControl = invokeController(spec, new BookKeeper(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        BookKeeperController.BookKeeperSetsLastApplied setsLastApplied =
                SerializationUtil.readJson(bookkeeperUpdateControl.getResource().getStatus().getLastApplied(),
                        BookKeeperController.BookKeeperSetsLastApplied.class);
        Assert.assertNotNull(setsLastApplied.getSets().get("setz"));
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        // verify order of sets follows the order declared in the spec
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-setz"));
        Assert.assertEquals(client.getDeletedResources().size(), 0);

        resolver.putResource("pulsar-spec-1-bookkeeper-setz",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper-setz", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        setsLastApplied =
                SerializationUtil.readJson(bookkeeperUpdateControl.getResource().getStatus().getLastApplied(),
                        BookKeeperController.BookKeeperSetsLastApplied.class);
        Assert.assertNotNull(setsLastApplied.getSets().get("bookkeeper"));
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        Assert.assertEquals(client.getDeletedResources().size(), 0);

        resolver.putResource("pulsar-spec-1-bookkeeper",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper", true).build());

        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertEquals(client.getDeletedResources().size(), 0);

        spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    sets:
                      setz: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(bookkeeperUpdateControl);
        setsLastApplied =
                SerializationUtil.readJson(bookkeeperUpdateControl.getResource().getStatus().getLastApplied(),
                        BookKeeperController.BookKeeperSetsLastApplied.class);
        Assert.assertNull(setsLastApplied.getSets().get("bookkeeper"));

        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertEquals(client.getCreatedResources(Service.class).size(), 1);
        Assert.assertNotNull(client.getCreatedResource(Service.class, "pulsar-spec-1-bookkeeper"));
        Assert.assertEquals(client.getDeletedResources().size(), 4);
        Assert.assertNotNull(client.getDeletedResource(PodDisruptionBudget.class, "pulsar-spec-1-bookkeeper"));
        Assert.assertNotNull(client.getDeletedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper"));
        Assert.assertNotNull(client.getDeletedResource(ConfigMap.class, "pulsar-spec-1-bookkeeper"));
        Assert.assertNotNull(client.getDeletedResource(StorageClass.class, "pulsar-spec-1-bookkeeper"));

        spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    replicas: 0
                    sets: {}
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(bookkeeperUpdateControl);
        setsLastApplied =
                SerializationUtil.readJson(bookkeeperUpdateControl.getResource().getStatus().getLastApplied(),
                        BookKeeperController.BookKeeperSetsLastApplied.class);
        Assert.assertNull(setsLastApplied.getSets().get("setz"));
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);

        Assert.assertEquals(client.getDeletedResources().size(), 6);
        Assert.assertNotNull(client.getDeletedResource(Service.class, "pulsar-spec-1-bookkeeper-setz"));
        Assert.assertNotNull(client.getDeletedResource(PodDisruptionBudget.class, "pulsar-spec-1-bookkeeper-setz"));
        Assert.assertNotNull(client.getDeletedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper-setz"));
        Assert.assertNotNull(client.getDeletedResource(ConfigMap.class, "pulsar-spec-1-bookkeeper-setz"));
        Assert.assertNotNull(client.getDeletedResource(StorageClass.class, "pulsar-spec-1-bookkeeper-setz"));
    }


    @Test
    public void testCleanupPvc() throws Exception {
        String spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    replicas: 4
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<BookKeeper> bookkeeperUpdateControl = invokeController(spec, new BookKeeper(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 1);
        Assert.assertNotNull(client.getCreatedResource(StatefulSet.class, "pulsar-spec-1-bookkeeper"));
        Assert.assertEquals(client.getDeletedResources().size(), 0);

        resolver.putResource("pulsar-spec-1-bookkeeper",
                resolver.newStatefulSetBuilder("pulsar-spec-1-bookkeeper", true).build());

        resolver.putResource("pulsar-spec-1-bookkeeper-journal-0", genPvc());
        resolver.putResource("pulsar-spec-1-bookkeeper-ledgers-0", genPvc());
        resolver.putResource("pulsar-spec-1-bookkeeper-journal-1", genPvc());
        resolver.putResource("pulsar-spec-1-bookkeeper-ledgers-1", genPvc());
        resolver.putResource("pulsar-spec-1-bookkeeper-journal-2", genPvc());
        resolver.putResource("pulsar-spec-1-bookkeeper-ledgers-2", genPvc());
        resolver.putResource("pulsar-spec-1-bookkeeper-journal-3", genPvc());
        resolver.putResource("pulsar-spec-1-bookkeeper-ledgers-3", genPvc());
        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(bookkeeperUpdateControl);
        Assert.assertEquals(client.getCreatedResources(StatefulSet.class).size(), 0);
        Assert.assertEquals(client.getDeletedResources().size(), 0);

        spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    replicas: 2
                """;
        mockBookieAdminClient(3);
        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(bookkeeperUpdateControl);
        Assert.assertEquals(client.getDeletedResources().size(), 4);
        Assert.assertNotNull(client.getDeletedResource(PersistentVolumeClaim.class, "pulsar-spec-1-bookkeeper-ledgers-2"));
        Assert.assertNotNull(client.getDeletedResource(PersistentVolumeClaim.class, "pulsar-spec-1-bookkeeper-journal-2"));
        Assert.assertNotNull(client.getDeletedResource(PersistentVolumeClaim.class, "pulsar-spec-1-bookkeeper-ledgers-3"));
        Assert.assertNotNull(client.getDeletedResource(PersistentVolumeClaim.class, "pulsar-spec-1-bookkeeper-journal-3"));


        spec = """
                global:
                    name: pulsar-spec-1
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    cleanUpPvcs: false
                    replicas: 1
                """;
        mockBookieAdminClient(2);
        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlReady(bookkeeperUpdateControl);
        Assert.assertEquals(client.getDeletedResources().size(), 0);

    }

    private PersistentVolumeClaim genPvc() {
        return new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withLabels(Map.of(CRDConstants.LABEL_APP, "pulsar",
                        CRDConstants.LABEL_CLUSTER, "pulsar-spec-1",
                        CRDConstants.LABEL_COMPONENT, "bookkeeper",
                        CRDConstants.LABEL_RESOURCESET, "bookkeeper"))
                .endMetadata()
                .build();
    }


    private AbstractController<BookKeeper> controllerConstructor(
            ControllerTestUtil<BookKeeperFullSpec, BookKeeper>.ControllerConstructorInput controllerConstructorInput) {
        return new BookKeeperController(controllerConstructorInput.getClient()) {

            @Override
            protected BookieAdminClient createBookieAdminClient(String namespace, String setName,
                                                                BookKeeperFullSpec lastApplied) {
                return bookieAdminClient;
            }

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


    @Test
    public void testDownscaling() throws Exception {
        String spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    replicas: 5
                """;
        MockResourcesResolver resolver = new MockResourcesResolver();
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, resolver);
        UpdateControl<BookKeeper> bookkeeperUpdateControl = invokeController(spec, new BookKeeper(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals((int) client.getCreatedResource(StatefulSet.class).getResource().getSpec().getReplicas(),
                5);

        mockBookieAdminClient(5);
        spec = """
                global:
                    name: pul
                    persistence: false
                    image: apachepulsar/pulsar:global
                bookkeeper:
                    replicas: 3
                """;
        client = new MockKubernetesClient(NAMESPACE, resolver);
        bookkeeperUpdateControl = invokeController(spec, bookkeeperUpdateControl.getResource(), client);
        KubeTestUtil.assertUpdateControlInitializing(bookkeeperUpdateControl);
        Assert.assertEquals((int) client.getCreatedResource(StatefulSet.class).getResource().getSpec().getReplicas(),
                3);
    }

    private void mockBookieAdminClient(int replicas) {
        final List<BookieAdminClient.BookieInfo> bookieInfos = new ArrayList<>();
        for (int i = 0; i < replicas; i++) {
            bookieInfos.add(genBookieInfo("pul-bookkeeper-" + i));
        }
        when(bookieAdminClient.collectBookieInfos()).thenReturn(bookieInfos);
        when(bookieAdminClient.doesNotHaveUnderReplicatedLedgers()).thenReturn(true);
        when(bookieAdminClient.existsLedger(any())).thenReturn(false);
    }

    private BookieAdminClient.BookieInfo genBookieInfo(String bookieId) {
        final PodResource pod = Mockito.mock(PodResource.class);
        when(pod.get()).thenReturn(new PodBuilder()
                .withNewMetadata()
                .withName(bookieId)
                .endMetadata()
                .build());
        return BookieAdminClient.BookieInfo.builder()
                .bookieId(bookieId)
                .podResource(pod)
                .build();
    }
}
