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

import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProbesConfig {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProbeConfig {

        @JsonPropertyDescription("Enables the probe.")
        private Boolean enabled;
        @JsonPropertyDescription("Indicates the timeout (in seconds) for the probe.")
        private Integer timeoutSeconds;
        @JsonPropertyDescription("Indicates the initial delay (in seconds) for the probe.")
        private Integer initialDelaySeconds;
        @JsonPropertyDescription("Indicates the period (in seconds) for the probe.")
        private Integer periodSeconds;
        @JsonPropertyDescription("Failure threshold.")
        private Integer failureThreshold;
        @JsonPropertyDescription("Success threshold.")
        private Integer successThreshold;
        @JsonPropertyDescription("Indicates the termination grace period (in seconds) for the probe.")
        private Long terminationGracePeriodSeconds;
    }

    @JsonPropertyDescription(CRDConstants.DOC_PROBE_READINESS)
    private ProbeConfig readiness;
    @JsonPropertyDescription(CRDConstants.DOC_PROBE_LIVENESS)
    private ProbeConfig liveness;


}
