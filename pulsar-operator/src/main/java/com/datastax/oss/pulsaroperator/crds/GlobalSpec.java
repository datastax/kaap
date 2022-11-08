package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import java.util.Map;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSpec extends ValidableSpec<GlobalSpec> implements WithDefaults {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsConfig {
        @JsonPropertyDescription("Global switch to turn on or off the TLS configurations.")
        private boolean enabled;
        @JsonPropertyDescription("TLS configurations related to the ZooKeeper component.")
        TlsEntryConfig zookeeper;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsEntryConfig {
        @JsonPropertyDescription("Enable tls for this component.")
        boolean enabled;
        @JsonPropertyDescription("Enable certificates for this component.")
        String tlsSecretName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GlobalStorageConfig {
        @JsonPropertyDescription("Indicates if a StorageClass is used. The operator will create the StorageClass if needed.")
        private StorageClassConfig storageClass;
        @JsonPropertyDescription("Indicates if an already existing storage class should be used.")
        private String existingStorageClassName;
    }


    @NotNull
    @Required
    @JsonPropertyDescription("Pulsar cluster base name.")
    private String name;
    @JsonPropertyDescription("Additional DNS config for each pod created by the operator.")
    private PodDNSConfig dnsConfig;
    @JsonPropertyDescription("""
            The domain name for your kubernetes cluster.
            This domain is documented here: https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#a-aaaa-records-1 .
            It's used to fully qualify service names when configuring Pulsar.
            The default value is 'cluster.local'.
            """)
    private String kubernetesClusterDomain;
    @JsonPropertyDescription("Global node selector. If set, this will apply to all components.")
    protected Map<String, String> nodeSelectors;
    @JsonPropertyDescription("TLS configuration for the cluster.")
    private TlsConfig tls;
    @JsonPropertyDescription("""
            If persistence is enabled, components that has state will be deployed with PersistentVolumeClaims, otherwise, for test purposes, they will be deployed with emptDir
            """)
    private Boolean persistence;

    // overridable parameters
    @JsonPropertyDescription("Default Pulsar image to use. Any components can be configured to use a different image.")
    private String image;
    @JsonPropertyDescription("Default Pulsar image pull policy to use. Any components can be configured to use a different image pull policy. Default value is 'IfNotPresent'.")
    private String imagePullPolicy;
    @JsonPropertyDescription("Storage configuration.")
    private GlobalStorageConfig storage;


    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (kubernetesClusterDomain == null) {
            kubernetesClusterDomain = "cluster.local";
        }
        if (imagePullPolicy == null) {
            imagePullPolicy = "IfNotPresent";
        }
        if (persistence == null) {
            persistence = true;
        }
        if (storage == null) {
            storage = new GlobalStorageConfig();
        }
        if (storage.getStorageClass() == null && storage.getExistingStorageClassName() == null) {
            storage.setExistingStorageClassName("default");
        }
        if (storage.getStorageClass() != null && storage.getStorageClass().getReclaimPolicy() == null) {
            storage.getStorageClass().setReclaimPolicy("Retain");
        }
    }

    @Override
    public boolean isValid(GlobalSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
