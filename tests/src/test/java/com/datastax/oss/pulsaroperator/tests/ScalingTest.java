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

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "scaling")
public class ScalingTest extends BasePulsarClusterTest {

    @Test
    public void testScaling() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        specs.getGlobal().getAuth().setEnabled(false);
        try {
            applyPulsarCluster(specsToYaml(specs));

            awaitInstalled();

            client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-zookeeper")
                    .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                            && s.getStatus().getReadyReplicas() == 3, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            specs.getBookkeeper().setReplicas(3);
            specs.getBroker().setConfig(
                    BaseComponentSpec.mergeMaps(
                            specs.getBroker().getConfig(),
                            Map.of(
                                    "managedLedgerDefaultAckQuorum", "2",
                                    "managedLedgerDefaultEnsembleSize", "2",
                                    "managedLedgerDefaultWriteQuorum", "2"
                            )
                    )
            );
            applyPulsarCluster(specsToYaml(specs));

            client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-bookkeeper")
                    .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                            && s.getStatus().getReadyReplicas() == 3, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            specs.getBroker().setReplicas(3);
            applyPulsarCluster(specsToYaml(specs));

            client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-broker")
                    .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                            && s.getStatus().getReadyReplicas() == 3, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            assertProduceConsume();
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }

    private void assertProduceConsume() {
        // use two different brokers to ensure broker's intra communication
        execInPod("pulsar-broker-2", "bin/pulsar-client produce -m test test-topic");
        execInPod("pulsar-broker-1", "bin/pulsar-client consume -s sub -p Earliest test-topic");
        final String proxyPod =
                client.pods().withLabel("component", "proxy").list().getItems().get(0).getMetadata().getName();

        execInPodContainer(proxyPod, "pulsar-proxy-ws",
                "bin/pulsar-client --url \"ws://localhost:8000\" produce -m test test-topic-proxy");
        execInPodContainer(proxyPod, "pulsar-proxy", "bin/pulsar-client consume -s sub-proxy -p Earliest test-topic-proxy");
    }



}
