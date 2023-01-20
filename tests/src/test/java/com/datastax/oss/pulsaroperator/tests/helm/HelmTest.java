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
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.Pod;
import java.util.List;
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
            helmInstall(Chart.OPERATOR, """
                    operator:
                        imagePullPolicy: Never
                        replicas: 2
                    """);
            awaitOperatorRunning();
            final List<Pod> pods = getOperatorPods();
            Assert.assertEquals(pods.size(), 2);
            Awaitility.await().untilAsserted(() -> {
                Assert.assertNotNull(client.leases()
                        .inNamespace(namespace)
                        .withName(LeaderElectionConfig.PULSAR_OPERATOR_LEASE_NAME)
                        .get());
            });

            final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
            applyPulsarCluster(specsToYaml(specs));
            awaitInstalled();

            helmUpgrade("""
                    operator:
                        imagePullPolicy: Never
                        replicas: 3
                    """);

            Awaitility.await().untilAsserted(() -> {
                Assert.assertEquals(getOperatorPods().size(), 3);
            });


            client.resources(PulsarCluster.class)
                    .inNamespace(namespace)
                    .withName("pulsar-cluster")
                    .delete();
            awaitUninstalled();
            helmUninstall();
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }
}
