package com.datastax.oss.pulsaroperator.tests;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
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

            specs.getBookkeeper().getAutoscaler().setEnabled(true);
            specs.getBookkeeper().getAutoscaler().setMinWritableBookies(3);
            specs.getBookkeeper().setReplicas(1);
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
