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
package com.datastax.oss.kaap.crds.configs.tls;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.certmanager.api.model.v1.CertificatePrivateKey;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TlsConfig {
    @JsonPropertyDescription("Deprecated. Use the 'enabled' field in each component spec instead.")
    @Deprecated
    Boolean enabled;
    @JsonPropertyDescription("Secret name used by each component to load TLS certificates. "
            + "Each component can load a different secret by setting the 'secretName' entry in the tls component spec.")
    String defaultSecretName;
    @JsonPropertyDescription("Path in the container filesystem where the TLS CA certificates are retrieved. "
            + "It has to point to a certificate file. The default value is /etc/ssl/certs/ca-certificates.crt.")
    String caPath;
    @JsonPropertyDescription("Certificate provisioner configuration.")
    CertProvisionerConfig certProvisioner;
    @JsonPropertyDescription("TLS configurations related to the ZooKeeper component.")
    TlsEntryConfig zookeeper;
    @JsonPropertyDescription("TLS configurations related to the BookKeeper component.")
    TlsEntryConfig bookkeeper;
    @JsonPropertyDescription("TLS configurations related to the Broker component.")
    TlsEntryConfig broker;
    @JsonPropertyDescription("TLS configurations related to the Broker resource sets.")
    Map<String, TlsEntryConfig> brokerResourceSets;
    @JsonPropertyDescription("TLS configurations related to the Proxy component.")
    ProxyTlsEntryConfig proxy;
    @JsonPropertyDescription("TLS configurations related to the Proxy resource sets.")
    Map<String, ProxyTlsEntryConfig> proxyResourceSets;
    @JsonPropertyDescription("TLS configurations related to the Functions worker component.")
    FunctionsWorkerTlsEntryConfig functionsWorker;
    @JsonPropertyDescription("TLS configurations related to the Autorecovery component.")
    TlsEntryConfig autorecovery;
    @JsonPropertyDescription("TLS configurations used by additional components, such as the Bastion component.")
    TlsEntryConfig ssCa;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsEntryConfig {
        public static boolean isEnabled(TlsEntryConfig tlsEntryConfig) {
            if (tlsEntryConfig != null
                    && tlsEntryConfig.getEnabled() != null
                    && tlsEntryConfig.getEnabled()) {
                return true;
            }
            return false;
        }
        @JsonPropertyDescription("Enable TLS.")
        Boolean enabled;
        @JsonPropertyDescription("Override the default secret name from where to load the certificates.")
        String secretName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    public static class ProxyTlsEntryConfig extends TlsEntryConfig {

        @Builder(builderMethodName = "proxyBuilder")
        public ProxyTlsEntryConfig(Boolean enabled, String secretName, Boolean enabledWithBroker) {
            super(enabled, secretName);
            this.enabledWithBroker = enabledWithBroker;
        }

        @JsonPropertyDescription("Enable TLS for the proxy to broker connections.")
        Boolean enabledWithBroker;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    public static class FunctionsWorkerTlsEntryConfig extends TlsEntryConfig {
        @Builder(builderMethodName = "functionsWorkerBuilder")
        public FunctionsWorkerTlsEntryConfig(Boolean enabled, String secretName, Boolean enabledWithBroker) {
            super(enabled, secretName);
            this.enabledWithBroker = enabledWithBroker;
        }
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
        @JsonPropertyDescription("Generate a different certificate for each component.")
        Boolean perComponent;
        @JsonPropertyDescription("Secret where to store the root CA certificate.")
        String caSecretName;

        @JsonPropertyDescription("Zookeeper self signed certificate config.")
        SelfSignedCertificatePerComponentConfig zookeeper;
        @JsonPropertyDescription("Bookkeeper self signed certificate config.")
        SelfSignedCertificatePerComponentConfig bookkeeper;
        @JsonPropertyDescription("Broker self signed certificate config.")
        SelfSignedCertificatePerComponentConfig broker;
        @JsonPropertyDescription("Proxy self signed certificate config.")
        SelfSignedCertificatePerComponentConfig proxy;
        @JsonPropertyDescription("Functions worker self signed certificate config.")
        SelfSignedCertificatePerComponentConfig functionsWorker;
        @JsonPropertyDescription("Autorecovery self signed certificate config.")
        SelfSignedCertificatePerComponentConfig autorecovery;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SelfSignedCertificatePerComponentConfig {
        @JsonPropertyDescription("Generate self signed certificates for the component.")
        Boolean generate;
        @JsonPropertyDescription("Cert-manager options for generating the private key.")
        CertificatePrivateKey privateKey;
    }
}
