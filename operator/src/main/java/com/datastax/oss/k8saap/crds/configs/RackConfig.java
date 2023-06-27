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
package com.datastax.oss.k8saap.crds.configs;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RackConfig {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HostRackTypeConfig {

        @JsonPropertyDescription("Enable the rack affinity rules.")
        private Boolean enabled;

        @JsonPropertyDescription("Indicates if the podAffinity rules will be enforced. Default is false. "
                + "If required, the affinity rule will be enforced using "
                + "'requiredDuringSchedulingIgnoredDuringExecution'.")
        private Boolean requireRackAffinity;

        @JsonPropertyDescription("Indicates if the podAntiAffinity rules will be enforced. Default is true. "
                + "If required, the affinity rule will be enforced using "
                + "'requiredDuringSchedulingIgnoredDuringExecution'.")
        private Boolean requireRackAntiAffinity;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ZoneRackTypeConfig {

        @JsonPropertyDescription("Enable the rack affinity rules.")
        private Boolean enabled;

        @JsonPropertyDescription(
                "Enable the host anti affinity. If set, all the pods of the same rack will deployed on different "
                        + "nodes of the same zone."
                        + "Default is true.")
        private Boolean enableHostAntiAffinity;

        @JsonPropertyDescription("Indicates if the podAffinity rules will be enforced. Default is false. "
                + "If required, the affinity rule will be enforced using "
                + "'requiredDuringSchedulingIgnoredDuringExecution'.")
        private Boolean requireRackAffinity;

        @JsonPropertyDescription("Indicates if the podAntiAffinity rules will be enforced. Default is true. "
                + "If required, the affinity rule will be enforced using "
                + "'requiredDuringSchedulingIgnoredDuringExecution'.")
        private Boolean requireRackAntiAffinity;

        @JsonPropertyDescription(
                "Indicates if the podAntiAffinity rules will be enforced for the host. Default is true. "
                        + "If required, the affinity rule will be enforced using "
                        + "'requiredDuringSchedulingIgnoredDuringExecution'.")
        private Boolean requireRackHostAntiAffinity;
    }


    @JsonPropertyDescription("Enable rack rules based on the hostname of the node.")
    private HostRackTypeConfig host;

    @JsonPropertyDescription("Enable rack rules based on the failure domain (availability zone) of the node.")
    private ZoneRackTypeConfig zone;

}
