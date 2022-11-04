package com.datastax.oss.pulsaroperator.crds;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface WithGlobalSpec {

    @JsonIgnore
    GlobalSpec getGlobalSpec();
}
