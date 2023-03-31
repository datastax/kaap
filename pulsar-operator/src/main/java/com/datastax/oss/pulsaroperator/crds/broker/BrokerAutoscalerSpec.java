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
package com.datastax.oss.pulsaroperator.crds.broker;

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

    public static final String RESOURCE_USAGE_SOURCE_LOAD_BALANCER = "pulsar-load-balancer-report";
    public static final String RESOURCE_USAGE_SOURCE_K8S_METRICS = "k8s-metrics";

    Boolean enabled;
    @Min(1000)
    @javax.validation.constraints.Min(1000)
    Long periodMs;
    @Min(1)
    @javax.validation.constraints.Min(1)
    Integer min;
    Integer max;
    @Min(0)
    @Max(1)
    @javax.validation.constraints.Min(0)
    @javax.validation.constraints.Max(1)
    Double lowerCpuThreshold;
    @Min(0)
    @Max(1)
    @javax.validation.constraints.Min(0)
    @javax.validation.constraints.Max(1)
    Double higherCpuThreshold;
    @Min(1)
    @javax.validation.constraints.Min(1)
    Integer scaleUpBy;
    @Min(1)
    @javax.validation.constraints.Min(1)
    Integer scaleDownBy;
    @Min(1)
    @javax.validation.constraints.Min(1)
    Long stabilizationWindowMs;

    String resourceUsageSource;

}
