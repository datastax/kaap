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
package com.datastax.oss.kaap.controllers.utils;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
import io.fabric8.certmanager.api.model.v1.Certificate;
import io.fabric8.certmanager.api.model.v1.Issuer;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CertManagerCertificatesProvisionerTest {

    public static final String NAMESPACE = "ns";

    @Test
    public void testDefaults() throws Exception {
        final MockKubernetesClient mockKubernetesClient = generateCertificates(
                """
                        global:
                            name: pul
                            tls:
                                enabled: true
                                certProvisioner:
                                    selfSigned:
                                        enabled: true
                        """
        );

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Certificate.class, "pul-ca-certificate").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Certificate
                        metadata:
                          name: pul-ca-certificate
                          namespace: ns
                        spec:
                          commonName: ns.svc.cluster.local
                          isCA: true
                          issuerRef:
                            name: pul-self-signed-issuer
                          secretName: pul-ss-ca
                          usages:
                          - server auth
                          - client auth
                        """);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Certificate.class, "pul-server-tls").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Certificate
                        metadata:
                          name: pul-server-tls
                          namespace: ns
                        spec:
                          issuerRef:
                            name: pul-ca-issuer
                          secretName: pulsar-tls
                          """);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Issuer.class, "pul-self-signed-issuer").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Issuer
                        metadata:
                          name: pul-self-signed-issuer
                          namespace: ns
                        spec:
                          selfSigned: {}
                        """);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Issuer.class, "pul-ca-issuer").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Issuer
                        metadata:
                          name: pul-ca-issuer
                          namespace: ns
                        spec:
                          ca:
                            secretName: pul-ss-ca
                        """);

    }

    @Test
    public void testAllTls() throws Exception {
        final MockKubernetesClient mockKubernetesClient = generateCertificates(
                """
                        global:
                            name: pul
                            tls:
                                enabled: true
                                broker:
                                    enabled: true
                                bookkeeper:
                                    enabled: true
                                zookeeper:
                                    enabled: true
                                proxy:
                                    enabled: true
                                functionsWorker:
                                    enabled: true
                                certProvisioner:
                                    selfSigned:
                                        enabled: true
                        """
        );

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Certificate.class, "pul-ca-certificate").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Certificate
                        metadata:
                          name: pul-ca-certificate
                          namespace: ns
                        spec:
                          commonName: ns.svc.cluster.local
                          isCA: true
                          issuerRef:
                            name: pul-self-signed-issuer
                          secretName: pul-ss-ca
                          usages:
                          - server auth
                          - client auth
                        """);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Certificate.class, "pul-server-tls").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Certificate
                        metadata:
                          name: pul-server-tls
                          namespace: ns
                        spec:
                          dnsNames:
                          - '*.pul-bookkeeper.ns.svc.cluster.local'
                          - '*.pul-bookkeeper.ns'
                          - '*.pul-bookkeeper'
                          - pul-bookkeeper.ns.svc.cluster.local
                          - pul-bookkeeper.ns
                          - pul-bookkeeper
                          - '*.pul-zookeeper.ns.svc.cluster.local'
                          - '*.pul-zookeeper.ns'
                          - '*.pul-zookeeper'
                          - pul-zookeeper.ns.svc.cluster.local
                          - pul-zookeeper.ns
                          - pul-zookeeper
                          - pul-zookeeper-ca.ns.svc.cluster.local
                          - pul-zookeeper-ca.ns
                          - pul-zookeeper-ca
                          - '*.pul-broker.ns.svc.cluster.local'
                          - '*.pul-broker.ns'
                          - '*.pul-broker'
                          - pul-broker.ns.svc.cluster.local
                          - pul-broker.ns
                          - pul-broker
                          - pul-proxy.ns.svc.cluster.local
                          - pul-proxy.ns
                          - pul-proxy
                          - '*.pul-function.ns.svc.cluster.local'
                          - '*.pul-function.ns'
                          - '*.pul-function'
                          - pul-function.ns.svc.cluster.local
                          - pul-function.ns
                          - pul-function
                          - pul-function-ca.ns.svc.cluster.local
                          - pul-function-ca.ns
                          - pul-function-ca
                          issuerRef:
                            name: pul-ca-issuer
                          secretName: pulsar-tls
                          """);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Issuer.class, "pul-self-signed-issuer").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Issuer
                        metadata:
                          name: pul-self-signed-issuer
                          namespace: ns
                        spec:
                          selfSigned: {}
                        """);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Issuer.class, "pul-ca-issuer").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Issuer
                        metadata:
                          name: pul-ca-issuer
                          namespace: ns
                        spec:
                          ca:
                            secretName: pul-ss-ca
                        """);

    }

    @Test
    public void testIncludeDns() throws Exception {
        MockKubernetesClient mockKubernetesClient = generateCertificates(
                """
                        global:
                            name: pul
                            dnsName: example.localhost
                            tls:
                                enabled: true
                                certProvisioner:
                                    selfSigned:
                                        enabled: true
                                        includeDns: true
                        """
        );

        Assert.assertTrue(mockKubernetesClient
                .getCreatedResource(Certificate.class, "pul-server-tls")
                .getResource()
                .getSpec()
                .getDnsNames()
                .contains("example.localhost")
        );

        mockKubernetesClient = generateCertificates(
                """
                        global:
                            name: pul
                            dnsName: example.localhost
                            tls:
                                enabled: true
                                certProvisioner:
                                    selfSigned:
                                        enabled: true
                                        includeDns: false
                        """
        );

        Assert.assertFalse(mockKubernetesClient
                .getCreatedResource(Certificate.class, "pul-server-tls")
                .getResource()
                .getSpec()
                .getDnsNames()
                .contains("example.localhost"));

    }


    @Test
    public void testGeneratePerComponent() throws Exception {
        MockKubernetesClient mockKubernetesClient = generateCertificates(
                """
                        global:
                            name: pul
                            dnsName: example.localhost
                            tls:
                                enabled: true
                                broker:
                                    enabled: true
                                    secretName: pul-broker-tls
                                bookkeeper:
                                    enabled: true
                                    secretName: pul-bookeeper-tls
                                zookeeper:
                                    enabled: true
                                    secretName: pul-zookeeper-tls
                                proxy:
                                    enabled: true
                                    secretName: pul-proxy-tls
                                functionsWorker:
                                    enabled: true
                                    secretName: pul-function-tls
                                certProvisioner:
                                    selfSigned:
                                        enabled: true
                                        perComponent: true
                                        broker:
                                            generate: true
                                        bookkeeper:
                                            generate: true
                                        zookeeper:
                                            generate: true
                                        proxy:
                                            generate: true
                                        functionsWorker:
                                            generate: true
                        """
        );
        Assert.assertEquals(mockKubernetesClient.getCreatedResources(Certificate.class).size(), 6);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Certificate.class, "pul-broker-tls").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Certificate
                        metadata:
                          name: pul-broker-tls
                          namespace: ns
                        spec:
                          dnsNames:
                          - '*.pul-broker.ns.svc.cluster.local'
                          - '*.pul-broker.ns'
                          - '*.pul-broker'
                          - pul-broker.ns.svc.cluster.local
                          - pul-broker.ns
                          - pul-broker
                          issuerRef:
                            name: pul-ca-issuer
                          secretName: pul-broker-tls
                        """);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Certificate.class, "pul-zookeeper-tls").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Certificate
                        metadata:
                          name: pul-zookeeper-tls
                          namespace: ns
                        spec:
                          dnsNames:
                          - '*.pul-zookeeper.ns.svc.cluster.local'
                          - '*.pul-zookeeper.ns'
                          - '*.pul-zookeeper'
                          - pul-zookeeper.ns.svc.cluster.local
                          - pul-zookeeper.ns
                          - pul-zookeeper
                          - pul-zookeeper-ca.ns.svc.cluster.local
                          - pul-zookeeper-ca.ns
                          - pul-zookeeper-ca
                          issuerRef:
                            name: pul-ca-issuer
                          secretName: pul-zookeeper-tls
                        """);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Certificate.class, "pul-bookkeeper-tls").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Certificate
                        metadata:
                          name: pul-bookkeeper-tls
                          namespace: ns
                        spec:
                          dnsNames:
                          - '*.pul-bookkeeper.ns.svc.cluster.local'
                          - '*.pul-bookkeeper.ns'
                          - '*.pul-bookkeeper'
                          - pul-bookkeeper.ns.svc.cluster.local
                          - pul-bookkeeper.ns
                          - pul-bookkeeper
                          issuerRef:
                            name: pul-ca-issuer
                          secretName: pul-bookeeper-tls
                        """);
        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Certificate.class, "pul-proxy-tls").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Certificate
                        metadata:
                          name: pul-proxy-tls
                          namespace: ns
                        spec:
                          dnsNames:
                          - pul-proxy.ns.svc.cluster.local
                          - pul-proxy.ns
                          - pul-proxy
                          issuerRef:
                            name: pul-ca-issuer
                          secretName: pul-proxy-tls
                        """);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Certificate.class, "pul-function-tls").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Certificate
                        metadata:
                          name: pul-function-tls
                          namespace: ns
                        spec:
                          dnsNames:
                          - '*.pul-function.ns.svc.cluster.local'
                          - '*.pul-function.ns'
                          - '*.pul-function'
                          - pul-function.ns.svc.cluster.local
                          - pul-function.ns
                          - pul-function
                          - pul-function-ca.ns.svc.cluster.local
                          - pul-function-ca.ns
                          - pul-function-ca
                          issuerRef:
                            name: pul-ca-issuer
                          secretName: pul-function-tls
                        """);

        Assert.assertEquals(
                mockKubernetesClient.getCreatedResource(Certificate.class, "pul-ca-certificate").getResourceYaml(),
                """
                        ---
                        apiVersion: cert-manager.io/v1
                        kind: Certificate
                        metadata:
                          name: pul-ca-certificate
                          namespace: ns
                        spec:
                          commonName: ns.svc.cluster.local
                          isCA: true
                          issuerRef:
                            name: pul-self-signed-issuer
                          secretName: pul-ss-ca
                          usages:
                          - server auth
                          - client auth
                        """);

    }

    private MockKubernetesClient generateCertificates(String spec) {
        final PulsarClusterSpec pulsarClusterSpec = SerializationUtil.readYaml(spec, PulsarClusterSpec.class);

        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        pulsarClusterSpec.getGlobal().applyDefaults(null);
        pulsarClusterSpec.applyDefaults(pulsarClusterSpec.getGlobalSpec());
        new CertManagerCertificatesProvisioner(mockKubernetesClient.getClient(), NAMESPACE, pulsarClusterSpec)
                .generateCertificates();
        return mockKubernetesClient;
    }

}
