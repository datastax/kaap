package com.nicoloboschi;

import lombok.Data;

@Data
public class PulsarOperatorConfig {

    private long scaleIntervalMs = 30000;
    private String brokerWebServiceURL;
    private String brokerWebServiceURLTLS;
    private boolean tlsEnabledWithBroker;
}
