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
