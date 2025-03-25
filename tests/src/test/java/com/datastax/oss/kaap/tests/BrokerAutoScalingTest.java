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

import com.datastax.oss.kaap.crds.broker.BrokerAutoscalerSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSetSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.crds.configs.ResourceSetConfig;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "scaling")
public class BrokerAutoScalingTest extends BasePulsarClusterTest {

    @Test
    public void testAutoscaling() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        specs.getGlobal().getAuth().setEnabled(false);

        specs.getGlobal().setResourceSets(Map.of("shared", ResourceSetConfig.builder().build(),
                "dedicated", ResourceSetConfig.builder().build()));
        specs.getBroker().setSets(new LinkedHashMap<>(Map.of(
                "dedicated", BrokerSetSpec.builder()
                        .autoscaler(BrokerAutoscalerSpec.builder()
                                .enabled(true)
                                .min(1)
                                .max(2)
                                .stabilizationWindowMs(10000L)
                                .higherCpuThreshold(0.21)
                                .lowerCpuThreshold(0.2)
                                .periodMs(2000L)
                                .build())
                        .build(),
                "shared", BrokerSetSpec.builder().replicas(1).build()
        )
        ));

        try {
            applyPulsarCluster(specsToYaml(specs));

            execInBastionPod("bin/pulsar-admin ns-isolation-policy set --auto-failover-policy-type min_available "
                    + "--auto-failover-policy-params min_limit=1,usage_threshold=80 --namespaces public/default "
                    + "--primary \".*-dedicated-.*\"  pulsar dedicated");


            String cmd = "bin/pulsar-shell --fail-on-error -np -e \"";
            for (int i = 0; i < 10; i++) {
                cmd += "admin topics create public/default/topic-%d\n".formatted(i);
                cmd += "client produce -m test -n 1000 public/default/topic-%d\n".formatted(i);
            }
            cmd += "\"";
            execInBastionPod(cmd);


            Awaitility.await().atMost(15, TimeUnit.MINUTES).until(() -> {
                final StatefulSetStatus status = client.apps().statefulSets()
                        .inNamespace(namespace)
                        .withName("pulsar-broker-dedicated")
                        .get()
                        .getStatus();
                final Integer replicas = status.getReplicas();
                final Integer readyReplicas = status.getReadyReplicas();
                if (replicas > 1 && readyReplicas > 1) {
                    return true;
                }
                log.info("awaiting for broker set 'shared' to scale up");
                return true;
            });

            Assert.assertEquals(client.apps().statefulSets()
                    .inNamespace(namespace)
                    .withName("pulsar-broker-shared")
                    .get()
                    .getStatus()
                    .getReadyReplicas(), 1);
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }
}
