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
package com.datastax.oss.pulsaroperator.controllers.proxy;

import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import com.datastax.oss.pulsaroperator.controllers.ControllerTestUtil;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.proxy.Proxy;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxyFullSpec;
import com.datastax.oss.pulsaroperator.mocks.MockKubernetesClient;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.Test;

@JBossLog
public class ProxySetsControllerTest {

    static final String NAMESPACE = "ns";
    static final String CLUSTER_NAME = "pulsarname";
    private final ControllerTestUtil<ProxyFullSpec, Proxy> controllerTestUtil =
            new ControllerTestUtil<>(NAMESPACE, CLUSTER_NAME);

    @Test
    public void testProxySetsDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                proxy:
                    sets:
                      set1: {}
                      set2: {}
                """;
        MockKubernetesClient client = invokeController(spec);
        Assert.assertEquals(client.getCreatedResources(Deployment.class).size(), 2);

        assertDeploymentEqualsDefault("set1",
                client.getCreatedResource(Deployment.class, "pulsarname-proxy-set1").getResource());

        assertDeploymentEqualsDefault("set2",
                client.getCreatedResource(Deployment.class, "pulsarname-proxy-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(ConfigMap.class).size(), 4);
        assertConfigMapEqualsDefault("set1",
                client.getCreatedResource(ConfigMap.class, "pulsarname-proxy-set1").getResource());
        assertConfigMapEqualsDefault("set2",
                client.getCreatedResource(ConfigMap.class, "pulsarname-proxy-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(PodDisruptionBudget.class).size(), 2);

        assertPdbEqualsDefault("set1",
                client.getCreatedResource(PodDisruptionBudget.class, "pulsarname-proxy-set1").getResource());
        assertPdbEqualsDefault("set2",
                client.getCreatedResource(PodDisruptionBudget.class, "pulsarname-proxy-set2").getResource());

        Assert.assertEquals(client.getCreatedResources(Service.class).size(), 3);

        assertServiceEqualsDefault("set1",
                client.getCreatedResource(Service.class, "pulsarname-proxy-set1").getResource());
        assertServiceEqualsDefault("set2",
                client.getCreatedResource(Service.class, "pulsarname-proxy-set2").getResource());
    }


    @Test
    public void testOverride() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                proxy:
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

        Assert.assertEquals(client.getCreatedResource(Deployment.class, "pulsarname-proxy-set1")
                .getResource()
                .getSpec()
                .getReplicas(), 3);

        Assert.assertEquals(client.getCreatedResource(Deployment.class, "pulsarname-proxy-set2")
                .getResource()
                .getSpec()
                .getReplicas(), 6);

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsarname-proxy-set1")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_common"), "commonvalue");

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsarname-proxy-set2")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_common"), "override");

        Assert.assertEquals(client.getCreatedResource(ConfigMap.class, "pulsarname-proxy-set1")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_myname"), "set1");

        Assert.assertNull(client.getCreatedResource(ConfigMap.class, "pulsarname-proxy-set2")
                .getResource()
                .getData()
                .get("PULSAR_PREFIX_myname"));

        Assert.assertEquals(client.getCreatedResource(PodDisruptionBudget.class, "pulsarname-proxy-set1")
                .getResource()
                .getSpec()
                .getMaxUnavailable()
                .getIntVal(), 2);

        Assert.assertEquals(client.getCreatedResource(PodDisruptionBudget.class, "pulsarname-proxy-set2")
                .getResource()
                .getSpec()
                .getMaxUnavailable()
                .getIntVal(), 1);

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsarname-proxy-set1")
                .getResource()
                .getMetadata()
                .getAnnotations().get("externaldns"), "myset1");

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsarname-proxy-set1")
                .getResource()
                .getSpec()
                .getPorts().size(), 4);

        Assert.assertNull(client.getCreatedResource(Service.class, "pulsarname-proxy-set2")
                .getResource()
                .getMetadata()
                .getAnnotations());

        Assert.assertEquals(client.getCreatedResource(Service.class, "pulsarname-proxy-set2")
                .getResource()
                .getSpec()
                .getPorts().size(), 3);
    }

    private void assertDeploymentEqualsDefault(String setName, Deployment deployment) {
        final Deployment defaultSts = invokeController("""
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:global
                """).getCreatedResource(Deployment.class).getResource();
        final String resourceName = CLUSTER_NAME + "-proxy-" + setName;
        Assert.assertEquals(deployment.getMetadata().getName(), resourceName);
        assertResourceSetLabel(deployment, setName);
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnvFrom().get(0)
                .getConfigMapRef().getName(), resourceName);
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(1).getEnvFrom().get(0)
                .getConfigMapRef().getName(), resourceName + "-ws");

        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getName(), resourceName);
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(1).getName(), resourceName + "-ws");

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
                    name: pulsarname
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
                    name: pulsarname
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
                    name: pulsarname
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

}
