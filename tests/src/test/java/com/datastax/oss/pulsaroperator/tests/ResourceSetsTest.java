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
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ResourceSetConfig;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySetSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
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
        specs.getBookkeeper().setSets(new LinkedHashMap<>(Map.of(
                "set1", BookKeeperSetSpec.builder().build(),
                "set2", BookKeeperSetSpec.builder().build()
        )));
        specs.getBroker().setSets(new LinkedHashMap<>(Map.of(
                "set1", BrokerSetSpec.builder().build(),
                "set2", BrokerSetSpec.builder().build()
        )));

        specs.getProxy().setSets(new LinkedHashMap<>(Map.of(
                "set1", ProxySetSpec.builder().build(),
                "set2", ProxySetSpec.builder().build()
        )));

        specs.getGlobal().setResourceSets(new LinkedHashMap<>(Map.of(
                "set1", ResourceSetConfig.builder().build(),
                "set2", ResourceSetConfig.builder().build()
        )));


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
                            .getItems().size(), 2);

            Assert.assertEquals(
                    client.apps().statefulSets()
                            .inNamespace(namespace)
                            .withLabels(Map.of(CRDConstants.LABEL_RESOURCESET, "set2"))
                            .list()
                            .getItems().size(), 2);

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

            Assert.assertNotNull(getResource(StatefulSet.class, "pulsar-bookkeeper-set1"));
            Assert.assertNotNull(getResource(StatefulSet.class, "pulsar-bookkeeper-set2"));
            Assert.assertNotNull(getResource(Service.class, "pulsar-bookkeeper-set1"));
            Assert.assertNotNull(getResource(Service.class, "pulsar-bookkeeper-set2"));
            Assert.assertNotNull(getResource(ConfigMap.class, "pulsar-bookkeeper-set1"));
            Assert.assertNotNull(getResource(ConfigMap.class, "pulsar-bookkeeper-set2"));
            Assert.assertNotNull(getResource(PodDisruptionBudget.class, "pulsar-bookkeeper-set1"));
            Assert.assertNotNull(getResource(PodDisruptionBudget.class, "pulsar-bookkeeper-set2"));

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


            specs.getBookkeeper().setSets(new LinkedHashMap<>(Map.of(
                    "set1", BookKeeperSetSpec.builder().build()
            )));

            specs.getBroker().setSets(new LinkedHashMap<>(Map.of(
                    "set1", BrokerSetSpec.builder().build()
            )));

            specs.getProxy().setSets(new LinkedHashMap<>(Map.of(
                    "set1", ProxySetSpec.builder().build()
            )));
            applyPulsarCluster(specsToYaml(specs));


            Awaitility.await().untilAsserted(() -> {
                assertListEmpty(client.apps().statefulSets().inNamespace(namespace)
                        .withLabel(CRDConstants.LABEL_RESOURCESET, "set2").list().getItems());

                assertListEmpty(client.apps().deployments().inNamespace(namespace)
                        .withLabel(CRDConstants.LABEL_RESOURCESET, "set2").list().getItems());

                assertListEmpty(client.configMaps().inNamespace(namespace)
                        .withLabel(CRDConstants.LABEL_RESOURCESET, "set2").list().getItems());

                assertListEmpty(client.services().inNamespace(namespace)
                        .withLabel(CRDConstants.LABEL_RESOURCESET, "set2").list().getItems());

                assertListEmpty(client.policy().v1().podDisruptionBudget().inNamespace(namespace)
                        .withLabel(CRDConstants.LABEL_RESOURCESET, "set2").list().getItems());
            });

        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }

    private <T extends HasMetadata> T getResource(Class<T> ofClass, String name) {
        return client.resources(ofClass).inNamespace(namespace).withName(name).get();
    }

    private void assertProduceConsume() {
        execInBastionPod("bin/pulsar-client produce -m test test-topic-proxy");
        execInBastionPod("bin/pulsar-admin tenants create mytenant");
    }

    private void assertListEmpty(final List<? extends HasMetadata> list) {
        Assert.assertTrue(list.isEmpty(),
                "Found resources for set2: " + list.stream().map(HasMetadata::getMetadata).collect(
                        Collectors.toList()));
    }

}
