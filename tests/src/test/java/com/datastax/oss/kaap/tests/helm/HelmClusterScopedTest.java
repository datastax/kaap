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
import com.datastax.oss.kaap.crds.cluster.PulsarCluster;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Pod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "helm-cluster-scoped")
public class HelmClusterScopedTest extends BaseHelmTest {

    @Test
    public void testHelm() throws Exception {
        try {
            String operatorNamespace = ensureNamespace(namespace);
            String deploymentNamespace = ensureNamespace("deployment-ns");

            final String spec = """
                    operator:
                        image: %s
                        watchAllNamespaces: true
                        imagePullPolicy: Never
                        replicas: 2
                        config:
                            logLevel: trace
                            quarkus:
                                log.level: debug
                                operator-sdk.controllers."controllers".retry.interval.initial: 2500
                            operator:
                                reconciliationRescheduleSeconds: 3
                    """.formatted(OPERATOR_IMAGE);

            Map<String, Map<String, Object>> specs = new HashMap<>();
            specs.put("operator", (Map<String, Object>) SerializationUtil.readYaml(spec, Map.class).get("operator"));
            specs.put("cluster", Map.of("create", "true",
                    "namespace", deploymentNamespace,
                    "spec", getDefaultPulsarClusterSpecs()));
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

            final List<Pod> pods = getOperatorPods();
            Assert.assertEquals(pods.size(), 2);
            Awaitility.await().untilAsserted(() -> {
                Assert.assertNotNull(client.leases()
                        .inNamespace(namespace)
                        .withName(LeaderElectionConfig.LEASE_NAME)
                        .get());
            });

            namespace = deploymentNamespace;
            awaitInstalled();

            namespace = operatorNamespace;
            specs.get("operator").put("replicas", 3);
            helmUpgrade(Chart.OPERATOR, SerializationUtil.writeAsYaml(specs));

            Awaitility.await().untilAsserted(() -> {
                Assert.assertTrue(getOperatorPods().size() >= 3);
            });

            client.resources(PulsarCluster.class)
                    .inNamespace(deploymentNamespace)
                    .withName(DEFAULT_PULSAR_CLUSTER_NAME)
                    .delete();
            awaitUninstalled();
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }
}
