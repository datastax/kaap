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
    public static class RackTypeConfig {

        @JsonPropertyDescription("Enable the rack affinity rules.")
        private Boolean enabled;

        @JsonPropertyDescription("Indicates if the podAffinity rules will be enforced. Default is false. "
                + "If required, the affinity rule will be enforced using 'requiredDuringSchedulingIgnoredDuringExecution'.")
        private Boolean requireRackAffinity;

        @JsonPropertyDescription("Indicates if the podAntiAffinity rules will be enforced. Default is true. "
                + "If required, the affinity rule will be enforced using 'requiredDuringSchedulingIgnoredDuringExecution'.")
        private Boolean requireRackAntiAffinity;
    }


    @JsonPropertyDescription("Enable rack rules based on the hostname of the node.")
    private RackTypeConfig host;

    @JsonPropertyDescription("Enable rack rules based on the failure domain (availability zone) of the node.")
    private RackTypeConfig zone;

}
