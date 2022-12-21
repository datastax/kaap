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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenConfig {

        @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
        private String publicKeyFile;
        @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
        private String privateKeyFile;
        @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
        private TreeSet<String> superUserRoles;
        @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
        private TreeSet<String> proxyRoles;
        @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
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

    @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
    Boolean enabled;
    TokenConfig token;

}
