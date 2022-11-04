package com.datastax.oss.pulsaroperator.crds.validation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Retention;
import javax.validation.Constraint;
import javax.validation.Payload;

@Retention(RUNTIME)
@Constraint(validatedBy = {})
public @interface ValidSpec {

    String message() default "The spec is not valid";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
