package com.datastax.oss.pulsaroperator.crds;

import java.util.Arrays;

public class SpecDiffer {

    private SpecDiffer() {
    }

    public static boolean specsAreEquals(Object spec1, Object spec2) {
        if (spec1 == null && spec2 == null) {
            return true;
        }
        if (spec1 == null) {
            return false;
        }
        if (spec2 == null) {
            return false;
        }
        return Arrays.equals(SerializationUtil.writeAsJsonBytes(spec1), SerializationUtil.writeAsJsonBytes(spec2));
    }

    public static boolean specsAreEquals(Object spec1, byte[] spec2) {
        if (spec1 == null && spec2 == null) {
            return true;
        }
        if (spec1 == null) {
            return false;
        }
        if (spec2 == null) {
            return false;
        }
        return Arrays.equals(SerializationUtil.writeAsJsonBytes(spec1), spec2);
    }

}
