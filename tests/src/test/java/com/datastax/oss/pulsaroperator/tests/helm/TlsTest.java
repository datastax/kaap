package com.datastax.oss.pulsaroperator.tests.helm;

import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.tls.TlsConfig;
import java.io.InputStream;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "helm")
public class TlsTest extends BaseHelmTest {

    public static final String CERT_MANAGER_CRDS =
            "https://github.com/cert-manager/cert-manager/releases/download/v1.11.0/cert-manager.crds.yaml";

    @Test
    public void test() throws Exception {
        try {
            try (final InputStream in = new URL(CERT_MANAGER_CRDS)
                    .openStream();) {
                applyManifest(in.readAllBytes());
            }

            helmInstall(Chart.STACK, """
                    cert-manager:
                      enabled: true
                      global:
                        leaderElection:
                            namespace: %s
                    """.formatted(namespace));
            awaitOperatorRunning();

            final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
            specs.getGlobal()
                            .setTls(TlsConfig.builder()
                                    .enabled(true)
                                    .certProvisioner(TlsConfig.CertProvisionerConfig.builder()
                                            .selfSigned(TlsConfig.SelfSignedCertProvisionerConfig.builder()
                                                    .enabled(true)
                                                    .build())
                                            .build())
                                    .broker(TlsConfig.TlsEntryConfig.builder()
                                            .enabled(true)
                                            .build())
                                    .proxy(TlsConfig.ProxyTlsEntryConfig.builder()
                                            .enabled(true)
                                            .build())
                                    .functionsWorker(TlsConfig.FunctionsWorkerTlsEntryConfig.builder()
                                            .enabled(true)
                                            .build())
                                    .build());
            applyPulsarCluster(specsToYaml(specs));
            awaitInstalled();

            final String bastion = getPodNameByComponent("bastion");
            execInPod(bastion, "bin/pulsar-client produce -m test test-topic");
            execInPod(bastion, "bin/pulsar-client consume -s sub -p Earliest test-topic");


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
