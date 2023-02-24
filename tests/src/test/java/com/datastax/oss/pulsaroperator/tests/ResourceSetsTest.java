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
package com.datastax.oss.pulsaroperator.tests;

import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.ConfigUtil;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySetSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "misc")
public class ResourceSetsTest extends BasePulsarClusterTest {

    @Test
    public void test() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        specs.getZookeeper().setReplicas(1);
        specs.getBroker().setConfig(
                ConfigUtil.mergeMaps(specs.getBroker().getConfig(),
                        Map.of(
                                "managedLedgerDefaultAckQuorum", "1",
                                "managedLedgerDefaultEnsembleSize", "1",
                                "managedLedgerDefaultWriteQuorum", "1"

                        ))
        );
        specs.getBroker().setSets(Map.of(
                "set1", BrokerSetSpec.builder().build(),
                "set2", BrokerSetSpec.builder().build()
        ));

        specs.getProxy().setSets(Map.of(
                "set1", ProxySetSpec.builder().build(),
                "set2", ProxySetSpec.builder().build()
        ));

        specs.getGlobal().setResourceSets(Map.of(
                "set1", Map.of(),
                "set2", Map.of()
        ));


        specs.getGlobal()
                .setAuth(AuthConfig.builder()
                        .enabled(true)
                        .build());

        try {
            applyPulsarCluster(specsToYaml(specs));
            Assert.assertEquals(
                    client.apps().statefulSets()
                            .inNamespace(namespace)
                            .withLabels(Map.of(CRDConstants.LABEL_COMPONENT, "broker"))
                            .list()
                            .getItems().size(), 2);

            Assert.assertEquals(
                    client.apps().statefulSets()
                            .inNamespace(namespace)
                            .withLabels(Map.of(CRDConstants.LABEL_RESOURCESET, "set1"))
                            .list()
                            .getItems().size(), 1);

            Assert.assertEquals(
                    client.apps().statefulSets()
                            .inNamespace(namespace)
                            .withLabels(Map.of(CRDConstants.LABEL_RESOURCESET, "set2"))
                            .list()
                            .getItems().size(), 1);

            Assert.assertEquals(
                    client.apps().deployments()
                            .inNamespace(namespace)
                            .withLabels(Map.of(CRDConstants.LABEL_COMPONENT, "proxy"))
                            .list()
                            .getItems().size(), 2);

            Assert.assertEquals(
                    client.apps().deployments()
                            .inNamespace(namespace)
                            .withLabels(Map.of(CRDConstants.LABEL_RESOURCESET, "set1"))
                            .list()
                            .getItems().size(), 1);

            Assert.assertEquals(
                    client.apps().deployments()
                            .inNamespace(namespace)
                            .withLabels(Map.of(CRDConstants.LABEL_RESOURCESET, "set2"))
                            .list()
                            .getItems().size(), 1);

            Assert.assertNotNull(getResource(StatefulSet.class, "pulsar-broker-set1"));
            Assert.assertNotNull(getResource(StatefulSet.class, "pulsar-broker-set2"));
            Assert.assertNotNull(getResource(Service.class, "pulsar-broker-set1"));
            Assert.assertNotNull(getResource(Service.class, "pulsar-broker-set2"));
            Assert.assertNotNull(getResource(ConfigMap.class, "pulsar-broker-set1"));
            Assert.assertNotNull(getResource(ConfigMap.class, "pulsar-broker-set2"));
            Assert.assertNotNull(getResource(PodDisruptionBudget.class, "pulsar-broker-set1"));
            Assert.assertNotNull(getResource(PodDisruptionBudget.class, "pulsar-broker-set2"));

            Assert.assertNotNull(getResource(Deployment.class, "pulsar-proxy-set1"));
            Assert.assertNotNull(getResource(Deployment.class, "pulsar-proxy-set2"));
            Assert.assertNotNull(getResource(Service.class, "pulsar-proxy-set1"));
            Assert.assertNotNull(getResource(Service.class, "pulsar-proxy-set2"));
            Assert.assertNotNull(getResource(ConfigMap.class, "pulsar-proxy-set1"));
            Assert.assertNotNull(getResource(ConfigMap.class, "pulsar-proxy-set2"));
            Assert.assertNotNull(getResource(PodDisruptionBudget.class, "pulsar-proxy-set1"));
            Assert.assertNotNull(getResource(PodDisruptionBudget.class, "pulsar-proxy-set2"));


            assertProduceConsume();



        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }

    private <T extends HasMetadata> T getResource(Class<T> ofClass, String name) {
        return client.resources(ofClass).inNamespace(name).withName(name).get();
    }

    private void assertProduceConsume() {
        final String proxyPod = getPodNameByComponent("proxy");
        execInPodContainer(proxyPod, "pulsar-proxy-ws",
                "bin/pulsar-client --url \"ws://localhost:8000\" produce -m test test-topic-proxy");
        execInBastionPod("bin/pulsar-client produce -m test test-topic-proxy");
        execInBastionPod("bin/pulsar-admin tenants create mytenant");
    }

}
