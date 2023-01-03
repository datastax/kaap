package com.datastax.oss.pulsaroperator.tests;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorkerSpec;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.Assert;
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
            final String proxyPod =
                    client.pods().withLabel("component", "proxy").list().getItems().get(0).getMetadata().getName();
            client.pods()
                    .inNamespace(namespace)
                    .withName("pulsar-function-0")
                    .waitUntilReady(30, TimeUnit.SECONDS);

            Awaitility.await().until(() -> {
                try {
                    execInBastionPod(
                            "bin/pulsar-admin sources create --name generator --tenant public --namespace default "
                                    + "--destinationTopicName generator_test --source-type data-generator "
                                    + "--ram 12800000 --cpu 0.001 --disk 1000000000 "
                                    + "--parallelism 2");
                    return true;
                } catch (Throwable t) {
                    log.error("Cmd failed", t);
                }
                return false;
            });

            Awaitility.await().untilAsserted(() -> {
                printRunningPods();
                Assert.assertTrue(
                        client.pods().inNamespace(namespace).withName("pf-public-default-generator-0").isReady());
                Assert.assertTrue(
                        client.pods().inNamespace(namespace).withName("pf-public-default-generator-1").isReady());
            });
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }


}
