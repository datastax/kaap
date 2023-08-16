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
package com.datastax.oss.kaap.migrationtool.specs;

import com.datastax.oss.kaap.controllers.bastion.BastionResourcesFactory;
import com.datastax.oss.kaap.crds.bastion.BastionSpec;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.migrationtool.InputClusterSpecs;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BastionSpecGenerator extends BaseSpecGenerator<BastionSpec> {

    public static final String SPEC_NAME = "Bastion";
    private final String resourceName;
    private BastionSpec generatedSpec;
    private PodDNSConfig podDNSConfig;
    private boolean isRestartOnConfigMapChange;
    private String priorityClassName;
    private Map<String, Object> config;
    private TlsConfig.TlsEntryConfig tlsEntryConfig;

    public BastionSpecGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        super(inputSpecs, client);
        final String clusterName = inputSpecs.getClusterName();
        resourceName = BastionResourcesFactory.getResourceName(clusterName,
                inputSpecs.getBastion().getBaseName());
        internalGenerateSpec();
    }

    @Override
    public String getSpecName() {
        return SPEC_NAME;
    }

    @Override
    public BastionSpec generateSpec() {
        return generatedSpec;
    }

    @Override
    public boolean isEnabled() {
        return generatedSpec.getReplicas() > 0;
    }

    public void internalGenerateSpec() {
        final ConfigMap configMap = getConfigMap(resourceName);
        if (configMap == null) {
            generatedSpec = BastionSpec.builder().replicas(0).build();
            return;
        }
        final Deployment deployment = requireDeployment(resourceName);

        verifyLabelsEquals(deployment, configMap);

        final DeploymentSpec deploymentSpec = deployment.getSpec();
        final PodSpec spec = deploymentSpec.getTemplate()
                .getSpec();
        final Container container = spec
                .getContainers()
                .get(0);
        podDNSConfig = spec.getDnsConfig();
        isRestartOnConfigMapChange = isPodDependantOnConfigMap(deploymentSpec.getTemplate());
        priorityClassName = spec.getPriorityClassName();
        config = convertConfigMapData(configMap);
        tlsEntryConfig = createTlsEntryConfig(deployment);

        final NodeAffinity nodeAffinity = spec.getAffinity() == null ? null : spec.getAffinity().getNodeAffinity();
        final Map<String, String> matchLabels = getMatchLabels(deploymentSpec.getSelector());
        generatedSpec = BastionSpec.builder()
                .annotations(cleanupAnnotations(deployment.getMetadata().getAnnotations()))
                .podAnnotations(cleanupAnnotations(deploymentSpec.getTemplate().getMetadata().getAnnotations()))
                .image(container.getImage())
                .imagePullPolicy(container.getImagePullPolicy())
                .nodeSelectors(spec.getNodeSelector())
                .replicas(deploymentSpec.getReplicas())
                .nodeAffinity(nodeAffinity)
                .labels(deployment.getMetadata().getLabels())
                .podLabels(deploymentSpec.getTemplate().getMetadata().getLabels())
                .matchLabels(matchLabels)
                .config(config)
                .gracePeriod(spec.getTerminationGracePeriodSeconds() == null ? null :
                        spec.getTerminationGracePeriodSeconds().intValue())
                .resources(container.getResources())
                .tolerations(deploymentSpec.getTemplate().getSpec().getTolerations())
                .imagePullSecrets(deploymentSpec.getTemplate().getSpec().getImagePullSecrets())
                .antiAffinity(createAntiAffinityConfig(spec))
                .env(container.getEnv())
                .initContainers(spec.getInitContainers())
                .sidecars(getSidecars(spec, BastionResourcesFactory.getContainerNames(resourceName)))
                .build();
    }


    @Override
    public PodDNSConfig getPodDnsConfig() {
        return podDNSConfig;
    }

    @Override
    public boolean isRestartOnConfigMapChange() {
        return isRestartOnConfigMapChange;
    }

    @Override
    public String getDnsName() {
        return null;
    }

    @Override
    public String getPriorityClassName() {
        return priorityClassName;
    }

    @Override
    public Map<String, Object> getConfig() {
        return config;
    }

    @Override
    public TlsConfig.TlsEntryConfig getTlsEntryConfig() {
        return tlsEntryConfig;
    }

    @Override
    public String getTlsCaPath() {
        if (tlsEntryConfig != null) {
            return Objects.requireNonNull((String) getConfig().get("tlsTrustCertsFilePath"));
        }
        return null;
    }

    @Override
    public String getAuthPublicKeyFile() {
        return null;
    }

    private TlsConfig.TlsEntryConfig createTlsEntryConfig(Deployment deployment) {
        final Volume certs = deployment.getSpec().getTemplate().getSpec().getVolumes().stream()
                .filter(v -> "certs".equals(v.getName()))
                .findFirst()
                .orElse(null);
        if (certs == null) {
            return null;
        }
        final String secretName = certs.getSecret().getSecretName();

        return TlsConfig.TlsEntryConfig.builder()
                .enabled(true)
                .secretName(secretName)
                .build();
    }

}
