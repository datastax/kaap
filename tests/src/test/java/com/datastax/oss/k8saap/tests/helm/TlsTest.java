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
package com.datastax.oss.k8saap.tests.helm;

import com.datastax.oss.k8saap.crds.cluster.PulsarCluster;
import com.datastax.oss.k8saap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.k8saap.crds.configs.tls.TlsConfig;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "helm-tls")
public class TlsTest extends BaseHelmTest {

    @Test
    public void testPerComponents() throws Exception {
        test(true);
    }

    @Test
    public void testGlobal() throws Exception {
        test(false);

    }

    private void test(boolean perComponentCerts) throws Exception {
        try {
            applyCertManagerCRDs();
            helmInstall(Chart.STACK, """
                    k8saap:
                        operator:
                            image: %s
                            imagePullPolicy: Never
                    cert-manager:
                      enabled: true
                      global:
                        leaderElection:
                            namespace: %s
                    """.formatted(OPERATOR_IMAGE, namespace));
            awaitOperatorRunning();

            final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
            if (perComponentCerts) {
                specs.getGlobal()
                        .setTls(TlsConfig.builder()
                                .enabled(true)
                                .certProvisioner(TlsConfig.CertProvisionerConfig.builder()
                                        .selfSigned(TlsConfig.SelfSignedCertProvisionerConfig.builder()
                                                .enabled(true)
                                                .perComponent(true)
                                                .zookeeper(TlsConfig.SelfSignedCertificatePerComponentConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .functionsWorker(TlsConfig.SelfSignedCertificatePerComponentConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .proxy(TlsConfig.SelfSignedCertificatePerComponentConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .broker(TlsConfig.SelfSignedCertificatePerComponentConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .bookkeeper(TlsConfig.SelfSignedCertificatePerComponentConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .autorecovery(TlsConfig.SelfSignedCertificatePerComponentConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .build())
                                        .build())
                                .zookeeper(TlsConfig.TlsEntryConfig.builder()
                                        .enabled(true)
                                        .secretName("zk-tls")
                                        .build())
                                .autorecovery(TlsConfig.TlsEntryConfig.builder()
                                        .enabled(true)
                                        .secretName("autorecovery-tls")
                                        .build())
                                .bookkeeper(TlsConfig.TlsEntryConfig.builder()
                                        .enabled(true)
                                        .secretName("bk-tls")
                                        .build())
                                .broker(TlsConfig.TlsEntryConfig.builder()
                                        .enabled(true)
                                        .secretName("broker-tls")
                                        .build())
                                .proxy(TlsConfig.ProxyTlsEntryConfig.proxyBuilder()
                                        .enabled(true)
                                        .enabledWithBroker(true)
                                        .secretName("proxy-tls")
                                        .build())
                                .functionsWorker(TlsConfig.FunctionsWorkerTlsEntryConfig.functionsWorkerBuilder()
                                        .enabled(true)
                                        .enabledWithBroker(true)
                                        .secretName("fn-worker-tls")
                                        .build())
                                .build());
            } else {
                specs.getGlobal()
                        .setTls(TlsConfig.builder()
                                .enabled(true)
                                .certProvisioner(TlsConfig.CertProvisionerConfig.builder()
                                        .selfSigned(TlsConfig.SelfSignedCertProvisionerConfig.builder()
                                                .enabled(true)
                                                .build())
                                        .build())
                                .zookeeper(TlsConfig.TlsEntryConfig.builder()
                                        .enabled(true)
                                        .build())
                                .autorecovery(TlsConfig.TlsEntryConfig.builder()
                                        .enabled(true)
                                        .build())
                                .bookkeeper(TlsConfig.TlsEntryConfig.builder()
                                        .enabled(true)
                                        .build())
                                .broker(TlsConfig.TlsEntryConfig.builder()
                                        .enabled(true)
                                        .build())
                                .proxy(TlsConfig.ProxyTlsEntryConfig.proxyBuilder()
                                        .enabled(true)
                                        .enabledWithBroker(true)
                                        .build())
                                .functionsWorker(TlsConfig.FunctionsWorkerTlsEntryConfig.functionsWorkerBuilder()
                                        .enabled(true)
                                        .enabledWithBroker(true)
                                        .build())
                                .build());
            }

            specs.getFunctionsWorker().setReplicas(1);
            applyPulsarCluster(specsToYaml(specs));
            awaitInstalled();

            final String bastion = getPodNameByComponent("bastion");
            execInPod(bastion, "bin/pulsar-client produce -m test test-topic");
            execInPod(bastion, "bin/pulsar-client consume -s sub -p Earliest test-topic");
            awaitFunctionsWorkerRunning();

            assertSourceInstalled();


            client.resources(PulsarCluster.class)
                    .inNamespace(namespace)
                    .withName("pulsar-cluster")
                    .delete();
            awaitUninstalled();
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }
}
