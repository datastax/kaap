package com.datastax.oss.pulsaroperator.crds;

public interface WithDefaults {

    void applyDefaults(GlobalSpec globalSpec);
}
