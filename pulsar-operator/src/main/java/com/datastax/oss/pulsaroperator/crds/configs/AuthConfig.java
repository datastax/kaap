package com.datastax.oss.pulsaroperator.crds.configs;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import java.util.List;
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
        private List<String> superUserRoles;
        @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
        private List<String> proxyRoles;
        @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
        private TokenAuthProvisionerConfig provisioner;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenAuthProvisionerConfig {

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class RbacConfig {
            @JsonPropertyDescription("Create needed RBAC to run the Functions Worker.")
            private Boolean create;
            @JsonPropertyDescription("Whether or not the RBAC is created per-namespace or for the cluster.")
            private Boolean namespaced;
        }

        @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
        private Boolean initialize;
        @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
        private String image;
        @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
        private String imagePullPolicy;
        @JsonPropertyDescription("Resource requirements for the ZooKeeper pod.")
        private ResourceRequirements resources;
        @JsonPropertyDescription("Resource requirements for the ZooKeeper pod.")
        private RbacConfig rbac;



    }

    @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
    Boolean enabled;
    TokenConfig token;

}
