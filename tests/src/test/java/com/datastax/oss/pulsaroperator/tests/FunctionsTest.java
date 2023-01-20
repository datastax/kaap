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
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorkerSpec;
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
                BaseComponentSpec.mergeMaps(specs.getBroker().getConfig(),
                        Map.of(
                                "managedLedgerDefaultAckQuorum", "1",
                                "managedLedgerDefaultEnsembleSize", "1",
                                "managedLedgerDefaultWriteQuorum", "1"

                        ))
        );
        specs.setFunctionsWorker(FunctionsWorkerSpec.builder()
                .replicas(1)
                .resources(RESOURCE_REQUIREMENTS)
                .runtime("kubernetes")
                .config(Map.of(
                        "numFunctionPackageReplicas", 1,
                        "functionInstanceMaxResources", Map.of(
                                "disk", 1000000000,
                                "ram", 12800000,
                                "cpu", 0.001d
                        )
                ))
                .probe(ProbeConfig.builder()
                        .initial(5)
                        .period(5)
                        .build())
                .build()
        );
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
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }



}
