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
package com.datastax.oss.kaap.crds;

import com.datastax.oss.kaap.crds.configs.AdditionalVolumesConfig;
import com.datastax.oss.kaap.crds.configs.AntiAffinityConfig;
import com.datastax.oss.kaap.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.kaap.crds.configs.SecurityContextConfig;
import com.datastax.oss.kaap.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.Toleration;
import java.util.List;
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
    @JsonPropertyDescription(CRDConstants.DOC_IMAGE_PULL_SECRETS)
    private List<LocalObjectReference> imagePullSecrets;
    @JsonPropertyDescription(CRDConstants.DOC_CONTAINER_ENV)
    private List<EnvVar> env;
    @JsonPropertyDescription(CRDConstants.DOC_SIDECARS)
    private List<Container> sidecars;
    @JsonPropertyDescription(CRDConstants.DOC_INIT_CONTAINERS)
    private List<Container> initContainers;
    @JsonPropertyDescription(CRDConstants.DOC_SERVICE_ACCOUNT_NAME)
    private String serviceAccountName;
    @JsonPropertyDescription(CRDConstants.DOC_SECURITY_CONTEXT)
    private SecurityContextConfig securityContext;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (image == null) {
            image = globalSpec.getImage();
        }
        if (imagePullPolicy == null) {
            imagePullPolicy = globalSpec.getImagePullPolicy();
        }
        applyPdbDefault();
    }

    public void applyPdbDefault() {
        PodDisruptionBudgetConfig defaultPdb = getDefaultPdb();
        if (pdb != null) {
            pdb.setEnabled(ObjectUtils.firstNonNull(pdb.getEnabled(), defaultPdb.getEnabled()));
            if (defaultPdb.getMinAvailable() != null || pdb.getMinAvailable() != null) {
                pdb.setMinAvailable(ObjectUtils.firstNonNull(pdb.getMinAvailable(),
                    defaultPdb.getMinAvailable()));
                pdb.setMaxUnavailable(null);
            } else if (defaultPdb.getMaxUnavailable() != null || pdb.getMaxUnavailable() != null) {
                pdb.setMaxUnavailable(ObjectUtils.firstNonNull(pdb.getMaxUnavailable(),
                    defaultPdb.getMaxUnavailable()));
                pdb.setMinAvailable(null);
            }
        } else {
            pdb = defaultPdb;
        }
    }

    /**
     * Default security context used by bookkeeper, zookeeper and functionsWorker
     */
    protected void applyFsGroup0IfSecurityContextMissing() {
        if (securityContext == null) {
            securityContext = SecurityContextConfig
                    .builder()
                    .fsGroup(0L)
                    .build();
        }
    }

    protected abstract PodDisruptionBudgetConfig getDefaultPdb();
}
