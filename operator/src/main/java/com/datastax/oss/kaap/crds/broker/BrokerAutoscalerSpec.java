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
package com.datastax.oss.kaap.crds.broker;

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
public class BrokerAutoscalerSpec {

    public static final String RESOURCE_USAGE_SOURCE_LOAD_BALANCER = "PulsarLBReport";
    public static final String RESOURCE_USAGE_SOURCE_K8S_METRICS = "K8SMetrics";

    @JsonPropertyDescription("Enable autoscaling for brokers.")
    Boolean enabled;
    @Min(1000)
    @jakarta.validation.constraints.Min(1000)
    @JsonPropertyDescription("The interval in milliseconds between two consecutive autoscaling checks.")
    Long periodMs;
    @Min(1)
    @jakarta.validation.constraints.Min(1)
    @JsonPropertyDescription("Min number of brokers. If the number of brokers is equals to this value, the autoscaler"
            + " will never scale down.")
    Integer min;
    @JsonPropertyDescription("Max number of brokers. If the number of brokers is equals to this value, the autoscaler"
            + " will never scale up.")
    Integer max;
    @Min(0)
    @Max(1)
    @jakarta.validation.constraints.Min(0)
    @jakarta.validation.constraints.Max(1)
    @JsonPropertyDescription("The threshold to trigger a scale down. The autoscaler will scale down if all the "
            + "brokers cpu usage is lower than this threshold. Default is '0.4'")
    Double lowerCpuThreshold;
    @Min(0)
    @Max(1)
    @jakarta.validation.constraints.Min(0)
    @jakarta.validation.constraints.Max(1)
    @JsonPropertyDescription("The threshold to trigger a scale up. The autoscaler will scale up if all the "
            + "brokers cpu usage is higher than this threshold. Default is '0.8'")
    Double higherCpuThreshold;
    @Min(1)
    @jakarta.validation.constraints.Min(1)
    @JsonPropertyDescription("The number of brokers to add at each scale up. Default is '1'")
    Integer scaleUpBy;
    @Min(1)
    @jakarta.validation.constraints.Min(1)
    @JsonPropertyDescription("The number of brokers to remove at each scale down. Default is '1'")
    Integer scaleDownBy;
    @Min(1)
    @jakarta.validation.constraints.Min(1)
    @JsonPropertyDescription("The stabilization window is used to restrict the flapping of replica count when the metrics used for scaling keep fluctuating. "
            + "The autoscaling algorithm uses this window to infer a previous desired state and avoid unwanted changes to workload scale."
            + "Default value is 5 minutes after the pod readiness.")
    Long stabilizationWindowMs;

    @JsonPropertyDescription("Source for getting the brokers resources usage. "
            + "Possible values are 'PulsarLBReport' and 'K8SMetrics'. Default is 'PulsarLBReport'")
    String resourcesUsageSource;

}
