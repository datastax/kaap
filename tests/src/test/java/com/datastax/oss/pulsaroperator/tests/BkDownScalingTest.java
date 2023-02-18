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

import static org.testng.Assert.assertEquals;
import com.datastax.oss.pulsaroperator.autoscaler.AutoscalerUtils;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "scaling")
public class BkDownScalingTest extends BasePulsarClusterTest {

    @Test
    public void testBkDownscaling() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        specs.getGlobal().getAuth().setEnabled(false);
        specs.getBroker().setReplicas(0);
        specs.getBastion().setReplicas(0);
        specs.getFunctionsWorker().setReplicas(0);
        specs.getProxy().setReplicas(0);

        specs.getBookkeeper().setReplicas(3);
        specs.getBookkeeper().getAutoscaler().setEnabled(true);
        specs.getBookkeeper().getAutoscaler().setMinWritableBookies(3);
        specs.getBookkeeper().getAutoscaler().setCleanUpPvcs(true);

        try {
            applyPulsarCluster(specsToYaml(specs));

            awaitZooKeeperRunning();
            awaitBookKeeperRunning(3);
            awaitAutorecoveryRunning();

            client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-zookeeper")
                    .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                            && s.getStatus().getReadyReplicas() == 3, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-bookkeeper")
                    .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                            && s.getStatus().getReadyReplicas() == 3, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            log.info("STARTED 3 bookies");

            log.info("SWITCHING bookies to r/o and waiting for the 3 extra added");
            client.pods().inNamespace(namespace)
                    .withLabel("component", "bookkeeper").resources().forEach(pod -> {
                        CompletableFuture<String> readonly = AutoscalerUtils.execInPod(client, namespace,
                                pod.get().getMetadata().getName(),
                                pod.get().getSpec().getContainers().get(0).getName(),
                                "curl -s -X PUT -H \"Content-Type: application/json\" "
                                        + "-d '{\"readOnly\":true}' "
                                        + "localhost:8000/api/v1/bookie/state/readonly");
                    });

            client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-bookkeeper")
                    .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                            && s.getStatus().getReadyReplicas() == 6, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            log.info("STARTED 6 bookies");

            log.info("SWITCHING bookies to r/w and waiting for the 3 extra removed");
            client.pods().inNamespace(namespace)
                    .withLabel("component", "bookkeeper").resources().forEach(pod -> {
                        CompletableFuture<String> readonly = AutoscalerUtils.execInPod(client, namespace,
                                pod.get().getMetadata().getName(),
                                pod.get().getSpec().getContainers().get(0).getName(),
                                "curl -s -X PUT -H \"Content-Type: application/json\" "
                                        + "-d '{\"readOnly\":false}' "
                                        + "localhost:8000/api/v1/bookie/state/readonly");
                    });

            client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-bookkeeper")
                    .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                            && s.getStatus().getReadyReplicas() == 3, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            log.info("REMOVED extra bookies");

            Awaitility.waitAtMost(DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS).untilAsserted(() -> {
                long count = client.persistentVolumeClaims()
                        .inNamespace(namespace)
                        .resources()
                        .filter(pvcr -> pvcr.get().getMetadata().getName().contains("-ledgers-")
                                    || pvcr.get().getMetadata().getName().contains("-journal-"))
                        .count();
                // 3 for ledgers + 3 for journals
                assertEquals(6, count);
            });
            log.info("REMOVED extra PVCs");

            log.info("DONE");
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }
}
