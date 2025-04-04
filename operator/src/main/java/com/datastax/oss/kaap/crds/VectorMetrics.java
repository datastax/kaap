package com.datastax.oss.kaap.crds;

import com.datastax.oss.kaap.crds.validation.ValidableSpec;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.ConstraintValidatorContext;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VectorMetrics extends ValidableSpec<VectorMetrics> {
    Boolean enabled;
    String image;
    String name;
    String endpoint;

    @Override
    public boolean isValid(VectorMetrics vectorMetrics, ConstraintValidatorContext context) {
        if (vectorMetrics == null || !vectorMetrics.getEnabled()) {
            return true;
        }
        if (vectorMetrics.getEndpoint() == null) {
            context.buildConstraintViolationWithTemplate("""
                    Vector Metrics field does not have an endpoint.
                    """).addConstraintViolation();
            return false;
        }
        return true;
    }
}
