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

import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.client.utils.URLUtils;
import javax.validation.ConstraintValidatorContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VectorMetrics extends ValidableSpec<VectorMetrics> {
    @JsonPropertyDescription("Enable vector metrics exporter.")
    private Boolean enabled;
    @JsonPropertyDescription("Vector image name")
    private String image;
    @JsonPropertyDescription(CRDConstants.DOC_IMAGE_PULL_POLICY)
    private String imagePullPolicy;
    @JsonPropertyDescription("Container name of vector metrics exporter.")
    private String name;
    @JsonPropertyDescription("Endpoint for /metrics, eg. https://0.0.0.0:8000 (default)."
            + "If config is provided, this will not be used.")
    private String scrapeEndpoint;
    @JsonPropertyDescription("Endpoint for vector aggregator sink "
            + "If config is provided, this will not be used.")
    private String sinkEndpoint;
    @JsonPropertyDescription("Config for vector. Overrides existing config.")
    private String config;

    @JsonIgnore
    public boolean isEndpointValid() {
        return URLUtils.isValidURL(getSinkEndpoint());
    }

    @JsonIgnore
    public boolean configIsNotEmpty() {
        return StringUtils.isNotEmpty(getConfig());
    }

    @Override
    public boolean isValid(VectorMetrics vectorMetrics, ConstraintValidatorContext context) {
        if (vectorMetrics == null || !vectorMetrics.getEnabled()) {
            return true;
        }
        if (isEndpointValid() || configIsNotEmpty()) {
            return true;
        }
        throw new IllegalStateException(
                "vectorMetrics: sinkEndpoint must be an url or config must not be empty");
    }
}
