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


    @NotNull
    private String name;
    private PodDNSConfig dnsConfig;
    protected Map<String, String> nodeSelectors;
    private TlsConfig tls;
    private boolean persistence;

    // overridable parameters
    String image;
    private String imagePullPolicy;


    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (imagePullPolicy == null) {
            imagePullPolicy = "IfNotPresent";
        }
    }

    @Override
    public boolean isValid(GlobalSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
