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

import com.datastax.oss.kaap.crds.ConfigUtil;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.crds.configs.AuthConfig;
import io.fabric8.kubernetes.api.model.Secret;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "misc")
public class TokenAuthTest extends BasePulsarClusterTest {

    @Test
    public void test() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();

        final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
        specs.getZookeeper().setReplicas(1);
        specs.getBroker().setConfig(
                ConfigUtil.mergeMaps(specs.getBroker().getConfig(),
                        Map.of(
                                "managedLedgerDefaultAckQuorum", "1",
                                "managedLedgerDefaultEnsembleSize", "1",
                                "managedLedgerDefaultWriteQuorum", "1"

                        ))
        );
        specs.getGlobal()
                .setAuth(AuthConfig.builder()
                        .enabled(true)
                        .build());

        try {
            applyPulsarCluster(specsToYaml(specs));
            Awaitility.await().untilAsserted(() -> {
                final List<Secret> secrets = client.secrets()
                        .inNamespace(namespace)
                        .list().getItems();
                Assert.assertEquals(secrets.size(), 6);
            });
            final List<Secret> secrets = client.secrets()
                    .inNamespace(namespace)
                    .list().getItems();
            final Map<String, Secret> secretMap =
                    secrets.stream().collect(Collectors.toMap(s -> s.getMetadata().getName(), Function.identity()));
            Assert.assertTrue(secretMap.containsKey("token-private-key"));
            Assert.assertTrue(secretMap.containsKey("token-public-key"));
            Assert.assertTrue(secretMap.containsKey("token-superuser"));
            Assert.assertTrue(secretMap.containsKey("token-admin"));
            Assert.assertTrue(secretMap.containsKey("token-proxy"));
            Assert.assertTrue(secretMap.containsKey("token-websocket"));

            awaitInstalled();
            execInBastionPod("bin/pulsar tokens create --private-key /pulsar/token-private-key/my-private.key --subject myuser"
                            + " > myuser.jwt",
                    "bin/pulsar tokens create --private-key /pulsar/token-private-key/my-private.key --subject "
                            + "myuser2 > myuser2.jwt",
                    "bin/pulsar-admin namespaces grant-permission --role myuser --actions produce public/default",
                    "bin/pulsar-admin namespaces grant-permission --role myuser2 --actions consume public/default");

            execInBastionPod(
                    "export PULSAR_PREFIX_authPlugin=org.apache.pulsar.client.impl.auth.AuthenticationToken",
                    "export PULSAR_PREFIX_authParams=\"file:///pulsar/myuser.jwt\"",
                    "/pulsar/bin/apply-config-from-env.py /pulsar/conf/client.conf",
                    "bin/pulsar-client produce -m hello public/default/topic");

            execInBastionPod(
                    "export PULSAR_PREFIX_authPlugin=org.apache.pulsar.client.impl.auth.AuthenticationToken",
                    "export PULSAR_PREFIX_authParams=\"file:///pulsar/myuser2.jwt\"",
                    "/pulsar/bin/apply-config-from-env.py /pulsar/conf/client.conf",
                    "bin/pulsar-client consume -s sub -p Earliest public/default/topic");
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }

}
