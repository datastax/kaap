package com.datastax.oss.pulsaroperator;

import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.quarkus.arc.Unremovable;
import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
@Unremovable
public class LeaderElectionConfig extends LeaderElectionConfiguration {

    public static final String PULSAR_OPERATOR_LEASE_NAME = "pulsar-operator-lease";

    public LeaderElectionConfig() {
        super(PULSAR_OPERATOR_LEASE_NAME);
    }
}