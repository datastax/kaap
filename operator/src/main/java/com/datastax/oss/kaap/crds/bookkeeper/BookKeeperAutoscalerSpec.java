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
package com.datastax.oss.kaap.crds.bookkeeper;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Max;
import io.fabric8.generator.annotation.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookKeeperAutoscalerSpec {

    @JsonPropertyDescription("Enable autoscaling for bookies.")
    Boolean enabled;

    @Min(1000)
    @jakarta.validation.constraints.Min(1000)
    @JsonPropertyDescription("The interval in milliseconds between two consecutive autoscaling checks.")
    Long periodMs;

    // should be around bookie's diskUsageWarnThreshold + diskUsageLwmThreshold / 2
    @Min(0.0d)
    @Max(1.0d)
    @JsonPropertyDescription("The threshold to trigger a scale down. The autoscaler will scale down if all the "
            + "bookies' disk usage is lower than this threshold. Default is '0.92'")
    Double diskUsageToleranceHwm;

    // should be around bookie's diskUsageLwmThreshold or below
    @Min(0.0d)
    @Max(1.0d)
    @JsonPropertyDescription("The threshold to trigger a scale up. The autoscaler will scale up if all the "
            + "bookies' disk usage is higher than this threshold. Default is '0.75'")
    Double diskUsageToleranceLwm;

    @Min(1)
    @jakarta.validation.constraints.Min(1)
    @JsonPropertyDescription(
            "Min number of writable bookies. The autoscaler will scale up if not enough writable bookies are detected"
                    + ". For instance, if a bookie went to read-only mode, "
                    + "the autoscaler will scale up to replace it. Default is '3'.")
    Integer minWritableBookies;

    @Min(1)
    @jakarta.validation.constraints.Min(1)
    @JsonPropertyDescription("The number of bookies to add at each scale up. Default is '1'")
    Integer scaleUpBy;

    @Min(1)
    @jakarta.validation.constraints.Min(1)
    @JsonPropertyDescription("Max number of bookies. If the number of bookies is equals to this value, the "
            + "autoscaler will never scale up.")
    Integer scaleUpMaxLimit;

    @Min(1)
    @jakarta.validation.constraints.Min(1)
    @JsonPropertyDescription("The number of bookies to remove at each scale down. Default is '1'")
    Integer scaleDownBy;

    @Min(1)
    @jakarta.validation.constraints.Min(1)
    @JsonPropertyDescription(
            "The stabilization window is used to restrict the flapping of replica count when the metrics used for "
                    + "scaling keep fluctuating. "
                    + "The autoscaling algorithm uses this window to infer a previous desired state and avoid "
                    + "unwanted changes to workload scale."
                    + "Default value is 5 minutes after the pod readiness.")
    Long stabilizationWindowMs;


}
