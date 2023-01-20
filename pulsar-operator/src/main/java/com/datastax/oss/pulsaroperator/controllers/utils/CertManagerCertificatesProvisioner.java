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

import com.datastax.oss.pulsaroperator.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.function.FunctionsWorkerResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.proxy.ProxyResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.tls.TlsConfig;
import io.fabric8.certmanager.api.model.v1.Certificate;
import io.fabric8.certmanager.api.model.v1.CertificateBuilder;
import io.fabric8.certmanager.api.model.v1.Issuer;
import io.fabric8.certmanager.api.model.v1.IssuerBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;

public class CertManagerCertificatesProvisioner {
    private final KubernetesClient client;
    private final String namespace;
    private final GlobalSpec globalSpec;

    public CertManagerCertificatesProvisioner(KubernetesClient client, String namespace, GlobalSpec globalSpec) {
        this.client = client;
        this.namespace = namespace;
        this.globalSpec = globalSpec;
    }

    public void generateCertificates() {
        if (globalSpec.getTls() == null
                || !globalSpec.getTls().getEnabled()
                || globalSpec.getTls().getCertProvisioner() == null
                || globalSpec.getTls().getCertProvisioner().getSelfSigned() == null) {
            return;
        }

        final TlsConfig.CertProvisionerConfig certProvisioner = globalSpec.getTls().getCertProvisioner();
        final TlsConfig.SelfSignedCertProvisionerConfig selfSigned =
                certProvisioner.getSelfSigned();

        if (!selfSigned.getEnabled()) {
            return;
        }
        final String name = globalSpec.getName();
        final String ssIssuerName = "%s-self-signed-issuer".formatted(name);
        final String caIssuerName = "%s-ca-issuer".formatted(name);
        final String ssCaSecretName = "%s-ss-ca".formatted(name);
        final String serviceDnsSuffix = "%s.svc.%s".formatted(namespace, globalSpec.getKubernetesClusterDomain());


        final Issuer ssIssuer = new IssuerBuilder()
                .withNewMetadata()
                .withName(ssIssuerName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withNewSelfSigned()
                .endSelfSigned()
                .endSpec()
                .build();

        client.resource(ssIssuer)
                .inNamespace(namespace)
                .createOrReplace();

        final Certificate caCertificate = new CertificateBuilder()
                .withNewMetadata()
                .withName("%s-ca-certificate".formatted(name))
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withSecretName(ssCaSecretName)
                .withCommonName(serviceDnsSuffix)
                .withPrivateKey(selfSigned.getPrivateKey())
                .withUsages("server auth", "client auth")
                .withIsCA(true)
                .withNewIssuerRef()
                .withName(ssIssuerName)
                .endIssuerRef()
                .endSpec()
                .build();

        client.resource(caCertificate)
                .inNamespace(namespace)
                .createOrReplace();

        final Issuer caIssuer = new IssuerBuilder()
                .withNewMetadata()
                .withName(caIssuerName)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withNewCa()
                .withSecretName(ssCaSecretName)
                .endCa()
                .endSpec()
                .build();

        client.resource(caIssuer)
                .inNamespace(namespace)
                .createOrReplace();


        List<String> dnsNames = new ArrayList<>();
        final String brokerBase =
                BrokerResourcesFactory.getResourceName(name, BrokerResourcesFactory.getComponentBaseName(globalSpec));
        dnsNames.add("*.%s.%s".formatted(brokerBase, serviceDnsSuffix));
        dnsNames.add("*.%s.%s".formatted(brokerBase, namespace));
        dnsNames.add("*.%s".formatted(brokerBase));
        dnsNames.add("%s.%s".formatted(brokerBase, serviceDnsSuffix));
        dnsNames.add("%s.%s".formatted(brokerBase, namespace));
        dnsNames.add(brokerBase);

        final String proxyBase =
                ProxyResourcesFactory.getResourceName(name, ProxyResourcesFactory.getComponentBaseName(globalSpec));

        dnsNames.add("%s.%s".formatted(proxyBase, serviceDnsSuffix));
        dnsNames.add("%s.%s".formatted(proxyBase, namespace));
        dnsNames.add(proxyBase);

        final String functionsWorkerBase =
                FunctionsWorkerResourcesFactory.getResourceName(name,
                        FunctionsWorkerResourcesFactory.getComponentBaseName(globalSpec));

        dnsNames.add("%s-ca.%s".formatted(functionsWorkerBase, serviceDnsSuffix));
        dnsNames.add("%s-ca.%s".formatted(functionsWorkerBase, namespace));
        dnsNames.add("%s-ca".formatted(functionsWorkerBase));

        if (globalSpec.getDnsName() != null
                && selfSigned.getIncludeDns() != null
                && selfSigned.getIncludeDns()) {
            dnsNames.add(globalSpec.getDnsName());
        }
        final Certificate ssCertificate = new CertificateBuilder()
                .withNewMetadata()
                .withName("%s-server-tls".formatted(name))
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withPrivateKey(selfSigned.getPrivateKey())
                .withSecretName(globalSpec.getTls().getDefaultSecretName())
                .withNewIssuerRef()
                .withName(caIssuerName)
                .endIssuerRef()
                .withDnsNames(dnsNames)
                .endSpec()
                .build();

        client.resource(ssCertificate)
                .inNamespace(namespace)
                .createOrReplace();

    }


}
