package com.datastax.oss.pulsaroperator.autoscaler.broker;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

public interface BrokerResourceUsageSource {

    @Data
    @AllArgsConstructor
    class ResourceUsage {
        String pod;
        float percentCpu;
    }

    List<ResourceUsage> getBrokersResourceUsages();

}
