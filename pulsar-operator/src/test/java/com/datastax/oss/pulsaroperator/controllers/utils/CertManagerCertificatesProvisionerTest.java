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
package com.datastax.oss.pulsaroperator.controllers.utils;

import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
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
                          dnsNames:
                          - '*.pul-broker.ns.svc.cluster.local'
                          - '*.pul-broker.ns'
                          - '*.pul-broker'
                          - pul-broker.ns.svc.cluster.local
                          - pul-broker.ns
                          - pul-broker
                          - pul-proxy.ns.svc.cluster.local
                          - pul-proxy.ns
                          - pul-proxy
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

    private MockKubernetesClient generateCertificates(String spec) {
        final GlobalSpec globalSpec = MockKubernetesClient.readYaml(spec, PulsarClusterSpec.class).getGlobalSpec();

        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        globalSpec.applyDefaults(null);
        new CertManagerCertificatesProvisioner(mockKubernetesClient.getClient(), NAMESPACE, globalSpec)
                .generateCertificates();
        return mockKubernetesClient;
    }

}