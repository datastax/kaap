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
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig.ComponentCertificateConfig;
import io.fabric8.certmanager.api.model.acme.v1.ACMEChallengeSolver;
import io.fabric8.certmanager.api.model.acme.v1.ACMEChallengeSolverBuilder;
import io.fabric8.certmanager.api.model.acme.v1.ACMEChallengeSolverDNS01Builder;
import io.fabric8.certmanager.api.model.acme.v1.ACMEChallengeSolverHTTP01Builder;
import io.fabric8.certmanager.api.model.acme.v1.ACMEChallengeSolverHTTP01IngressBuilder;
import io.fabric8.certmanager.api.model.acme.v1.ACMEIssuerBuilder;
import io.fabric8.certmanager.api.model.v1.Certificate;
import io.fabric8.certmanager.api.model.v1.CertificateBuilder;
import io.fabric8.certmanager.api.model.v1.CertificatePrivateKey;
import io.fabric8.certmanager.api.model.v1.Issuer;
import io.fabric8.certmanager.api.model.v1.IssuerBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final TlsConfig.AcmeCertProvisionerConfig acme;
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
        TlsConfig tls = globalSpec != null ? globalSpec.getTls() : null;
        TlsConfig.CertProvisionerConfig provisioner = tls != null ? tls.getCertProvisioner() : null;
        if (tls == null || !Boolean.TRUE.equals(tls.getEnabled()) || provisioner == null) {
            this.selfSigned = null;
            this.acme = null;
            this.clusterSpecName = null;
            this.caIssuerName = null;
            this.ssCaSecretName = null;
            this.serviceDnsSuffix = null;
            return;
        }
        this.selfSigned = provisioner.getSelfSigned();
        this.acme = provisioner.getAcme();
        this.clusterSpecName = globalSpec.getName();
        this.caIssuerName = "%s-ca-issuer".formatted(clusterSpecName);
        this.ssCaSecretName = BaseResourcesFactory.getTlsSsCaSecretName(globalSpec);
        this.serviceDnsSuffix = "%s.svc.%s".formatted(namespace, globalSpec.getKubernetesClusterDomain());
    }

    public void generateCertificates() {
        validateProvisionersConfig();
        if (selfSigned != null && Boolean.TRUE.equals(selfSigned.getEnabled())) {
            generateSelfSignedCertificates();
        }
        if (acme != null && Boolean.TRUE.equals(acme.getEnabled())) {
            generateAcmeCertificates();
        }
    }

    private void generateSelfSignedCertificates() {
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
                    dnsNames,
                    caIssuerName
            );
        } else {
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getBroker())) {
                createCertificatePerComponent(selfSigned.getBroker(),
                        globalSpec.getComponents().getBrokerBaseName(),
                        ObjectUtils.firstNonNull(selfSigned.getBroker().getSecretName(),
                                globalSpec.getTls().getBroker().getSecretName()),
                        getBrokerDNSNames(selfSigned.getBroker()),
                        caIssuerName,
                        selfSigned.getPrivateKey());
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getBookkeeper())) {
                createCertificatePerComponent(selfSigned.getBookkeeper(),
                        globalSpec.getComponents().getBookkeeperBaseName(),
                        ObjectUtils.firstNonNull(selfSigned.getBookkeeper().getSecretName(),
                                globalSpec.getTls().getBookkeeper().getSecretName()),
                        getBookKeeperDNSNames(selfSigned.getBookkeeper()),
                        caIssuerName,
                        selfSigned.getPrivateKey());
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getAutorecovery())) {
                createAutorecoveryCertificate();
            }
            if (TlsConfig.ProxyTlsEntryConfig.isEnabled(globalSpec.getTls().getProxy())) {
                createCertificatePerComponent(selfSigned.getProxy(),
                        globalSpec.getComponents().getProxyBaseName(),
                        ObjectUtils.firstNonNull(selfSigned.getProxy().getSecretName(),
                                globalSpec.getTls().getProxy().getSecretName()),
                        getProxyDNSNames(selfSigned.getProxy()),
                        caIssuerName,
                        selfSigned.getPrivateKey());
            }
            if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getZookeeper())) {
                createCertificatePerComponent(selfSigned.getZookeeper(),
                        globalSpec.getComponents().getZookeeperBaseName(),
                        ObjectUtils.firstNonNull(selfSigned.getZookeeper().getSecretName(),
                                globalSpec.getTls().getZookeeper().getSecretName()),
                        getZookeeperDNSNames(selfSigned.getZookeeper()),
                        caIssuerName,
                        selfSigned.getPrivateKey());
            }

            if (TlsConfig.FunctionsWorkerTlsEntryConfig.isEnabled(globalSpec.getTls().getFunctionsWorker())) {
                createCertificatePerComponent(selfSigned.getFunctionsWorker(),
                        globalSpec.getComponents().getFunctionsWorkerBaseName(),
                        ObjectUtils.firstNonNull(selfSigned.getFunctionsWorker().getSecretName(),
                                globalSpec.getTls().getFunctionsWorker().getSecretName()),
                        getFunctionsWorkerDNSNames(selfSigned.getFunctionsWorker()),
                        caIssuerName,
                        selfSigned.getPrivateKey());
            }
            if (selfSigned.getExternal() != null) {
                for (Map.Entry<String, ComponentCertificateConfig> entry
                        : selfSigned.getExternal().entrySet()) {
                    final String serviceName = entry.getKey();
                    final ComponentCertificateConfig config = entry.getValue();

                    if (config.getGenerate() != null && config.getGenerate()) {
                        createCertificatePerComponent(
                                config,
                                serviceName,
                                config.getSecretName(),
                                getBaseK8sDnsNames(config, serviceName),
                                caIssuerName,
                                selfSigned.getPrivateKey()
                        );
                    }
                }
            }
        }
    }

    private void generateAcmeCertificates() {
        createAcmeIssuer();
        String issuerName = acme.getIssuer().getName();
        if (TlsConfig.TlsEntryConfig.isEnabled(globalSpec.getTls().getBroker())) {
            createCertificatePerComponent(
                    acme.getBroker(),
                    globalSpec.getComponents().getBrokerBaseName(),
                    ObjectUtils.firstNonNull(acme.getBroker().getSecretName(),
                            globalSpec.getTls().getBroker().getSecretName()),
                    mergeDnsNames(null, acme.getBroker()),
                    issuerName,
                    acme.getPrivateKey()
            );
        }
        if (TlsConfig.ProxyTlsEntryConfig.isEnabled(globalSpec.getTls().getProxy())) {
            createCertificatePerComponent(
                    acme.getProxy(),
                    globalSpec.getComponents().getProxyBaseName(),
                    ObjectUtils.firstNonNull(acme.getProxy().getSecretName(),
                            globalSpec.getTls().getProxy().getSecretName()),
                    mergeDnsNames(null, acme.getProxy()),
                    issuerName,
                    acme.getPrivateKey()
            );
        }
        if (acme.getExternal() != null) {
            for (Map.Entry<String, ComponentCertificateConfig> entry : acme.getExternal().entrySet()) {
                final String serviceName = entry.getKey();
                final ComponentCertificateConfig config = entry.getValue();

                if (Boolean.TRUE.equals(config.getGenerate())) {
                    createCertificatePerComponent(
                            config,
                            serviceName,
                            config.getSecretName(),
                            mergeDnsNames(null, config),
                            issuerName,
                            acme.getPrivateKey()
                    );
                }
            }
        }
    }

    public static void validateAcmeIssuerConfig(TlsConfig.AcmeIssuerConfig config) {
        if (config.getSolvers() == null || config.getSolvers().isEmpty()) {
            throw new IllegalArgumentException("At least one ACME solver must be configured");
        }
        for (TlsConfig.SolverConfig solver : config.getSolvers()) {
            int count = 0;
            if (solver.getHttp01() != null) count++;
            if (solver.getDns01() != null) count++;
            if (count != 1) {
                throw new IllegalArgumentException(
                        "Exactly one of http01 or dns01 must be configured per solver");
            }
            if (solver.getDns01() != null) {
                TlsConfig.Dns01Config dns = solver.getDns01();
                int providers = 0;
                if (dns.getRoute53() != null) providers++;
                if (dns.getCloudflare() != null) providers++;
                if (dns.getCloudDNS() != null) providers++;
                if (providers != 1) {
                    throw new IllegalArgumentException(
                            "Exactly one DNS provider must be configured per solver");
                }
            }
        }
    }

    private void createAcmeIssuer() {
        TlsConfig.AcmeIssuerConfig issuerConfig = acme.getIssuer();
        validateAcmeIssuerConfig(issuerConfig);
        List<ACMEChallengeSolver> solvers = new ArrayList<>();

        for (TlsConfig.SolverConfig solver : issuerConfig.getSolvers()) {
            ACMEChallengeSolverBuilder solverBuilder = new ACMEChallengeSolverBuilder();
            if (solver.getHttp01() != null) {
                TlsConfig.Http01Config http = solver.getHttp01();
                solverBuilder.withHttp01(
                        new ACMEChallengeSolverHTTP01Builder()
                                .withIngress(
                                        new ACMEChallengeSolverHTTP01IngressBuilder()
                                                .withIngressClassName(http.getIngressClass())
                                                .build()
                                )
                                .build()
                );
            }

            if (solver.getDns01() != null) {
                TlsConfig.Dns01Config dns = solver.getDns01();
                ACMEChallengeSolverDNS01Builder dnsBuilder = new ACMEChallengeSolverDNS01Builder();
                if (dns.getRoute53() != null) {
                    TlsConfig.Route53Config r = dns.getRoute53();
                    dnsBuilder
                            .withNewRoute53()
                            .withRegion(r.getRegion())
                            .withHostedZoneID(r.getHostedZoneId())
                            .endRoute53();
                } else if (dns.getCloudflare() != null) {
                    TlsConfig.CloudflareConfig cf = dns.getCloudflare();
                    dnsBuilder
                            .withNewCloudflare()
                            .withEmail(cf.getEmail())
                            .withNewApiTokenSecretRef(cf.getApiTokenSecretKey(), cf.getApiTokenSecretName())
                            .endCloudflare();
                } else if (dns.getCloudDNS() != null) {
                    TlsConfig.GoogleCloudDnsConfig g = dns.getCloudDNS();
                    dnsBuilder
                            .withNewCloudDNS()
                            .withProject(g.getProject())
                            .withNewServiceAccountSecretRef(g.getServiceAccountSecretKey(),
                                    g.getServiceAccountSecretName())
                            .endCloudDNS();
                }
                solverBuilder.withDns01(dnsBuilder.build());
            }
            solvers.add(solverBuilder.build());
        }

        Issuer issuer = new IssuerBuilder()
                .withNewMetadata()
                .withName(issuerConfig.getName())
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withAcme(
                        new ACMEIssuerBuilder()
                                .withServer(issuerConfig.getServer())
                                .withEmail(issuerConfig.getEmail())
                                .withNewPrivateKeySecretRef()
                                .withName(issuerConfig.getPrivateKeySecretName())
                                .endPrivateKeySecretRef()
                                .withSolvers(solvers)
                                .build()
                )
                .endSpec()
                .build();
        client.resource(issuer).inNamespace(namespace).createOrReplace();
    }

    private void createAutorecoveryCertificate() {
        // autorecovery only need to be accessed via the client tls auth, no dns names needed
        final ComponentCertificateConfig autorecoveryConfig = selfSigned.getAutorecovery();

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

    private void createCertificatePerComponent(final ComponentCertificateConfig componentConfig,
                                               final String baseName,
                                               final String secretName,
                                               List<String> dnsNames,
                                               String issuerName,
                                               CertificatePrivateKey provisionerPrivateKey) {
        if (componentConfig == null || !Boolean.TRUE.equals(componentConfig.getGenerate())) {
            return;
        }

        createCertificate(
                "%s-%s-tls".formatted(clusterSpecName, baseName),
                secretName,
                ObjectUtils.firstNonNull(componentConfig.getPrivateKey(), provisionerPrivateKey),
                dnsNames,
                issuerName
        );
    }

    private List<String> mergeDnsNames(List<String> k8sDnsNames, ComponentCertificateConfig config) {
        final List<String> finalDnsNames = new ArrayList<>(ObjectUtils.firstNonNull(k8sDnsNames, new ArrayList<>()));
        if (config != null && config.getDnsNames() != null) {
            finalDnsNames.addAll(config.getDnsNames());
        }
        return finalDnsNames;
    }

    private List<String> getDnsNamesForExternalServicesForGlobalCert() {
        final List<String> dnsNames = new ArrayList<>();
        if (selfSigned.getExternal() != null) {
            for (Map.Entry<String, ComponentCertificateConfig> entry
                    : selfSigned.getExternal().entrySet()) {
                final ComponentCertificateConfig config = entry.getValue();
                dnsNames.addAll(getBaseK8sDnsNames(config, entry.getKey()));
            }
        }
        return dnsNames;
    }

    private List<String> getBaseK8sDnsNames(ComponentCertificateConfig config, String serviceName) {
        List<String> k8sDnsNames = enumerateDnsNames(serviceName, true);
        return mergeDnsNames(k8sDnsNames, config);
    }

    private List<String> getBookKeeperDNSNames(ComponentCertificateConfig config) {
        final String componentBaseName = BookKeeperResourcesFactory.getComponentBaseName(globalSpec);
        List<String> k8sDnsNames = BookKeeperController
                .enumerateBookKeeperSets(clusterSpecName, componentBaseName, pulsarClusterSpec.getBookkeeper()).stream()
                .flatMap(set -> enumerateDnsNames(set, true).stream())
                .collect(Collectors.toList());
        return mergeDnsNames(k8sDnsNames, config);
    }

    private List<String> getFunctionsWorkerDNSNames(ComponentCertificateConfig config) {
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

    private List<String> getZookeeperDNSNames(ComponentCertificateConfig config) {
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

    private List<String> getProxyDNSNames(ComponentCertificateConfig config) {
        final String componentBaseName = ProxyResourcesFactory.getComponentBaseName(globalSpec);
        List<String> k8sDnsNames = ProxyController
                .enumerateProxySets(clusterSpecName, componentBaseName, pulsarClusterSpec.getProxy()).stream()
                .flatMap(set -> enumerateDnsNames(set, false).stream())
                .collect(Collectors.toList());
        return mergeDnsNames(k8sDnsNames, config);
    }

    private List<String> getBrokerDNSNames(ComponentCertificateConfig config) {
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
                                   List<String> dnsNames,
                                   String issuerName) {
        final Certificate certificate = new CertificateBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .endMetadata()
                .withNewSpec()
                .withPrivateKey(privateKey)
                .withSecretName(secretName)
                .withNewIssuerRef()
                .withName(issuerName)
                .endIssuerRef()
                .withDnsNames(dnsNames)
                .endSpec()
                .build();
        client.resource(certificate)
                .inNamespace(namespace)
                .createOrReplace();
        log.debugf("Created certificate %s mapped to secret %s", name, secretName);
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

    private void validateProvisionersConfig() {
        if (selfSigned == null || acme == null) {
            return;
        }
        validateComponent(
                "broker",
                selfSigned.getBroker(),
                acme.getBroker(),
                globalSpec.getTls().getBroker()
        );
        validateComponent(
                "proxy",
                selfSigned.getProxy(),
                acme.getProxy(),
                globalSpec.getTls().getProxy()
        );
        validateExternal(selfSigned.getExternal(), acme.getExternal());
    }

    private void validateComponent(
            String componentName,
            ComponentCertificateConfig selfSignedConfig,
            ComponentCertificateConfig acmeConfig,
            TlsConfig.TlsEntryConfig tlsEntryConfig) {
        if (selfSignedConfig == null || acmeConfig == null) {
            return;
        }

        if (Boolean.TRUE.equals(selfSignedConfig.getGenerate())
                && Boolean.TRUE.equals(acmeConfig.getGenerate())) {

            String ssSecret = ObjectUtils.firstNonNull(
                    selfSignedConfig.getSecretName(),
                    tlsEntryConfig.getSecretName()
            );
            String acmeSecret = ObjectUtils.firstNonNull(
                    acmeConfig.getSecretName(),
                    tlsEntryConfig.getSecretName()
            );

            if (Objects.equals(ssSecret, acmeSecret)) {
                throw new IllegalArgumentException(
                        String.format(
                                "Invalid TLS configuration: both selfSigned and ACME provisioners generate a %s "
                                        + "certificate using the same secret '%s'. This would cause certificates to "
                                        + "override each other.", componentName, ssSecret
                        )
                );
            }
        }
    }

    private void validateExternal(Map<String, ComponentCertificateConfig> selfSignedExternal,
            Map<String, ComponentCertificateConfig> acmeExternal) {

        if (selfSignedExternal == null || acmeExternal == null) {
            return;
        }
        for (Map.Entry<String, ComponentCertificateConfig> entry : selfSignedExternal.entrySet()) {
            String serviceName = entry.getKey();
            ComponentCertificateConfig ssConfig = entry.getValue();
            ComponentCertificateConfig acmeConfig = acmeExternal.get(serviceName);
            if (acmeConfig == null) {
                continue;
            }
            if (Boolean.TRUE.equals(ssConfig.getGenerate())
                    && Boolean.TRUE.equals(acmeConfig.getGenerate())) {

                String ssSecret = ssConfig.getSecretName();
                String acmeSecret = acmeConfig.getSecretName();

                if (Objects.equals(ssSecret, acmeSecret)) {
                    throw new IllegalArgumentException(
                            String.format("Invalid TLS configuration: both selfSigned and ACME provisioners generate an"
                                            + " external certificate for service '%s' using the same secret '%s'. "
                                            + "This would cause certificates to override each other.", serviceName,
                                    ssSecret
                            )
                    );
                }
            }
        }
    }
}