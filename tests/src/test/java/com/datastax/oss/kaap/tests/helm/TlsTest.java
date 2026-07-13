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

import com.datastax.oss.kaap.crds.cluster.PulsarCluster;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import io.fabric8.certmanager.api.model.v1.Certificate;
import io.fabric8.certmanager.api.model.v1.Issuer;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testng.Assert;

@Slf4j
public abstract class TlsTest extends BaseHelmTest {

    private static final String DEFAULT_ACME_ISSUER_NAME = "pul-acme-issuer";
    private static final String DEFAULT_ACME_ACCOUNT_SECRET = "pul-acme-account-key";
    private static final String DEFAULT_BROKER_ACME_SECRET = "broker-acme-tls";
    private static final String DEFAULT_PROXY_ACME_SECRET = "proxy-acme-tls";

    protected void test(boolean perComponentCerts, boolean pulsar3) throws Exception {
        try {
            applyCertManagerCRDs();
            helmInstall(Chart.STACK, """
                    kaap:
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

            final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs(pulsar3);
            if (perComponentCerts) {
                specs.getGlobal()
                        .setTls(TlsConfig.builder()
                                .enabled(true)
                                .certProvisioner(TlsConfig.CertProvisionerConfig.builder()
                                        .selfSigned(TlsConfig.SelfSignedCertProvisionerConfig.builder()
                                                .enabled(true)
                                                .perComponent(true)
                                                .zookeeper(TlsConfig.ComponentCertificateConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .functionsWorker(TlsConfig.ComponentCertificateConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .proxy(TlsConfig.ComponentCertificateConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .broker(TlsConfig.ComponentCertificateConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .bookkeeper(TlsConfig.ComponentCertificateConfig
                                                        .builder()
                                                        .generate(true)
                                                        .build())
                                                .autorecovery(TlsConfig.ComponentCertificateConfig
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

    /**
     * ACME integration flow using Pebble.
     * Keeps the same post-install checks as the self-signed flow:
     * - awaitInstalled()
     * - produce/consume with bastion
     * - awaitFunctionsWorkerRunning()
     * - assertSourceInstalled()
     * - cleanup + awaitUninstalled()
     * In addition, it asserts:
     * - ACME Issuer is created
     * - ACME broker/proxy Certificates are created
     * - TLS secrets are created
     */
    protected void testWithAcme(boolean pulsar4) {
        try (GenericContainer<?> pebble = startPebble()) {
            final String acmeServerUrl = resolvePebbleDirectoryUrl(pebble);

            applyCertManagerCRDs();
            helmInstall(Chart.STACK, """
                    kaap:
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

            final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs(pulsar4);
            specs.getFunctionsWorker().setReplicas(1);

            specs.getGlobal().setTls(TlsConfig.builder()
                    .enabled(true)
                    .defaultSecretName("pulsar-tls")
                    .certProvisioner(TlsConfig.CertProvisionerConfig.builder()
                            .selfSigned(TlsConfig.SelfSignedCertProvisionerConfig.builder()
                                    .enabled(true)
                                    .build())
                            .acme(TlsConfig.AcmeCertProvisionerConfig.builder()
                                    .enabled(true)
                                    .issuer(TlsConfig.AcmeIssuerConfig.builder()
                                            .name(DEFAULT_ACME_ISSUER_NAME)
                                            .server(acmeServerUrl)
                                            .email("admin@example.com")
                                            .privateKeySecretName(DEFAULT_ACME_ACCOUNT_SECRET)
                                            .solvers(List.of(
                                                    TlsConfig.SolverConfig.builder()
                                                            .http01(TlsConfig.Http01Config.builder()
                                                                    .ingressClass("nginx")
                                                                    .build())
                                                            .build()
                                            ))
                                            .build())
                                    .broker(TlsConfig.ComponentCertificateConfig.builder()
                                            .generate(true)
                                            .secretName(DEFAULT_BROKER_ACME_SECRET)
                                            .build())
                                    .proxy(TlsConfig.ComponentCertificateConfig.builder()
                                            .generate(true)
                                            .secretName(DEFAULT_PROXY_ACME_SECRET)
                                            .build())
                                    .build())
                            .build())
                    .zookeeper(TlsConfig.TlsEntryConfig.builder()
                            .enabled(true)
                            .build())
                    .bookkeeper(TlsConfig.TlsEntryConfig.builder()
                            .enabled(true)
                            .build())
                    .broker(TlsConfig.TlsEntryConfig.builder()
                            .enabled(true)
                            .secretName(DEFAULT_BROKER_ACME_SECRET)
                            .build())
                    .proxy(TlsConfig.ProxyTlsEntryConfig.proxyBuilder()
                            .enabled(true)
                            .enabledWithBroker(true)
                            .secretName(DEFAULT_PROXY_ACME_SECRET)
                            .build())
                    .functionsWorker(TlsConfig.FunctionsWorkerTlsEntryConfig.functionsWorkerBuilder()
                            .enabled(true)
                            .enabledWithBroker(true)
                            .build())
                    .autorecovery(TlsConfig.TlsEntryConfig.builder()
                            .enabled(true)
                            .build())
                    .build());

            applyPulsarCluster(specsToYaml(specs));

            awaitAcmeIssuerAndCertificates(
                    DEFAULT_ACME_ISSUER_NAME,
                    DEFAULT_BROKER_ACME_SECRET,
                    DEFAULT_PROXY_ACME_SECRET
            );

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
            log.error("ACME test failed with {}", t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }

    private GenericContainer<?> startPebble() {
        final GenericContainer<?> pebble = new GenericContainer<>(
                DockerImageName.parse(System.getProperty(
                        "kaap.tests.acme.pebble.image",
                        "ghcr.io/letsencrypt/pebble:latest")))
                .withExposedPorts(14000, 15000)
                .withEnv("PEBBLE_VA_NOSLEEP", "1");

        if (Boolean.getBoolean("kaap.tests.acme.pebble.alwaysValid")) {
            pebble.withEnv("PEBBLE_VA_ALWAYS_VALID", "1");
        }

        pebble.start();
        log.info("Started Pebble at {}", resolvePebbleDirectoryUrl(pebble));
        return pebble;
    }

    private String resolvePebbleDirectoryUrl(GenericContainer<?> pebble) {
        return System.getProperty(
                "kaap.tests.acme.pebble.url",
                "https://%s:%d/dir".formatted(pebble.getHost(), pebble.getMappedPort(14000))
        );
    }

    private void awaitAcmeIssuerAndCertificates(String issuerName, String... secretNames) {
        Awaitility.await().untilAsserted(() -> {
            final Issuer issuer = client.resources(Issuer.class)
                    .inNamespace(namespace)
                    .withName(issuerName)
                    .get();
            Assert.assertNotNull(issuer, "ACME Issuer was not created");
        });

        client.resources(Issuer.class)
                .inNamespace(namespace)
                .withName(issuerName)
                .edit(issuer -> {
                    issuer.getSpec().getAcme().setSkipTLSVerify(true);
                    return issuer;
                });

        Awaitility.await().untilAsserted(() -> {
            final Issuer issuer = client.resources(Issuer.class)
                    .inNamespace(namespace)
                    .withName(issuerName)
                    .get();
            Assert.assertNotNull(issuer);
            Assert.assertNotNull(issuer.getSpec());
            Assert.assertNotNull(issuer.getSpec().getAcme());
            Assert.assertEquals(issuer.getSpec().getAcme().getServer(),
                    System.getProperty("kaap.tests.acme.pebble.url", issuer.getSpec().getAcme().getServer()));
        });

        for (String secretName : secretNames) {
            Awaitility.await().untilAsserted(() -> {
                final Certificate certificate = client.resources(Certificate.class)
                        .inNamespace(namespace)
                        .withName(secretName)
                        .get();
                Assert.assertNotNull(certificate, "Certificate resource not created: " + secretName);
            });

            Awaitility.await().untilAsserted(() -> Assert.assertNotNull(
                    client.secrets()
                            .inNamespace(namespace)
                            .withName(secretName)
                            .get(), "TLS secret not created: " + secretName));
        }
    }
}
