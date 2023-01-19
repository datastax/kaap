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
package com.datastax.oss.pulsaroperator.crds.bastion;

import static com.datastax.oss.pulsaroperator.crds.BaseComponentSpec.mergeMaps;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.WithDefaults;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import java.util.Map;
import java.util.function.Supplier;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BastionSpec extends ValidableSpec<BastionSpec> implements WithDefaults {

    private static final Supplier<ResourceRequirements> DEFAULT_RESOURCE_REQUIREMENTS =
            () -> new ResourceRequirementsBuilder()
                    .withRequests(Map.of("memory", Quantity.parse("256Mi"), "cpu", Quantity.parse("0.25")))
                    .build();


    @JsonPropertyDescription(CRDConstants.DOC_IMAGE)
    protected String image;
    @JsonPropertyDescription(CRDConstants.DOC_IMAGE_PULL_POLICY)
    private String imagePullPolicy;
    @JsonPropertyDescription(CRDConstants.DOC_NODE_SELECTORS)
    protected Map<String, String> nodeSelectors;
    @JsonPropertyDescription(CRDConstants.DOC_CONFIG)
    protected Map<String, String> config;
    @JsonPropertyDescription(CRDConstants.DOC_REPLICAS)
    protected Integer replicas;
    @JsonPropertyDescription(CRDConstants.DOC_ANNOTATIONS)
    private Map<String, String> annotations;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription(CRDConstants.DOC_GRACE_PERIOD)
    private Integer gracePeriod;
    @JsonPropertyDescription(CRDConstants.DOC_RESOURCES)
    private ResourceRequirements resources;
    @JsonPropertyDescription("Indicates to connect to proxy or the broker. The default value depends whether Proxy is deployed or not.")
    private Boolean targetProxy;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (image == null) {
            image = globalSpec.getImage();
        }
        nodeSelectors = mergeMaps(globalSpec.getNodeSelectors(), nodeSelectors);
        if (imagePullPolicy == null) {
            imagePullPolicy = globalSpec.getImagePullPolicy();
        }
        if (replicas == null) {
            replicas = 1;
        }
        if (gracePeriod == null) {
            gracePeriod = 60;
        }
        if (resources == null) {
            resources = DEFAULT_RESOURCE_REQUIREMENTS.get();
        }
    }

    @Override
    public boolean isValid(BastionSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
