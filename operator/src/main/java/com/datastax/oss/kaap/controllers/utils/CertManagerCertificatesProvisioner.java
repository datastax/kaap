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

import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.controllers.bookkeeper.BookKeeperController;
import com.datastax.oss.kaap.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.kaap.controllers.broker.BrokerController;
import com.datastax.oss.kaap.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.kaap.controllers.function.FunctionsWorkerResourcesFactory;
import com.datastax.oss.kaap.controllers.proxy.ProxyController;
import com.datastax.oss.kaap.controllers.proxy.ProxyResourcesFactory;
import com.datastax.oss.kaap.controllers.zookeeper.ZooKeeperResourcesFactory;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig.SelfSignedCertificatePerComponentConfig;
import io.fabric8.certmanager.api.model.v1.Certificate;
import io.fabric8.certmanager.api.model.v1.CertificateBuilder;
import io.fabric8.certmanager.api.model.v1.CertificatePrivateKey;
import io.fabric8.certmanager.api.model.v1.Issuer;
import io.fabric8.certmanager.api.model.v1.IssuerBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final String clusterSpecName;
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
            this.clusterSpecName = null;
            this.caIssuerName = null;
            this.ssCaSecretName = null;
            this.serviceDnsSuffix = null;
        } else {
            this.selfSigned = globalSpec.getTls().getCertProvisioner().getSelfSigned();
            this.clusterSpecName = globalSpec.getName();
            this.caIssuerName = "%s-ca-issuer".formatted(clusterSpecName);
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
                dnsNames.addAll(getBookKeeperDNSNames(null));
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getZookeeper())) {
                dnsNames.addAll(getZookeeperDNSNames(null));
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getBroker())) {
                dnsNames.addAll(getBrokerDNSNames(null));
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getProxy())) {
                dnsNames.addAll(getProxyDNSNames(null));
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getFunctionsWorker())) {
                dnsNames.addAll(getFunctionsWorkerDNSNames(null));
            }
            dnsNames.addAll(getDnsNamesForExternalServicesForGlobalCert());
            if (globalSpec.getDnsName() != null
                    && selfSigned.getIncludeDns() != null
                    && selfSigned.getIncludeDns()) {
                dnsNames.add(globalSpec.getDnsName());
            }
            final String certName = "%s-server-tls".formatted(clusterSpecName);
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
                        getBrokerDNSNames(selfSigned.getBroker()));
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getBookkeeper())) {
                createCertificatePerComponent(selfSigned.getBookkeeper(),
                        globalSpec.getComponents().getBookkeeperBaseName(),
                        globalSpec.getTls().getBookkeeper().getSecretName(),
                        getBookKeeperDNSNames(selfSigned.getBookkeeper()));
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getAutorecovery())) {
                createAutorecoveryCertificate();
            }
            if (TlsConfig.ProxyTlsEntryConfig.isEnabled(globalSpec.getTls().getProxy())) {
                createCertificatePerComponent(selfSigned.getProxy(),
                        globalSpec.getComponents().getProxyBaseName(),
                        globalSpec.getTls().getProxy().getSecretName(),
                        getProxyDNSNames(selfSigned.getProxy()));
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getZookeeper())) {
                createCertificatePerComponent(selfSigned.getZookeeper(),
                        globalSpec.getComponents().getZookeeperBaseName(),
                        globalSpec.getTls().getZookeeper().getSecretName(),
                        getZookeeperDNSNames(selfSigned.getZookeeper()));
            }

            if (TlsConfig.FunctionsWorkerTlsEntryConfig.isEnabled(globalSpec.getTls().getFunctionsWorker())) {
                createCertificatePerComponent(selfSigned.getFunctionsWorker(),
                        globalSpec.getComponents().getFunctionsWorkerBaseName(),
                        globalSpec.getTls().getFunctionsWorker().getSecretName(),
                        getFunctionsWorkerDNSNames(selfSigned.getFunctionsWorker()));
            }
            if (selfSigned.getExternal() != null) {
                for (Map.Entry<String, SelfSignedCertificatePerComponentConfig> entry
                        : selfSigned.getExternal().entrySet()) {
                    final String serviceName = entry.getKey();
                    final SelfSignedCertificatePerComponentConfig config = entry.getValue();

                    if (config.getGenerate() != null && config.getGenerate()) {
                        createCertificatePerComponent(
                                config,
                                serviceName,
                                config.getSecretName(),
                                getBaseK8sDnsNames(config, serviceName)
                        );
                    }
                }
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
                        .formatted(clusterSpecName, globalSpec.getComponents().getAutorecoveryBaseName()))
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
                "%s-%s-tls".formatted(clusterSpecName, baseName),
                secretName,
                ObjectUtils.firstNonNull(componentConfig.getPrivateKey(), selfSigned.getPrivateKey()),
                dnsNames
        );
    }

    private List<String> mergeDnsNames(List<String> k8sDnsNames, SelfSignedCertificatePerComponentConfig config) {
        final List<String> finalDnsNames = new ArrayList<>(ObjectUtils.firstNonNull(k8sDnsNames, new ArrayList<>()));
        if (config != null && config.getDnsNames() != null) {
            finalDnsNames.addAll(config.getDnsNames());
        }
        return finalDnsNames;
    }

    private List<String> getDnsNamesForExternalServicesForGlobalCert() {
        final List<String> dnsNames = new ArrayList<>();
        if (selfSigned.getExternal() != null) {
            for (Map.Entry<String, SelfSignedCertificatePerComponentConfig> entry
                    : selfSigned.getExternal().entrySet()) {
                final SelfSignedCertificatePerComponentConfig config = entry.getValue();
                dnsNames.addAll(getBaseK8sDnsNames(config, entry.getKey()));
            }
        }
        return dnsNames;
    }

    private List<String> getBaseK8sDnsNames(SelfSignedCertificatePerComponentConfig config, String serviceName) {
        List<String> k8sDnsNames = enumerateDnsNames(serviceName, true);
        return mergeDnsNames(k8sDnsNames, config);
    }

    private List<String> getBookKeeperDNSNames(SelfSignedCertificatePerComponentConfig config) {
        final String componentBaseName = BookKeeperResourcesFactory.getComponentBaseName(globalSpec);
        List<String> k8sDnsNames = BookKeeperController
                .enumerateBookKeeperSets(clusterSpecName, componentBaseName, pulsarClusterSpec.getBookkeeper()).stream()
                .flatMap(set -> enumerateDnsNames(set, true).stream())
                .collect(Collectors.toList());
        return mergeDnsNames(k8sDnsNames, config);
    }

    private List<String> getFunctionsWorkerDNSNames(SelfSignedCertificatePerComponentConfig config) {
        final String functionsWorkerBase =
                FunctionsWorkerResourcesFactory.getResourceName(clusterSpecName,
                        FunctionsWorkerResourcesFactory.getComponentBaseName(globalSpec));
        List<String> k8sDnsNames = new ArrayList<>();
        // The DNS names ending in "-ca" are meant for client access. The wildcard names are used for zookeeper to
        // zookeeper networking.
        k8sDnsNames.addAll(enumerateDnsNames(functionsWorkerBase, true));
        k8sDnsNames.addAll(enumerateDnsNames(functionsWorkerBase + "-ca", false));
        return mergeDnsNames(k8sDnsNames, config);
    }

    private List<String> getZookeeperDNSNames(SelfSignedCertificatePerComponentConfig config) {
        final String zookeeperDNSNames =
                ZooKeeperResourcesFactory.getResourceName(clusterSpecName,
                        ZooKeeperResourcesFactory.getComponentBaseName(globalSpec));
        List<String> k8sDnsNames = new ArrayList<>();
        // The DNS names ending in "-ca" are meant for client access. The wildcard names are used for zookeeper to
        // zookeeper networking.
        k8sDnsNames.addAll(enumerateDnsNames(zookeeperDNSNames, true));
        k8sDnsNames.addAll(enumerateDnsNames(zookeeperDNSNames + "-ca", false));
        return mergeDnsNames(k8sDnsNames, config);
    }

    private List<String> getProxyDNSNames(SelfSignedCertificatePerComponentConfig config) {
        final String componentBaseName = ProxyResourcesFactory.getComponentBaseName(globalSpec);
        List<String> k8sDnsNames = ProxyController
                .enumerateProxySets(clusterSpecName, componentBaseName, pulsarClusterSpec.getProxy()).stream()
                .flatMap(set -> enumerateDnsNames(set, false).stream())
                .collect(Collectors.toList());
        return mergeDnsNames(k8sDnsNames, config);
    }

    private List<String> getBrokerDNSNames(SelfSignedCertificatePerComponentConfig config) {
        final String componentBaseName = BrokerResourcesFactory.getComponentBaseName(globalSpec);
        List<String> k8sDnsNames = BrokerController
                .enumerateBrokerSets(clusterSpecName, componentBaseName, pulsarClusterSpec.getBroker()).stream()
                .flatMap(set -> enumerateDnsNames(set, true).stream())
                .collect(Collectors.toList());
        return mergeDnsNames(k8sDnsNames, config);
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
        log.debugf("Created self-signed certificate %s mapped to secret %s", name, secretName);
    }

    private void createRootCACertificate() {
        final String ssIssuerName = "%s-self-signed-issuer".formatted(clusterSpecName);

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
                .withName("%s-ca-certificate".formatted(clusterSpecName))
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
        log.debug("Created self-signed root CA certificate");
    }


}