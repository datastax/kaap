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
import static org.testng.Assert.assertTrue;
import com.datastax.oss.kaap.autoscaler.AutoscalerUtils;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.Pod;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "bk-scaling")
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

        specs.getZookeeper().setReplicas(1);
        specs.getBookkeeper().setReplicas(3);

        try {
            applyPulsarCluster(specsToYaml(specs));

            awaitZooKeeperRunning();
            awaitBookKeeperRunning(3);
            awaitAutorecoveryRunning();

            assertEquals(3, client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-bookkeeper")
                    .get().getStatus().getReadyReplicas());

            log.info("STARTED 3 bookies");

            specs.getBookkeeper().getAutoscaler().setEnabled(true);
            specs.getBookkeeper().getAutoscaler().setMinWritableBookies(3);
            specs.getBookkeeper().getAutoscaler().setDiskUsageToleranceLwm(0.999d);
            specs.getBookkeeper().getAutoscaler().setDiskUsageToleranceHwm(0.9999d);
            applyPulsarCluster(specsToYaml(specs));

            Pod bookiePod = client.pods().inNamespace(namespace)
                    .withLabel("component", "bookkeeper").resources().findFirst().get().get();

            // place some entries on bookies about to go read-only
            log.info("CREATING ledgers");
            generateLedgers(bookiePod, 3, 50);

            log.info("SWITCHING bookies to r/o and waiting for the 3 extra added");
            client.pods().inNamespace(namespace)
                    .withLabel("component", "bookkeeper").resources().forEach(pod -> {
                        AutoscalerUtils.execInPod(client, namespace,
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
                            && s.getStatus().getReadyReplicas() >= 6, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            assertEquals(6, client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-bookkeeper")
                    .get().getStatus().getReadyReplicas());

            log.info("STARTED 6 bookies");

            // place some entries on bookies to be scaled down later
            log.info("CREATING ledgers");
            generateLedgers(bookiePod, 3, 50);

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

            // write entries on bookies while scaling down
            log.info("CREATING ledgers");
            for (int i = 0; i < 10; i++) {
                Thread.sleep(25);
                generateLedgers(bookiePod, 1, 100);
                log.info("CREATED {} ledgers", i);
            }

            client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-bookkeeper")
                    .waitUntilCondition(s -> s.getStatus().getReadyReplicas() != null
                            && s.getStatus().getReadyReplicas() == 3, DEFAULT_AWAIT_SECONDS, TimeUnit.SECONDS);

            log.info("REMOVED extra bookies");

            log.info("Triggering audit");
            triggerAudit(bookiePod);

            Awaitility.waitAtMost(15, TimeUnit.MINUTES).untilAsserted(() -> {
                long count = client.persistentVolumeClaims()
                        .inNamespace(namespace)
                        .resources()
                        .filter(pvcr -> pvcr.get().getMetadata().getName().contains("-ledgers-")
                                    || pvcr.get().getMetadata().getName().contains("-journal-"))
                        .peek(pvc -> log.info("Found pvc {}", pvc.get().getMetadata().getName()))
                        .count();
                // 3 for ledgers + 3 for journals
                assertEquals(6, count);
            });
            log.info("REMOVED extra PVCs");

            log.info("Waiting for the auditor to run");
            // no better way now AFAIK
            Thread.sleep(5000);

            log.info("Listing under replicated ledgers");
            String urLedgersOut = listUnderReplicated(bookiePod);
            assertTrue(urLedgersOut.contains("No under replicated ledgers found"));

            log.info("DONE");
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }

    private String listUnderReplicated(Pod bookiePod) throws InterruptedException, ExecutionException {
        String urLedgersOut = AutoscalerUtils.execInPod(client, namespace,
                bookiePod.getMetadata().getName(),
                bookiePod.getSpec().getContainers().get(0).getName(),
                "curl -s localhost:8000/api/v1/autorecovery/list_under_replicated_ledger/").get();
        return urLedgersOut;
    }

    private void triggerAudit(Pod bookiePod) throws InterruptedException, ExecutionException {
        String res = AutoscalerUtils.execInPod(client, namespace,
                bookiePod.getMetadata().getName(),
                bookiePod.getSpec().getContainers().get(0).getName(),
                "curl -s -X PUT localhost:8000/api/v1/autorecovery/trigger_audit")
                .get();
        log.info("Trigger audit response: {}", res);
    }

    private void generateLedgers(Pod bookiePod, int ledgerCount, int numEntries)
            throws ExecutionException, InterruptedException {
        for (int i = 0; i < ledgerCount; i++) {
            String res = AutoscalerUtils.execInPod(client, namespace,
                            bookiePod.getMetadata().getName(),
                            bookiePod.getSpec().getContainers().get(0).getName(),
                            "bin/bookkeeper shell simpletest "
                                    + "-ensemble 3 -writeQuorum 1 -ackQuorum 1 -numEntries " + numEntries)
                    .get();
            assertTrue(res.contains("entries written to ledger"));
        }
    }
}
