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
package com.datastax.oss.pulsaroperator.crds.bookkeeper;

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

    Boolean enabled;

    @Min(1000)
    @javax.validation.constraints.Min(1000)
    Long periodMs;

    // should be around bookie's diskUsageWarnThreshold + diskUsageLwmThreshold / 2
    @Min(0.0d)
    @Max(1.0d)
    Double diskUsageToleranceHwm;

    // should be around bookie's diskUsageLwmThreshold or below
    @Min(0.0d)
    @Max(1.0d)
    Double diskUsageToleranceLwm;

    @Min(1)
    @javax.validation.constraints.Min(1)
    Integer minWritableBookies;

    @Min(1)
    @javax.validation.constraints.Min(1)
    Integer scaleUpBy;

    @Min(1)
    @javax.validation.constraints.Min(1)
    Integer scaleDownBy;

    @Min(1)
    @javax.validation.constraints.Min(1)
    Long stabilizationWindowMs;

    Boolean cleanUpPvcs;
}
