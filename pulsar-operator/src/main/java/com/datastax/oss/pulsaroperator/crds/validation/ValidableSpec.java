package com.datastax.oss.pulsaroperator.crds.validation;

import javax.validation.ConstraintValidator;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
public abstract class ValidableSpec<T> implements ConstraintValidator<ValidSpec, T> {

}
