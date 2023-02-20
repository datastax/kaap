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
package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.crds.configs.AdditionalVolumesConfig;
import com.datastax.oss.pulsaroperator.crds.configs.AntiAffinityConfig;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.Toleration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.ObjectUtils;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseComponentSpec<T> extends ValidableSpec<T> implements WithDefaults {

    @JsonPropertyDescription(CRDConstants.DOC_IMAGE)
    protected String image;
    @JsonPropertyDescription(CRDConstants.DOC_IMAGE_PULL_POLICY)
    private String imagePullPolicy;
    @JsonPropertyDescription(CRDConstants.DOC_NODE_SELECTORS)
    protected Map<String, String> nodeSelectors;
    @JsonPropertyDescription(CRDConstants.DOC_REPLICAS)
    protected Integer replicas;
    @JsonPropertyDescription("Pod disruption budget configuration.")
    private PodDisruptionBudgetConfig pdb;
    @JsonPropertyDescription("Mount additional volumes to the pod.")
    private AdditionalVolumesConfig additionalVolumes;
    @JsonPropertyDescription(CRDConstants.DOC_TOLERATIONS)
    private List<Toleration> tolerations;
    @JsonPropertyDescription(CRDConstants.DOC_NODE_AFFINITY)
    private NodeAffinity nodeAffinity;
    @JsonPropertyDescription(CRDConstants.DOC_ANTIAFFINITY)
    private AntiAffinityConfig antiAffinity;
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
    @JsonPropertyDescription(CRDConstants.DOC_CONTAINER_ENV)
    private List<EnvVar> env;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (image == null) {
            image = globalSpec.getImage();
        }
        nodeSelectors = mergeMaps(globalSpec.getNodeSelectors(), nodeSelectors);
        if (imagePullPolicy == null) {
            imagePullPolicy = globalSpec.getImagePullPolicy();
        }
        applyPdbDefault();
    }

    public void applyPdbDefault() {
        PodDisruptionBudgetConfig defaultPdb = getDefaultPdb();
        if (pdb != null) {
            pdb.setEnabled(ObjectUtils.firstNonNull(pdb.getEnabled(), defaultPdb.getEnabled()));
            pdb.setMaxUnavailable(ObjectUtils.firstNonNull(pdb.getMaxUnavailable(),
                    defaultPdb.getMaxUnavailable()));
        } else {
            pdb = defaultPdb;
        }
    }

    public static <T> Map<String, T> mergeMaps(Map<String, T> parent, Map<String, T> child) {
        if (parent == null) {
            return child;
        }
        if (child == null) {
            return parent;
        }
        Map<String, T> result = new HashMap<>(parent);
        result.putAll(child);
        return result;
    }


    protected abstract PodDisruptionBudgetConfig getDefaultPdb();
}
