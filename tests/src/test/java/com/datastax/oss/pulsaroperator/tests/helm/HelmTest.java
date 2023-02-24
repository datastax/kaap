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
package com.datastax.oss.pulsaroperator.tests.helm;

import com.datastax.oss.pulsaroperator.LeaderElectionConfig;
import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import io.fabric8.kubernetes.api.model.Pod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "helm")
public class HelmTest extends BaseHelmTest {

    @Test
    public void testHelm() throws Exception {
        try {
            final Map<String, Map<String, Object>> specs = new HashMap<>(
                    Map.of("operator", new HashMap<>(Map.of("image", OPERATOR_IMAGE,
                                    "imagePullPolicy", "Never",
                                    "replicas", 2
                            )),
                            "cluster", Map.of("create", "true",
                                    "spec", getDefaultPulsarClusterSpecs()))
            );
            final String yaml = SerializationUtil.writeAsYaml(specs);
            helmInstall(Chart.OPERATOR, yaml);
            awaitOperatorRunning();
            final List<Pod> pods = getOperatorPods();
            Assert.assertEquals(pods.size(), 2);
            Awaitility.await().untilAsserted(() -> {
                Assert.assertNotNull(client.leases()
                        .inNamespace(namespace)
                        .withName(LeaderElectionConfig.PULSAR_OPERATOR_LEASE_NAME)
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
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }
}
