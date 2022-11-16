package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import java.util.Map;
import java.util.function.Supplier;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSpec extends ValidableSpec<GlobalSpec> implements WithDefaults {


    private static final Supplier<TlsConfig> DEFAULT_TLS_CONFIG = () -> TlsConfig.builder()
            .enabled(false)
            .defaultSecretName("pulsar-tls")
            .build();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Components {
        @JsonPropertyDescription("Zookeeper base name. Default value is 'zookeeper'.")
        private String zookeeperBaseName;
        @JsonPropertyDescription("BookKeeper base name. Default value is 'bookkeeper'.")
        private String bookkeeperBaseName;
        @JsonPropertyDescription("Broker base name. Default value is 'broker'.")
        private String brokerBaseName;
        @JsonPropertyDescription("Proxy base name. Default value is 'proxy'.")
        private String proxyBaseName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsConfig {
        @JsonPropertyDescription("Global switch to turn on or off the TLS configurations.")
        private Boolean enabled;
        @JsonPropertyDescription("Default secret name.")
        private String defaultSecretName;
        @JsonPropertyDescription("TLS configurations related to the ZooKeeper component.")
        TlsEntryConfig zookeeper;
        @JsonPropertyDescription("TLS configurations related to the BookKeeper component.")
        TlsEntryConfig bookkeeper;
        @JsonPropertyDescription("TLS configurations related to the broker component.")
        TlsEntryConfig broker;
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
    @JsonPropertyDescription("Pulsar cluster components names.")
    private Components components;
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
        applyComponentsDefaults();

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
        applyTlsDefaults();

    }

    private void applyTlsDefaults() {
        if (tls == null) {
            tls = DEFAULT_TLS_CONFIG.get();
        }
        tls.setEnabled(ObjectUtils.getFirstNonNull(
                () -> tls.getEnabled(),
                () -> DEFAULT_TLS_CONFIG.get().getEnabled())
        );
        tls.setDefaultSecretName(ObjectUtils.getFirstNonNull(
                () -> tls.getDefaultSecretName(),
                () -> DEFAULT_TLS_CONFIG.get().getDefaultSecretName())
        );
    }

    private void applyComponentsDefaults() {
        if (components == null) {
            components = new Components();
        }
        components.setZookeeperBaseName(ObjectUtils.firstNonNull(components.getZookeeperBaseName(), "zookeeper"));
        components.setBookkeeperBaseName(ObjectUtils.firstNonNull(components.getBookkeeperBaseName(), "bookkeeper"));
        components.setBrokerBaseName(ObjectUtils.firstNonNull(components.getBrokerBaseName(), "broker"));
        components.setProxyBaseName(ObjectUtils.firstNonNull(components.getProxyBaseName(), "proxy"));
    }

    @Override
    public boolean isValid(GlobalSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
