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
package com.datastax.oss.pulsaroperator.migrationtool.specs;

import com.datastax.oss.pulsaroperator.controllers.autorecovery.AutorecoveryResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.autorecovery.AutorecoverySpec;
import com.datastax.oss.pulsaroperator.crds.configs.AntiAffinityConfig;
import com.datastax.oss.pulsaroperator.crds.configs.tls.TlsConfig;
import com.datastax.oss.pulsaroperator.migrationtool.InputClusterSpecs;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutorecoverySpecGenerator extends BaseSpecGenerator<AutorecoverySpec> {

    public static final String SPEC_NAME = "Autorecovery";
    private final String resourceName;
    private AutorecoverySpec generatedSpec;
    private AntiAffinityConfig antiAffinityConfig;
    private PodDNSConfig podDNSConfig;
    private boolean isRestartOnConfigMapChange;
    private String priorityClassName;
    private Map<String, Object> config;
    private TlsConfig.TlsEntryConfig tlsEntryConfig;

    public AutorecoverySpecGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        super(inputSpecs, client);
        final String clusterName = inputSpecs.getClusterName();
        resourceName = AutorecoveryResourcesFactory.getResourceName(clusterName,
                inputSpecs.getAutorecovery().getBaseName());
        internalGenerateSpec();
    }

    @Override
    public String getSpecName() {
        return SPEC_NAME;
    }

    @Override
    public List<HasMetadata> getAllResources() {
        return resources;
    }


    @Override
    public AutorecoverySpec generateSpec() {
        return generatedSpec;
    }

    public void internalGenerateSpec() {
        final ConfigMap configMap = requireConfigMap(resourceName);
        final Deployment deployment = requireDeployment(resourceName);
        addResource(configMap);
        addResource(deployment);

        verifyLabelsEquals(deployment, configMap);

        final DeploymentSpec deploymentSpec = deployment.getSpec();
        final PodSpec spec = deploymentSpec.getTemplate()
                .getSpec();
        final Container container = spec
                .getContainers()
                .get(0);
        podDNSConfig = spec.getDnsConfig();
        antiAffinityConfig = createAntiAffinityConfig(spec);
        isRestartOnConfigMapChange = isPodDependantOnConfigMap(deploymentSpec.getTemplate());
        priorityClassName = spec.getPriorityClassName();
        config = convertConfigMapData(configMap);
        tlsEntryConfig = createTlsEntryConfig(deployment);

        final NodeAffinity nodeAffinity = spec.getAffinity() == null ? null : spec.getAffinity().getNodeAffinity();
        final Map<String, String> matchLabels = getMatchLabels(deploymentSpec.getSelector());
        generatedSpec = AutorecoverySpec.builder()
                .annotations(deployment.getMetadata().getAnnotations())
                .podAnnotations(deploymentSpec.getTemplate().getMetadata().getAnnotations())
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
                .build();
    }


    @Override
    public PodDNSConfig getPodDnsConfig() {
        return podDNSConfig;
    }

    @Override
    public AntiAffinityConfig getAntiAffinityConfig() {
        return antiAffinityConfig;
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

    private TlsConfig.TlsEntryConfig createTlsEntryConfig(Deployment deployment) {
        final String tlsProvider = (String) getConfig().get("tlsProvider");
        if (!"OpenSSL".equals(tlsProvider)) {
            return null;
        }
        final Volume certs = deployment.getSpec().getTemplate().getSpec().getVolumes().stream()
                .filter(v -> v.getName().equals("certs"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tls volume with name 'certs' found"));
        final String secretName = certs.getSecret().getSecretName();

        return TlsConfig.TlsEntryConfig.builder()
                .enabled(true)
                .secretName(secretName)
                .build();
    }

}
