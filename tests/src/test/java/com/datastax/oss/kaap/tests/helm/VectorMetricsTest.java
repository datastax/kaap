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
package com.datastax.oss.kaap.tests.helm;

import com.datastax.oss.kaap.LeaderElectionConfig;
import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.cluster.PulsarCluster;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.crds.metrics.VectorMetrics;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Test(groups = "kaap-vector-metrics")
public class VectorMetricsTest extends BaseHelmTest {

    @Test
    public void testHelm() {
        try {
            final String spec = """
                    operator:
                        image: %s
                        imagePullPolicy: Never
                        replicas: 2
                        config:
                            logLevel: trace
                            quarkus:
                                log.level: debug
                                operator-sdk.controllers."controllers".retry.interval.initial: 2500
                            operator:
                                reconciliationRescheduleSeconds: 3
                    """.formatted("us-central1-docker.pkg.dev/datastax-gcp-pulsar/kaap/kaapoperator/operator");

            Map<String, Map<String, Object>> specs = new HashMap<>();
            specs.put("operator", (Map<String, Object>) SerializationUtil.readYaml(spec, Map.class).get("operator"));
            specs.put("cluster", Map.of("create", "true", "spec", getDefaultPulsarClusterSpecs()));
            final String yaml = SerializationUtil.writeAsYaml(specs);
            helmInstall(Chart.OPERATOR, yaml);
            awaitOperatorRunning();
            final ConfigMap configMap = client.configMaps().inNamespace(namespace)
                    .withName("kaap")
                    .get();
            log.info("Found config map: {}", configMap.getData());
            Assert.assertEquals(configMap.getData().get("QUARKUS_LOG_CATEGORY__COM_DATASTAX_OSS_KAAP__LEVEL"),
                    "trace");
            Assert.assertEquals(configMap.getData().get("QUARKUS_LOG_LEVEL"),
                    "debug");
            Assert.assertEquals(configMap.getData().get("QUARKUS_OPERATOR_SDK_CONTROLLERS__CONTROLLERS__RETRY_INTERVAL_INITIAL"),
                    "2500");
            Assert.assertEquals(configMap.getData().get("KAAP_RECONCILIATION_RESCHEDULE_SECONDS"),
                    "3");
            Assert.assertEquals(configMap.getData().get("QUARKUS_OPERATOR_SDK_NAMESPACES"),
                    "JOSDK_WATCH_CURRENT");

            final List<Pod> pods = getOperatorPods();
            Assert.assertEquals(pods.size(), 2);
            Awaitility.await().untilAsserted(() -> {
                Assert.assertNotNull(client.leases()
                        .inNamespace(namespace)
                        .withName(LeaderElectionConfig.LEASE_NAME)
                        .get());
            });

            awaitInstalled();

            specs.get("operator").put("replicas", 3);
            helmUpgrade(Chart.OPERATOR, SerializationUtil.writeAsYaml(specs));

            Awaitility.await().untilAsserted(() -> {
                Assert.assertTrue(getOperatorPods().size() >= 3);
            });


            client.resources(PulsarCluster.class)
                    .inNamespace(namespace)
                    .withName(DEFAULT_PULSAR_CLUSTER_NAME)
                    .delete();
            awaitUninstalled();
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            throwUnchecked(t);
        }
    }

    @Override
    protected PulsarClusterSpec getDefaultPulsarClusterSpecs() {
        var cfg = super.getDefaultPulsarClusterSpecs();
        cfg.getZookeeper().setVectorMetrics(vectorMetrics());
        cfg.getBookkeeper().setVectorMetrics(vectorMetrics());
        cfg.getBroker().setVectorMetrics(vectorMetrics());
        cfg.getProxy().setVectorMetrics(vectorMetrics());
        cfg.getAutorecovery().setVectorMetrics(vectorMetrics());
        cfg.getFunctionsWorker().setVectorMetrics(vectorMetrics());
        return cfg;
    }

    private VectorMetrics vectorMetrics() {
        return vectorMetrics(null);
    }

    private VectorMetrics vectorMetrics(String url) {
        var metrics = new VectorMetrics();
        metrics.setEnabled(true);
        metrics.setImage("us-central1-docker.pkg.dev/datastax-gcp-pulsar/kaap/kaapoperator/metrics");
        metrics.setImagePullPolicy("Always");
        metrics.setName("vector-metrics");
        metrics.setSinkEndpoint("0.0.0.0");
        metrics.setScrapeEndpoint("http://%s:8000/metrics"
                .formatted(url == null ? "0.0.0.0" : url));
        return metrics;
    }

    @SuppressWarnings("unchecked")
    private <T extends Throwable> void throwUnchecked(Throwable t) throws T {
        throw (T) t;
    }
}
