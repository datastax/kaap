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

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.broker.BrokerController;
import com.datastax.oss.pulsaroperator.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.function.FunctionsWorkerResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.proxy.ProxyController;
import com.datastax.oss.pulsaroperator.controllers.proxy.ProxyResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.zookeeper.ZooKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.tls.TlsConfig;
import io.fabric8.certmanager.api.model.v1.Certificate;
import io.fabric8.certmanager.api.model.v1.CertificateBuilder;
import io.fabric8.certmanager.api.model.v1.CertificatePrivateKey;
import io.fabric8.certmanager.api.model.v1.Issuer;
import io.fabric8.certmanager.api.model.v1.IssuerBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.ObjectUtils;

@JBossLog
public class CertManagerCertificatesProvisioner {
    private final KubernetesClient client;
    private final String namespace;
    private final PulsarClusterSpec pulsarClusterSpec;
    private final GlobalSpec globalSpec;
    private final TlsConfig.SelfSignedCertProvisionerConfig selfSigned;
    private final String clusterName;
    private final String ssCaSecretName;
    private final String caIssuerName;
    private final String serviceDnsSuffix;

    public CertManagerCertificatesProvisioner(KubernetesClient client, String namespace,
                                              PulsarClusterSpec pulsarClusterSpec) {
        this.client = client;
        this.namespace = namespace;
        this.pulsarClusterSpec = pulsarClusterSpec;
        this.globalSpec = pulsarClusterSpec.getGlobalSpec();
        if (globalSpec.getTls() == null
                || !globalSpec.getTls().getEnabled()
                || globalSpec.getTls().getCertProvisioner() == null
                || globalSpec.getTls().getCertProvisioner().getSelfSigned() == null) {
            this.selfSigned = null;
            this.clusterName = null;
            this.caIssuerName = null;
            this.ssCaSecretName = null;
            this.serviceDnsSuffix = null;
        } else {
            this.selfSigned = globalSpec.getTls().getCertProvisioner().getSelfSigned();
            this.clusterName = globalSpec.getName();
            this.caIssuerName = "%s-ca-issuer".formatted(clusterName);
            this.ssCaSecretName = BaseResourcesFactory.getTlsSsCaSecretName(globalSpec);
            this.serviceDnsSuffix = "%s.svc.%s".formatted(namespace, globalSpec.getKubernetesClusterDomain());
        }

    }

    public void generateCertificates() {
        if (selfSigned == null) {
            return;
        }

        createRootCACertificate();
        if (selfSigned.getPerComponent() == null || !selfSigned.getPerComponent()) {
            List<String> dnsNames = new ArrayList<>();
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getBookkeeper())) {
                dnsNames.addAll(getBookKeeperDNSNames());
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getZookeeper())) {
                dnsNames.addAll(getZookeeperDNSNames());
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getBroker())) {
                dnsNames.addAll(getBrokerDNSNames());
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getProxy())) {
                dnsNames.addAll(getProxyDNSNames());
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getFunctionsWorker())) {
                dnsNames.addAll(getFunctionsWorkerDNSNames());
            }
            if (globalSpec.getDnsName() != null
                    && selfSigned.getIncludeDns() != null
                    && selfSigned.getIncludeDns()) {
                dnsNames.add(globalSpec.getDnsName());
            }
            final String certName = "%s-server-tls".formatted(clusterName);
            createCertificate(
                    certName,
                    globalSpec.getTls().getDefaultSecretName(),
                    selfSigned.getPrivateKey(),
                    dnsNames
            );
        } else {
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getBroker())) {
                createCertificatePerComponent(selfSigned.getBroker(),
                        globalSpec.getComponents().getBrokerBaseName(),
                        globalSpec.getTls().getBroker().getSecretName(),
                        getBrokerDNSNames());
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getBookkeeper())) {
                createCertificatePerComponent(selfSigned.getBookkeeper(),
                        globalSpec.getComponents().getBookkeeperBaseName(),
                        globalSpec.getTls().getBookkeeper().getSecretName(),
                        getBookKeeperDNSNames());
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getAutorecovery())) {
                createAutorecoveryCertificate();
            }
            if (TlsConfig.ProxyTlsEntryConfig.isEnabled(globalSpec.getTls().getProxy())) {
                createCertificatePerComponent(selfSigned.getProxy(),
                        globalSpec.getComponents().getProxyBaseName(),
                        globalSpec.getTls().getProxy().getSecretName(),
                        getProxyDNSNames());
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getZookeeper())) {
                createCertificatePerComponent(selfSigned.getZookeeper(),
                        globalSpec.getComponents().getZookeeperBaseName(),
                        globalSpec.getTls().getZookeeper().getSecretName(),
                        getZookeeperDNSNames());
            }

            if (TlsConfig.FunctionsWorkerTlsEntryConfig.isEnabled(globalSpec.getTls().getFunctionsWorker())) {
                createCertificatePerComponent(selfSigned.getFunctionsWorker(),
                        globalSpec.getComponents().getFunctionsWorkerBaseName(),
                        globalSpec.getTls().getFunctionsWorker().getSecretName(),
                        getFunctionsWorkerDNSNames());
            }
        }
    }

    private void createAutorecoveryCertificate() {
        // autorecovery only need to be accessed via the client tls auth, no dns names needed
        final TlsConfig.SelfSignedCertificatePerComponentConfig autorecoveryConfig = selfSigned.getAutorecovery();

        final CertificatePrivateKey privateKey = ObjectUtils
                .firstNonNull(autorecoveryConfig == null ? null : autorecoveryConfig.getPrivateKey(),
                        selfSigned.getPrivateKey());

        final Certificate caCertificate = new CertificateBuilder()
                .withNewMetadata()
                .withName("%s-%s-tls"
                        .formatted(clusterName, globalSpec.getComponents().getAutorecoveryBaseName()))
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withSecretName(globalSpec.getTls().getAutorecovery().getSecretName())
                .withCommonName(globalSpec.getComponents().getAutorecoveryBaseName())
                .withPrivateKey(privateKey)
                .withUsages("client auth")
                .withNewIssuerRef()
                .withName(caIssuerName)
                .endIssuerRef()
                .endSpec()
                .build();

        client.resource(caCertificate)
                .inNamespace(namespace)
                .createOrReplace();
    }

    private void createCertificatePerComponent(final TlsConfig.SelfSignedCertificatePerComponentConfig componentConfig,
                                               final String baseName,
                                               final String secretName,
                                               List<String> dnsNames) {
        if (componentConfig == null || !componentConfig.getGenerate()) {
            return;
        }

        createCertificate(
                "%s-%s-tls".formatted(clusterName, baseName),
                secretName,
                ObjectUtils.firstNonNull(componentConfig.getPrivateKey(), selfSigned.getPrivateKey()),
                dnsNames
        );
    }

    private List<String> getBookKeeperDNSNames() {
        final String bkBase =
                BookKeeperResourcesFactory.getResourceName(clusterName,
                        BookKeeperResourcesFactory.getComponentBaseName(globalSpec));
        return enumerateDnsNames(bkBase, true);
    }

    private List<String> getFunctionsWorkerDNSNames() {
        final String functionsWorkerBase =
                FunctionsWorkerResourcesFactory.getResourceName(clusterName,
                        FunctionsWorkerResourcesFactory.getComponentBaseName(globalSpec));
        List<String> dnsNames = new ArrayList<>();
        // The DNS names ending in "-ca" are meant for client access. The wildcard names are used for zookeeper to
        // zookeeper networking.
        dnsNames.addAll(enumerateDnsNames(functionsWorkerBase, true));
        dnsNames.addAll(enumerateDnsNames(functionsWorkerBase + "-ca", false));
        return dnsNames;
    }

    private List<String> getZookeeperDNSNames() {
        final String zookeeperDNSNames =
                ZooKeeperResourcesFactory.getResourceName(clusterName,
                        ZooKeeperResourcesFactory.getComponentBaseName(globalSpec));
        List<String> dnsNames = new ArrayList<>();
        // The DNS names ending in "-ca" are meant for client access. The wildcard names are used for zookeeper to
        // zookeeper networking.
        dnsNames.addAll(enumerateDnsNames(zookeeperDNSNames, true));
        dnsNames.addAll(enumerateDnsNames(zookeeperDNSNames + "-ca", false));
        return dnsNames;
    }

    private List<String> getProxyDNSNames() {
        final String componentBaseName = ProxyResourcesFactory.getComponentBaseName(globalSpec);
        return ProxyController
                .enumerateProxySets(clusterName, componentBaseName, pulsarClusterSpec.getProxy()).stream()
                .flatMap(set -> enumerateDnsNames(set, false).stream())
                .collect(Collectors.toList());
    }

    private List<String> getBrokerDNSNames() {
        final List<String> sets = BrokerController.enumerateBrokerSets(pulsarClusterSpec.getBroker());
        final String componentBaseName = BrokerResourcesFactory.getComponentBaseName(globalSpec);
        return sets.stream()
                .map(set -> BrokerResourcesFactory.getResourceName(clusterName, componentBaseName, set))
                .flatMap(set -> enumerateDnsNames(set, true).stream())
                .collect(Collectors.toList());
    }

    private List<String> enumerateDnsNames(final String serviceName, boolean wildcard) {
        List<String> dnsNames = new ArrayList<>();
        if (wildcard) {
            dnsNames.add("*.%s.%s".formatted(serviceName, serviceDnsSuffix));
            dnsNames.add("*.%s.%s".formatted(serviceName, namespace));
            dnsNames.add("*.%s".formatted(serviceName));
        }
        dnsNames.add("%s.%s".formatted(serviceName, serviceDnsSuffix));
        dnsNames.add("%s.%s".formatted(serviceName, namespace));
        dnsNames.add(serviceName);
        return dnsNames;
    }

    private void createCertificate(String name,
                                   String secretName,
                                   CertificatePrivateKey privateKey,
                                   List<String> dnsNames) {
        final Certificate ssCertificate = new CertificateBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withPrivateKey(privateKey)
                .withSecretName(secretName)
                .withNewIssuerRef()
                .withName(caIssuerName)
                .endIssuerRef()
                .withDnsNames(dnsNames)
                .endSpec()
                .build();

        client.resource(ssCertificate)
                .inNamespace(namespace)
                .createOrReplace();
        log.infof("Created self-signed certificate %s mapped to secret %s", name, secretName);
    }

    private void createRootCACertificate() {
        final String ssIssuerName = "%s-self-signed-issuer".formatted(clusterName);

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
                .withName("%s-ca-certificate".formatted(clusterName))
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
        log.infof("Created self-signed root CA certificate");
    }


}
