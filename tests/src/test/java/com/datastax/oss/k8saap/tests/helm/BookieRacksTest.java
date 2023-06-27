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
package com.datastax.oss.k8saap.tests.helm;

import com.datastax.oss.k8saap.common.SerializationUtil;
import com.datastax.oss.k8saap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.k8saap.crds.cluster.PulsarCluster;
import com.datastax.oss.k8saap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.k8saap.crds.configs.RackConfig;
import com.datastax.oss.k8saap.crds.configs.ResourceSetConfig;
import com.datastax.oss.k8saap.crds.configs.tls.TlsConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "helm")
public class BookieRacksTest extends BaseHelmTest {

    @Test
    public void testVerifyRackOnZk() throws Exception {
        try {
            applyCertManagerCRDs();

            helmInstall(Chart.STACK, """
                    k8saap:
                        operator:
                            image: %s
                            imagePullPolicy: Never
                    cert-manager:
                      enabled: true
                      global:
                        leaderElection:
                            namespace: %s
                    """.formatted(OPERATOR_IMAGE, namespace));
            awaitOperatorRunning();

            final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
            specs.getGlobal()
                    .setTls(TlsConfig.builder()
                            .enabled(true)
                            .certProvisioner(TlsConfig.CertProvisionerConfig.builder()
                                    .selfSigned(TlsConfig.SelfSignedCertProvisionerConfig.builder()
                                            .enabled(true)
                                            .build())
                                    .build())
                            .zookeeper(TlsConfig.TlsEntryConfig.builder()
                                    .enabled(true)
                                    .build())
                            .autorecovery(TlsConfig.TlsEntryConfig.builder()
                                    .enabled(true)
                                    .build())
                            .bookkeeper(TlsConfig.TlsEntryConfig.builder()
                                    .enabled(true)
                                    .build())
                            .broker(TlsConfig.TlsEntryConfig.builder()
                                    .enabled(true)
                                    .build())
                            .build());

            // all disabled, we only care about the ledger placement policy
            final RackConfig rackConfig = RackConfig.builder()
                    .host(RackConfig.HostRackTypeConfig.builder()
                            .enabled(false)
                            .build())
                    .zone(RackConfig.ZoneRackTypeConfig.builder()
                            .enabled(false)
                            .build())
                    .build();

            specs.getGlobal().setRacks(Map.of(
                    "rack1", rackConfig,
                    "rack2", rackConfig
            ));

            specs.getGlobal().setResourceSets(Map.of(
                    "set1", ResourceSetConfig.builder().rack("rack1").build(),
                    "set2", ResourceSetConfig.builder().rack("rack2").build(),
                    "norack", ResourceSetConfig.builder().build()
            ));

            specs.getBookkeeper().setSets(new LinkedHashMap<>(Map.of(
                    "set1", BookKeeperSetSpec.builder().replicas(2).build(),
                    "set2", BookKeeperSetSpec.builder().replicas(2).build(),
                    "norack", BookKeeperSetSpec.builder().build()
            )));

            specs.getBroker().setReplicas(1);
            specs.getProxy().setReplicas(0);
            specs.getBastion().setReplicas(0);
            specs.getFunctionsWorker().setReplicas(0);
            applyPulsarCluster(specsToYaml(specs));

            final String nodeName = client.nodes().list().getItems().get(0).getMetadata().getName();

            for (int i = 0; i < 2; i++) {
                AtomicInteger finalI = new AtomicInteger(i);
                Awaitility.await().untilAsserted(() -> {
                    final String bookieId = getBookieId(finalI.get(), "set1");
                    assertBookiePlacedInRack(bookieId, "rack1", nodeName);
                });
            }

            for (int i = 0; i < 2; i++) {
                AtomicInteger finalI = new AtomicInteger(i);
                Awaitility.await().untilAsserted(() -> {
                    final String bookieId = getBookieId(finalI.get(), "set2");
                    assertBookiePlacedInRack(bookieId, "rack2", nodeName);
                });
            }
            Awaitility.await().untilAsserted(() -> {
                final String bookieId = getBookieId(0, "norack");
                assertBookiePlacedInRack(bookieId, null, null);
            });

            specs.getBookkeeper().setSets(new LinkedHashMap<>(Map.of(
                    "set1", BookKeeperSetSpec.builder().replicas(3).build(),
                    "set2", BookKeeperSetSpec.builder().replicas(1).build(),
                    "norack", BookKeeperSetSpec.builder().build()
            )));
            applyPulsarCluster(specsToYaml(specs));

            Awaitility.await().untilAsserted(() -> {
                final String bookieId = getBookieId(2, "set1");

                assertBookiePlacedInRack(bookieId, "rack1", nodeName);
            });

            Awaitility.await().untilAsserted(() -> {
                final String bookieId = getBookieId(1, "set2");
                assertBookiePlacedInRack(bookieId, null, null);
            });

            client.resources(PulsarCluster.class)
                    .inNamespace(namespace)
                    .withName("pulsar-cluster")
                    .delete();
            awaitUninstalled();
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }

    private String getBookieId(int i, String set) {
        return "pulsar-bookkeeper-%s-%d.pulsar-bookkeeper-%s.%s.svc.cluster.local:3181"
                .formatted(set, i, set, namespace);
    }

    private void assertBookiePlacedInRack(String bookieId, String expectedRack, String node) {
        final String response =
                execInPod("pulsar-broker-0", "bin/pulsar-admin bookies get-bookie-rack -b %s".formatted(bookieId));
        if (StringUtils.isBlank(response)) {
            if (expectedRack == null) {
                return;
            }

        } else {
            if (expectedRack == null) {
                Assert.fail("Bookie %s is placed in rack %s, but it should not".formatted(bookieId, expectedRack));
            } else {
                final Map mapResult = SerializationUtil.readJson(response, Map.class);
                Assert.assertEquals(mapResult.get("rack"), expectedRack + "/" + node);
                Assert.assertEquals(mapResult.get("hostname"), bookieId);
            }
        }

    }
}
