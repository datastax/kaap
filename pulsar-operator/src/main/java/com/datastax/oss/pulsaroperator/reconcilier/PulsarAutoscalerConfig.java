package com.datastax.oss.pulsaroperator.reconcilier;

import lombok.Data;

@Data
public class PulsarAutoscalerConfig {

    private long scaleIntervalMs = 30000;
    private String brokerWebServiceURL;
    private String brokerWebServiceURLTLS;
    private boolean tlsEnabledWithBroker;
}
