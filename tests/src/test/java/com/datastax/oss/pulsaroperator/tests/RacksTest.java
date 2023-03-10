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

import com.datastax.oss.pulsaroperator.crds.ConfigUtil;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AntiAffinityConfig;
import com.datastax.oss.pulsaroperator.crds.configs.RackConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ResourceSetConfig;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySetSpec;
import io.fabric8.kubernetes.api.model.Pod;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "multi-nodes")
public class RacksTest extends BasePulsarClusterTest {

    public RacksTest() {
        super(1);
    }

    @Test
    public void testBroker() throws Exception {
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
        specs.getBroker().setReplicas(2);
        specs.getBroker().setSets(new LinkedHashMap<>(Map.of(
                "set1", BrokerSetSpec.builder().build(),
                "set2", BrokerSetSpec.builder().build(),
                "norack", BrokerSetSpec.builder().build()
        )));

        specs.getProxy().setReplicas(0);
        specs.getProxy().setWebSocket(ProxySetSpec.WebSocketConfig.builder().enabled(false).build());

        final RackConfig rackConfig = RackConfig.builder()
                .host(RackConfig.HostRackTypeConfig.builder()
                        .enabled(true)
                        .requireRackAffinity(true)
                        .requireRackAntiAffinity(true)
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
        specs.getGlobal().setAntiAffinity(AntiAffinityConfig.builder()
                .host(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                        .enabled(true)
                        .required(true)
                        .build())
                .build());

        try {
            applyPulsarCluster(specsToYaml(specs));
            Assert.assertEquals(getPodNodeName("pulsar-broker-set1-0"), getPodNodeName("pulsar-broker-set1-1"));
            Assert.assertEquals(getPodNodeName("pulsar-broker-set2-0"), getPodNodeName("pulsar-broker-set2-1"));
            Assert.assertNotEquals(getPodNodeName("pulsar-broker-set1-0"), getPodNodeName("pulsar-broker-set2-0"));
            Assert.assertNotEquals(getPodNodeName("pulsar-broker-norack-0"), getPodNodeName("pulsar-broker-norack-1"));
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }


    @Test
    public void testProxy() throws Exception {
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
        specs.getBroker().setReplicas(1);
        specs.getProxy().setReplicas(2);
        specs.getProxy().setWebSocket(ProxySetSpec.WebSocketConfig.builder().enabled(false).build());
        specs.getProxy().setSets(new LinkedHashMap<>(Map.of(
                "set1", ProxySetSpec.builder().build(),
                "set2", ProxySetSpec.builder().build(),
                "norack", ProxySetSpec.builder().build()
        )));

        final RackConfig rackConfig = RackConfig.builder()
                .host(RackConfig.HostRackTypeConfig.builder()
                        .enabled(true)
                        .requireRackAffinity(true)
                        .requireRackAntiAffinity(true)
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
        specs.getGlobal().setAntiAffinity(AntiAffinityConfig.builder()
                .host(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                        .enabled(true)
                        .required(true)
                        .build())
                .build());

        try {
            applyPulsarCluster(specsToYaml(specs));
            final List<String> set1 = getPodNamesByResourceSet("set1");
            final List<String> set2 = getPodNamesByResourceSet("set2");
            final List<String> norack = getPodNamesByResourceSet("norack");
            Assert.assertEquals(getPodNodeName(set1.get(0)), getPodNodeName(set1.get(1)));
            Assert.assertEquals(getPodNodeName(set2.get(0)), getPodNodeName(set2.get(0)));
            Assert.assertNotEquals(getPodNodeName(set1.get(0)), getPodNodeName(set2.get(0)));
            Assert.assertNotEquals(getPodNodeName(norack.get(0)), getPodNodeName(norack.get(1)));
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }


    @Test
    public void testBookKeeper() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();



        specs.getZookeeper().setReplicas(1);
        specs.getBroker().setReplicas(0);
        specs.getBookkeeper().setReplicas(2);
        specs.getBookkeeper().setSets(new LinkedHashMap<>(Map.of(
                "set1", BookKeeperSetSpec.builder().build(),
                "set2", BookKeeperSetSpec.builder().build(),
                "norack", BookKeeperSetSpec.builder().build()
        )));

        specs.getProxy().setReplicas(0);

        final RackConfig rackConfig = RackConfig.builder()
                .host(RackConfig.HostRackTypeConfig.builder()
                        .enabled(true)
                        .requireRackAffinity(true)
                        .requireRackAntiAffinity(true)
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
        specs.getGlobal().setAntiAffinity(AntiAffinityConfig.builder()
                .host(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                        .enabled(true)
                        .required(true)
                        .build())
                .build());

        try {
            applyPulsarCluster(specsToYaml(specs));
            Assert.assertEquals(getPodNodeName("pulsar-bookkeeper-set1-0"), getPodNodeName("pulsar-bookkeeper-set1-1"));
            Assert.assertEquals(getPodNodeName("pulsar-bookkeeper-set2-0"), getPodNodeName("pulsar-bookkeeper-set2-1"));
            Assert.assertNotEquals(getPodNodeName("pulsar-bookkeeper-set1-0"), getPodNodeName("pulsar-bookkeeper-set2-0"));
            Assert.assertNotEquals(getPodNodeName("pulsar-bookkeeper-norack-0"), getPodNodeName("pulsar-bookkeeper-norack-1"));
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }



    private String getPodNodeName(String name) {
        return client.resources(Pod.class).inNamespace(namespace).withName(name).get()
                .getSpec().getNodeName();
    }
}
