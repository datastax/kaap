package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
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
        private boolean enabled;
        TlsEntryConfig zookeeper;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsEntryConfig {
        boolean enabled;
        String tlsSecretName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GlobalStorageConfig {
        private StorageClassConfig storageClass;
        private String existingStorageClassName;
    }


    @NotNull
    private String name;
    private PodDNSConfig dnsConfig;
    protected Map<String, String> nodeSelectors;
    private TlsConfig tls;
    private Boolean persistence;

    // overridable parameters
    private String image;
    private String imagePullPolicy;
    private GlobalStorageConfig storage;


    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
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
