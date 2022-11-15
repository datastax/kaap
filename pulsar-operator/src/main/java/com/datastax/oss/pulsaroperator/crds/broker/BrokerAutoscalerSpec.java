package com.datastax.oss.pulsaroperator.crds.broker;

import io.fabric8.crd.generator.annotation.SchemaFrom;
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
    @SchemaFrom(type = float.class)
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

}
