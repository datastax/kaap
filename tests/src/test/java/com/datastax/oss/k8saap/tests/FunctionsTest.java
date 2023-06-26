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
package com.datastax.oss.k8saap.tests;

import com.datastax.oss.k8saap.crds.ConfigUtil;
import com.datastax.oss.k8saap.crds.cluster.PulsarClusterSpec;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "misc")
public class FunctionsTest extends BasePulsarClusterTest {

    @Test
    public void testFunctions() throws Exception {
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
        specs.getFunctionsWorker().setReplicas(1);
        try {
            applyPulsarCluster(specsToYaml(specs));
            awaitInstalled();
            awaitFunctionsWorkerRunning();
            client.pods()
                    .inNamespace(namespace)
                    .withName("pulsar-function-0")
                    .waitUntilReady(30, TimeUnit.SECONDS);

            assertSourceInstalled();
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }



}
