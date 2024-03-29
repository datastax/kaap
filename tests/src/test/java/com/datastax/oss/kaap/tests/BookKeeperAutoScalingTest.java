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
package com.datastax.oss.kaap.tests;

import static org.testng.Assert.assertEquals;
import com.datastax.oss.kaap.crds.ConfigUtil;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "bk-scaling")
public class BookKeeperAutoScalingTest extends BasePulsarClusterTest {

    @Test
    public void testAutoscaling() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();

        specs.getGlobal().getAuth().setEnabled(false);
        specs.getBroker().setReplicas(0);
        specs.getBastion().setReplicas(0);
        specs.getFunctionsWorker().setReplicas(0);
        specs.getProxy().setReplicas(0);

        specs.getZookeeper().setReplicas(1);
        specs.getBookkeeper().getAutoscaler().setDiskUsageToleranceLwm(0.999d);
        specs.getBookkeeper().getAutoscaler().setDiskUsageToleranceHwm(0.9999d);

        try {
            applyPulsarCluster(specsToYaml(specs));

            awaitZooKeeperRunning();
            awaitBookKeeperRunning(1);
            awaitAutorecoveryRunning();

            client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-zookeeper")
                    .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                            && s.getStatus().getReadyReplicas() == 1, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            specs.getBookkeeper().getAutoscaler().setEnabled(true);
            specs.getBookkeeper().getAutoscaler().setMinWritableBookies(3);
            specs.getBroker().setConfig(
                    ConfigUtil.mergeMaps(
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
                            && s.getStatus().getReadyReplicas() >= 3, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            assertEquals(3, client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-bookkeeper")
                    .get().getStatus().getReadyReplicas());
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }
}
