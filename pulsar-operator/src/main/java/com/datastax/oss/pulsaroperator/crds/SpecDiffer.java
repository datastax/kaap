package com.datastax.oss.pulsaroperator.crds;

import org.apache.commons.lang3.builder.ReflectionDiffBuilder;
import org.apache.commons.lang3.builder.StandardToStringStyle;

public class SpecDiffer {

    private SpecDiffer() {
    }

    public static boolean specsAreEquals(Object spec1, Object spec2) {
        return new ReflectionDiffBuilder<>(
                spec1, spec2, new StandardToStringStyle()
        )
                .build()
                .getNumberOfDiffs() == 0;

    }

}
