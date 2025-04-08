package com.datastax.oss.kaap.crds.metrics;

import org.apache.commons.lang3.StringUtils;

public class VectorMetricsConstants {

    public static String getConfig(String scrapeEndpoint,
                            String destEndpoint) {
        if (StringUtils.isEmpty(scrapeEndpoint)) {
            scrapeEndpoint = "http://0.0.0.0:8000/metrics";
        }
        String config = """
                [sources.metrics]
                type = "prometheus_scrape"
                endpoints = [ "%s" ]
                
                [sinks.aggregator_sink]
                type = "vector"
                inputs = [ "metrics" ]
                address = "%s"
                """;
        return config.formatted(scrapeEndpoint, destEndpoint);
    }
}
