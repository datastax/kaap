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
package com.datastax.oss.kaap.crds.configs;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A minimal wrapper class for Kubernetes {@link io.fabric8.kubernetes.api.model.PodSecurityContext}
 * exposing only selected fields required for this CRD for security reasons.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityContextConfig {
    @JsonProperty("fsGroup")
    private Long fsGroup;
    @JsonProperty("fsGroupChangePolicy")
    private String fsGroupChangePolicy;
    @JsonProperty("runAsGroup")
    private Long runAsGroup;
    @JsonProperty("runAsNonRoot")
    private Boolean runAsNonRoot;
    @JsonProperty("runAsUser")
    private Long runAsUser;
    @JsonProperty("supplementalGroups")
    private List<Long> supplementalGroups;

    /**
     * Converts this wrapper into a full {@link PodSecurityContext} object.
     * Only sets the fields that are explicitly provided.
     */
    public PodSecurityContext toPodSecurityContext() {
        return new PodSecurityContextBuilder()
                .withFsGroup(fsGroup)
                .withFsGroupChangePolicy(fsGroupChangePolicy)
                .withRunAsGroup(runAsGroup)
                .withRunAsNonRoot(runAsNonRoot)
                .withRunAsUser(runAsUser)
                .withSupplementalGroups(supplementalGroups)
                .build();
    }
}
