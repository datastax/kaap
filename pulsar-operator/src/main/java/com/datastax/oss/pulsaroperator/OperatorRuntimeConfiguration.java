package com.datastax.oss.pulsaroperator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;


@ConfigMapping(prefix = "pulsar-operator")
public interface OperatorRuntimeConfiguration {

    @WithDefault("5")
    Integer reconciliationRescheduleSeconds();
}
