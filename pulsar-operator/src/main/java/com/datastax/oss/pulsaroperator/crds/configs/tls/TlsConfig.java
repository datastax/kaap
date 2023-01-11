package com.datastax.oss.pulsaroperator.crds.configs.tls;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TlsConfig {
    @JsonPropertyDescription("Global switch to turn on or off the TLS configurations.")
    Boolean enabled;
    @JsonPropertyDescription("Default secret name.")
    String defaultSecretName;
    @JsonPropertyDescription("Default secret name.")
    CertProvisionerConfig certProvisioner;
    @JsonPropertyDescription("TLS configurations related to the ZooKeeper component.")
    TlsEntryConfig zookeeper;
    @JsonPropertyDescription("TLS configurations related to the BookKeeper component.")
    TlsEntryConfig bookkeeper;
    @JsonPropertyDescription("TLS configurations related to the broker component.")
    TlsEntryConfig broker;
    @JsonPropertyDescription("TLS configurations related to the proxy component.")
    ProxyTlsEntryConfig proxy;
    @JsonPropertyDescription("TLS configurations related to the proxy component.")
    FunctionsWorkerTlsEntryConfig functionsWorker;
    @JsonPropertyDescription("TLS configurations related to the broker component.")
    TlsEntryConfig ssCa;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsEntryConfig {
        @JsonPropertyDescription("Enable tls for this component.")
        Boolean enabled;
        @JsonPropertyDescription("Enable certificates for this component.")
        String secretName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProxyTlsEntryConfig {
        @JsonPropertyDescription("Enable tls for this component.")
        Boolean enabled;
        @JsonPropertyDescription("Enable certificates for this component.")
        String secretName;
        @JsonPropertyDescription("Enable tls for this component.")
        Boolean enabledWithBroker;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FunctionsWorkerTlsEntryConfig {
        @JsonPropertyDescription("Enable tls for this component.")
        Boolean enabled;
        @JsonPropertyDescription("Enable certificates for this component.")
        String secretName;
        @JsonPropertyDescription("Enable tls for this component.")
        Boolean enabledWithBroker;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CertProvisionerConfig {
        @JsonPropertyDescription("Enable tls for this component.")
        SelfSignedCertProvisionerConfig selfSigned;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SelfSignedCertProvisionerConfig {
        @JsonPropertyDescription("Enable tls for this component.")
        Boolean enabled;
        @JsonPropertyDescription("Enable certificates for this component.")
        String mode;
        @JsonPropertyDescription("Enable certificates for this component.")
        Boolean includeDns;
        @JsonPropertyDescription("Enable certificates for this component.")
        Map<String, Object> privateKey;
    }
}

