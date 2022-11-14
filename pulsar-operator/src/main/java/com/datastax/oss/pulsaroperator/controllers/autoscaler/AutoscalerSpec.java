package com.datastax.oss.pulsaroperator.controllers.autoscaler;

import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.WithDefaults;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import io.fabric8.generator.annotation.Max;
import io.fabric8.generator.annotation.Min;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoscalerSpec extends ValidableSpec<AutoscalerSpec> implements WithDefaults {

    private static Supplier<BrokerConfig> DEFAULT_BROKER_CONFIG = () -> BrokerConfig.builder()
            .enabled(false)
            .periodMs(TimeUnit.SECONDS.toMillis(10))
            .min(1)
            .lowerCpuThreshold(0.3f)
            .higherCpuThreshold(0.8f)
            .scaleUpBy(1)
            .scaleDownBy(1)
            .build();

    @Builder
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerConfig {
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
        Float lowerCpuThreshold;
        @Min(0)
        @Max(1)
        @javax.validation.constraints.Min(0)
        @javax.validation.constraints.Max(1)
        Float higherCpuThreshold;
        @Min(1)
        @javax.validation.constraints.Min(1)
        Integer scaleUpBy;
        @Min(1)
        @javax.validation.constraints.Min(1)
        Integer scaleDownBy;
    }

    @Valid
    BrokerConfig broker;


    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (broker == null) {
            broker = DEFAULT_BROKER_CONFIG.get();
        }
        broker.setEnabled(ObjectUtils.getFirstNonNull(
                () -> broker.getEnabled(),
                () -> DEFAULT_BROKER_CONFIG.get().getEnabled()
        ));
        broker.setPeriodMs(ObjectUtils.getFirstNonNull(
                () -> broker.getPeriodMs(),
                () -> DEFAULT_BROKER_CONFIG.get().getPeriodMs()
        ));
        broker.setMin(ObjectUtils.getFirstNonNull(
                () -> broker.getMin(),
                () -> DEFAULT_BROKER_CONFIG.get().getMin()
        ));
        broker.setMax(ObjectUtils.getFirstNonNull(
                () -> broker.getMax(),
                () -> DEFAULT_BROKER_CONFIG.get().getMax()
        ));
        broker.setLowerCpuThreshold(ObjectUtils.getFirstNonNull(
                () -> broker.getLowerCpuThreshold(),
                () -> DEFAULT_BROKER_CONFIG.get().getLowerCpuThreshold()
        ));
        broker.setHigherCpuThreshold(ObjectUtils.getFirstNonNull(
                () -> broker.getHigherCpuThreshold(),
                () -> DEFAULT_BROKER_CONFIG.get().getHigherCpuThreshold()
        ));
        broker.setScaleUpBy(ObjectUtils.getFirstNonNull(
                () -> broker.getScaleUpBy(),
                () -> DEFAULT_BROKER_CONFIG.get().getScaleUpBy()
        ));
        broker.setScaleDownBy(ObjectUtils.getFirstNonNull(
                () -> broker.getScaleDownBy(),
                () -> DEFAULT_BROKER_CONFIG.get().getScaleDownBy()
        ));
    }

    @Override
    public boolean isValid(AutoscalerSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
