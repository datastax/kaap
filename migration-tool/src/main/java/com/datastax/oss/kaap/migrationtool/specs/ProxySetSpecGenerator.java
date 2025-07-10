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

import com.datastax.oss.kaap.controllers.proxy.ProxyResourcesFactory;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.crds.proxy.ProxySetSpec;
import com.datastax.oss.kaap.migrationtool.InputClusterSpecs;
import com.datastax.oss.kaap.migrationtool.PulsarClusterResourceGenerator;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProxySetSpecGenerator extends BaseSpecGenerator<ProxySetSpec> {

    public static final String SPEC_NAME = "Proxy";
    private final InputClusterSpecs.ProxySpecs.ProxySetSpecs proxyInputSpecs;
    private final String resourceName;
    private final String resourceNameSvc;
    private ProxySetSpec generatedSpec;
    private PodDNSConfig podDNSConfig;
    private boolean isRestartOnConfigMapChange;
    private String priorityClassName;
    private Map<String, Object> config;
    private TlsConfig.TlsEntryConfig tlsEntryConfig;

    public ProxySetSpecGenerator(InputClusterSpecs inputSpecs, InputClusterSpecs.ProxySpecs.ProxySetSpecs proxyInputSpecs,
                                 KubernetesClient client, String resourceNameSvc) {
        super(inputSpecs, client);
        this.proxyInputSpecs = proxyInputSpecs;
        final String clusterName = inputSpecs.getClusterName();
        resourceName = ProxyResourcesFactory.getResourceName(clusterName,
                inputSpecs.getProxy().getBaseName(), proxyInputSpecs.getName(), proxyInputSpecs.getOverrideName());
        this.resourceNameSvc = resourceNameSvc.isEmpty() ? resourceName : resourceNameSvc;
        internalGenerateSpec();
    }

    @Override
    public String getSpecName() {
        return SPEC_NAME;
    }

    @Override
    public ProxySetSpec generateSpec() {
        return generatedSpec;
    }

    @Override
    public boolean isEnabled() {
        return generatedSpec.getReplicas() > 0;
    }

    public void internalGenerateSpec() {
        final ConfigMap configMap = requireConfigMap(resourceName);
        final ConfigMap configMapWs = getConfigMap(resourceName + "-ws");
        final Deployment deployment = requireDeployment(resourceName);
        final PodDisruptionBudget pdb = getPodDisruptionBudget(resourceName);
        final Service service = requireService(resourceNameSvc);
        if (configMapWs != null) {
            assertConfigMapsCompatible(configMap.getData(), configMapWs.getData());
        }

        verifyLabelsEquals(deployment, configMap, configMapWs, pdb);

        final DeploymentSpec deploymentSpec = deployment.getSpec();
        final PodSpec spec = deploymentSpec.getTemplate()
                .getSpec();
        final Container mainContainer = getContainerByName(spec.getContainers(), resourceName);
        final Container wsContainer = getContainerByName(spec.getContainers(), resourceName + "-ws");
        final ProxySetSpec.WebSocketConfig wsConfig;
        if (wsContainer != null) {
            wsConfig = ProxySetSpec.WebSocketConfig.builder()
                    .enabled(true)
                    .resources(wsContainer.getResources())
                    .config(convertConfigMapData(configMapWs))
                    .probes(createProbeConfig(wsContainer))
                    .build();
        } else {
            wsConfig = ProxySetSpec.WebSocketConfig.builder()
                    .enabled(false)
                    .build();
        }


        podDNSConfig = spec.getDnsConfig();
        isRestartOnConfigMapChange = isPodDependantOnConfigMap(deploymentSpec.getTemplate());
        priorityClassName = spec.getPriorityClassName();
        config = convertConfigMapData(configMap);
        tlsEntryConfig = createTlsEntryConfig(deployment);

        final NodeAffinity nodeAffinity = spec.getAffinity() == null ? null : spec.getAffinity().getNodeAffinity();
        final Map<String, String> matchLabels = getMatchLabels(deploymentSpec.getSelector());
        generatedSpec = ProxySetSpec.builder()
                .overrideResourceName(proxyInputSpecs.getOverrideName())
                .annotations(cleanupAnnotations(deployment.getMetadata().getAnnotations()))
                .podAnnotations(cleanupAnnotations(deploymentSpec.getTemplate().getMetadata().getAnnotations()))
                .image(mainContainer.getImage())
                .imagePullPolicy(mainContainer.getImagePullPolicy())
                .nodeSelectors(spec.getNodeSelector())
                .replicas(deploymentSpec.getReplicas())
                .nodeAffinity(nodeAffinity)
                .probes(createProbeConfig(mainContainer))
                .labels(deployment.getMetadata().getLabels())
                .podLabels(deploymentSpec.getTemplate().getMetadata().getLabels())
                .matchLabels(matchLabels)
                .config(config)
                .gracePeriod(spec.getTerminationGracePeriodSeconds() == null ? null :
                        spec.getTerminationGracePeriodSeconds().intValue())
                .resources(mainContainer.getResources())
                .tolerations(deploymentSpec.getTemplate().getSpec().getTolerations())
                .imagePullSecrets(deploymentSpec.getTemplate().getSpec().getImagePullSecrets())
                .service(createServiceConfig(service))
                .updateStrategy(deploymentSpec.getStrategy())
                .pdb(createPodDisruptionBudgetConfig(pdb))
                .webSocket(wsConfig)
                .antiAffinity(createAntiAffinityConfig(spec))
                .env(mainContainer.getEnv())
                .initContainers(spec.getInitContainers())
                .sidecars(getSidecars(spec, ProxyResourcesFactory.getContainerNames(resourceName)))
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
        String tokenPublicKey = (String) getConfig().get("tokenPublicKey");
        if (tokenPublicKey == null) {
            return null;
        }
        return getPublicKeyFileFromFileURL(tokenPublicKey);
    }

    private TlsConfig.ProxyTlsEntryConfig createTlsEntryConfig(Deployment deployment) {
        boolean tlsEnabled = parseConfigValueBool(getConfig(), "tlsEnabledInProxy");
        if (!tlsEnabled) {
            return null;
        }
        final Volume certs = deployment.getSpec().getTemplate().getSpec().getVolumes().stream()
                .filter(v -> "certs".equals(v.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tls volume with name 'certs' found"));
        final String secretName = certs.getSecret().getSecretName();

        return TlsConfig.ProxyTlsEntryConfig.proxyBuilder()
                .enabled(true)
                .enabledWithBroker(parseConfigValueBool(getConfig(), "tlsEnabledWithBroker"))
                .secretName(secretName)
                .build();
    }

    private ProxySetSpec.ServiceConfig createServiceConfig(Service service) {
        Map<String, String> annotations = service.getMetadata().getAnnotations();
        if (annotations == null) {
            annotations = new HashMap<>();
        }
        boolean hasPlainTextPort = service.getSpec().getPorts().stream()
                .filter(p -> p.getPort() == ProxyResourcesFactory.DEFAULT_HTTP_PORT).findFirst().isPresent();
        return ProxySetSpec.ServiceConfig.builder()
                .annotations(cleanupAnnotations(annotations))
                .additionalPorts(removeServicePorts(service.getSpec().getPorts(),
                        ProxyResourcesFactory.DEFAULT_HTTP_PORT,
                        ProxyResourcesFactory.DEFAULT_HTTPS_PORT,
                        ProxyResourcesFactory.DEFAULT_PULSAR_PORT,
                        ProxyResourcesFactory.DEFAULT_PULSARSSL_PORT,
                        ProxyResourcesFactory.DEFAULT_WSS_PORT,
                        ProxyResourcesFactory.DEFAULT_WS_PORT
                ))
                .loadBalancerIP(service.getSpec().getLoadBalancerIP())
                .type(service.getSpec().getType())
                .enablePlainTextWithTLS(hasPlainTextPort)
                .build();
    }

    private static void assertConfigMapsCompatible(Map<String, String> configMap, Map<String, String> wsConfigMap) {
        assertSameEntryValue(configMap, wsConfigMap, "authenticationEnabled");
        if (boolConfigMapValue(configMap, "authenticationEnabled")) {
            PulsarClusterResourceGenerator
                    .checkAuthenticationProvidersContainTokenAuth(configMap, SPEC_NAME);
            PulsarClusterResourceGenerator
                    .checkAuthenticationProvidersContainTokenAuth(wsConfigMap, SPEC_NAME + " WS");
        }
        assertSameEntryValue(configMap, wsConfigMap, "tlsTrustCertsFilePath");
    }

    private static void assertSameEntryValue(Map<String, String> configMap, Map<String, String> wsConfigMap,
                                             String prop) {
        if (!Objects.equals(configMap.get(prop), wsConfigMap.get(prop))) {
            throw new IllegalArgumentException(
                    "Incompatible proxy config maps: " + prop + " must be the same, got " + configMap.get(prop)
                            + " and " + wsConfigMap.get(prop));
        }
    }

}
