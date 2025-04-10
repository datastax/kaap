/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
