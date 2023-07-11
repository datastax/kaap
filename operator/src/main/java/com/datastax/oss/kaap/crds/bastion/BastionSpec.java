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
package com.datastax.oss.kaap.crds.bastion;

import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.ConfigUtil;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.WithDefaults;
import com.datastax.oss.kaap.crds.configs.AntiAffinityConfig;
import com.datastax.oss.kaap.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.crd.generator.annotation.SchemaFrom;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Toleration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Min;
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
    // workaround to generate CRD spec that accepts any type as key
    @SchemaFrom(type = JsonNode.class)
    protected Map<String, Object> config;
    @JsonPropertyDescription(CRDConstants.DOC_REPLICAS)
    protected Integer replicas;
    @JsonPropertyDescription(CRDConstants.DOC_ANNOTATIONS)
    private Map<String, String> annotations;
    @JsonPropertyDescription(CRDConstants.DOC_POD_ANNOTATIONS)
    private Map<String, String> podAnnotations;
    @JsonPropertyDescription(CRDConstants.DOC_LABELS)
    private Map<String, String> labels;
    @JsonPropertyDescription(CRDConstants.DOC_POD_LABELS)
    private Map<String, String> podLabels;
    @JsonPropertyDescription(CRDConstants.DOC_POD_MATCH_LABELS)
    private Map<String, String> matchLabels;
    @JsonPropertyDescription(CRDConstants.DOC_IMAGE_PULL_SECRETS)
    private List<LocalObjectReference> imagePullSecrets;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription(CRDConstants.DOC_GRACE_PERIOD)
    private Integer gracePeriod;
    @JsonPropertyDescription(CRDConstants.DOC_RESOURCES)
    private ResourceRequirements resources;
    @JsonPropertyDescription("Indicates to connect to proxy or the broker. The default value depends whether Proxy is deployed or not.")
    private Boolean targetProxy;
    @JsonPropertyDescription(CRDConstants.DOC_TOLERATIONS)
    private List<Toleration> tolerations;
    @JsonPropertyDescription(CRDConstants.DOC_NODE_AFFINITY)
    private NodeAffinity nodeAffinity;
    @JsonPropertyDescription(CRDConstants.DOC_ANTIAFFINITY)
    private AntiAffinityConfig antiAffinity;
    @JsonPropertyDescription(CRDConstants.DOC_CONTAINER_ENV)
    private List<EnvVar> env;
    @JsonPropertyDescription(CRDConstants.DOC_SIDECARS)
    private List<Container> sidecars;
    @JsonPropertyDescription(CRDConstants.DOC_INIT_CONTAINERS)
    private List<Container> initContainers;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (image == null) {
            image = globalSpec.getImage();
        }
        nodeSelectors = ConfigUtil.mergeMaps(globalSpec.getNodeSelectors(), nodeSelectors);
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
