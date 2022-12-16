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
    Integer scaleUpBy = 1;

    @Min(1)
    @javax.validation.constraints.Min(1)
    Integer scaleDownBy = 1;

    @Min(1)
    @javax.validation.constraints.Min(1)
    Long stabilizationWindowMs;

}
