package com.datastax.oss.pulsaroperator.crds;

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
    public static class AntiAffinityConfig {
        AntiAffinityHostConfig host = new AntiAffinityHostConfig();
        AntiAffinityZoneConfig zone = new AntiAffinityZoneConfig();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AntiAffinityHostConfig {
        boolean enabled = true;
        String mode = "required";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AntiAffinityZoneConfig {
        boolean enabled = false;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsConfig {
        TlsEntryConfig zookeeper = new TlsEntryConfig();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsEntryConfig {
        boolean enabled;
        String tlsSecretName;
    }


    @NotNull
    private String name;
    private PodDNSConfig dnsConfig;
    private boolean priorityClass;
    protected Map<String, String> nodeSelectors;
    private boolean enableAntiAffinity;
    private AntiAffinityConfig antiAffinity;
    private boolean enableTls;
    private TlsConfig tls = new TlsConfig();
    private boolean persistence;

    // overridable parameters
    String image;
    @Builder.Default
    private String imagePullPolicy = "IfNotPresent";


    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
    }

    @Override
    public boolean isValid(GlobalSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
