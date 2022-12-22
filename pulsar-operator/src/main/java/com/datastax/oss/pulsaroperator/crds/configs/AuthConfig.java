package com.datastax.oss.pulsaroperator.crds.configs;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthConfig {

    /*
    .token(AuthConfig.TokenAuthenticationConfig.builder()
                    .publicKeyFile("my-public.key")
                    .privateKeyFile("my-private.key")
                    .superUserRoles(new TreeSet<>(Set.of("superuser", "admin", "websocket", "proxy")))
                    .proxyRoles(new TreeSet<>(Set.of("proxy")))
                    .initialize(true)
                    .build())
            .build();
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenAuthenticationConfig {

        @JsonPropertyDescription("Public key file name stored in the Secret. Default is 'my-public.key'")
        private String publicKeyFile;
        @JsonPropertyDescription("Private key file name stored in the Secret. Default is 'my-private.key'")
        private String privateKeyFile;
        @JsonPropertyDescription("Super user roles.")
        private TreeSet<String> superUserRoles;
        @JsonPropertyDescription("Proxy roles.")
        private TreeSet<String> proxyRoles;
        @JsonPropertyDescription("Initialize Secrets with new pair of keys and tokens for the super user roles. The generated Secret name is 'token-<role>'.")
        private Boolean initialize;

        public String superUserRolesAsString() {
            return convertRoleListToString(superUserRoles);
        }

        public String proxyRolesAsString() {
            return convertRoleListToString(proxyRoles);
        }

        private static String convertRoleListToString(Set<String> roles) {
            if (roles == null) {
                return "";
            }
            return roles.stream().collect(Collectors.joining(","));
        }

    }

    @JsonPropertyDescription("Enable authentication in the cluster. Default is 'false'.")
    Boolean enabled;
    @JsonPropertyDescription("Token based authentication configuration.")
    TokenAuthenticationConfig token;

}
