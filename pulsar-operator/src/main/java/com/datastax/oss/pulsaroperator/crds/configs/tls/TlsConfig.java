package com.datastax.oss.pulsaroperator.crds.configs.tls;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.certmanager.api.model.v1.CertificatePrivateKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TlsConfig {
    @JsonPropertyDescription("Global switch to turn on or off the TLS configurations. Additionally, you have configure each component section.")
    Boolean enabled;
    @JsonPropertyDescription("Secret name used by each component to load TLS certificates. "
            + "Each component can load a different secret by setting the 'secretName' entry in the tls component spec.")
    String defaultSecretName;
    @JsonPropertyDescription("Certificate provisioner configuration.")
    CertProvisionerConfig certProvisioner;
    @JsonPropertyDescription("TLS configurations related to the ZooKeeper component.")
    TlsEntryConfig zookeeper;
    @JsonPropertyDescription("TLS configurations related to the BookKeeper component.")
    TlsEntryConfig bookkeeper;
    @JsonPropertyDescription("TLS configurations related to the Broker component.")
    TlsEntryConfig broker;
    @JsonPropertyDescription("TLS configurations related to the Proxy component.")
    ProxyTlsEntryConfig proxy;
    @JsonPropertyDescription("TLS configurations related to the Functions worker component.")
    FunctionsWorkerTlsEntryConfig functionsWorker;
    @JsonPropertyDescription("TLS configurations used by additional components, such as the Bastion component.")
    TlsEntryConfig ssCa;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsEntryConfig {
        @JsonPropertyDescription("Enable TLS.")
        Boolean enabled;
        @JsonPropertyDescription("Override the default secret name from where to load the certificates.")
        String secretName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProxyTlsEntryConfig {
        @JsonPropertyDescription("Enable TLS.")
        Boolean enabled;
        @JsonPropertyDescription("Override the default secret name from where to load the certificates.")
        String secretName;
        @JsonPropertyDescription("Enable TLS for the proxy to broker connections.")
        Boolean enabledWithBroker;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FunctionsWorkerTlsEntryConfig {
        @JsonPropertyDescription("Enable TLS.")
        Boolean enabled;
        @JsonPropertyDescription("Override the default secret name from where to load the certificates.")
        String secretName;
        @JsonPropertyDescription("Enable TLS for the functions worker to broker connections.")
        Boolean enabledWithBroker;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CertProvisionerConfig {
        @JsonPropertyDescription("Self signed certificate provisioner configuration.")
        SelfSignedCertProvisionerConfig selfSigned;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SelfSignedCertProvisionerConfig {
        @JsonPropertyDescription("Generate self signed certificates for broker, proxy and functions worker.")
        Boolean enabled;
        @JsonPropertyDescription("Include dns name in the DNS names covered by the certificate.")
        Boolean includeDns;
        @JsonPropertyDescription("Cert-manager options for generating the private key.")
        CertificatePrivateKey privateKey;
    }
}

