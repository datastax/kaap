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

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.function.FunctionsWorkerResourcesFactory;
import com.datastax.oss.kaap.crds.configs.VolumeConfig;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.crds.function.FunctionsWorkerSpec;
import com.datastax.oss.kaap.migrationtool.InputClusterSpecs;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FunctionsWorkerSpecGenerator extends BaseSpecGenerator<FunctionsWorkerSpec> {

    public static final String SPEC_NAME = "Functions Worker";
    private final String resourceName;
    private FunctionsWorkerSpec generatedSpec;
    private PodDNSConfig podDNSConfig;
    private boolean isRestartOnConfigMapChange;
    private String priorityClassName;
    private Map<String, Object> config;
    private TlsConfig.TlsEntryConfig tlsEntryConfig;

    public FunctionsWorkerSpecGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        super(inputSpecs, client);
        final String clusterName = inputSpecs.getClusterName();
        resourceName = FunctionsWorkerResourcesFactory.getResourceName(clusterName,
                inputSpecs.getFunctionsWorker().getBaseName());
        internalGenerateSpec();
    }

    @Override
    public String getSpecName() {
        return SPEC_NAME;
    }

    @Override
    public FunctionsWorkerSpec generateSpec() {
        return generatedSpec;
    }

    @Override
    public boolean isEnabled() {
        return generatedSpec.getReplicas() > 0;
    }

    public void internalGenerateSpec() {
        final ConfigMap configMap = getConfigMap(resourceName);
        if (configMap == null) {
            generatedSpec = FunctionsWorkerSpec.builder().replicas(0).build();
            return;
        }
        final ConfigMap configMapExtra = requireConfigMap(resourceName + "-extra");
        final StatefulSet statefulSet = requireStatefulSet(resourceName);
        final PodDisruptionBudget pdb = getPodDisruptionBudget(resourceName);
        final Service service = requireService(resourceName);
        final Service caService = requireService(resourceName + "-ca");

        verifyLabelsEquals(statefulSet, configMap, configMapExtra, pdb);
        verifyLabelsEquals(service, caService);

        final StatefulSetSpec statefulSetSpec = statefulSet.getSpec();
        final PodSpec spec = statefulSetSpec.getTemplate()
                .getSpec();
        final Container mainContainer = getContainerByName(spec.getContainers(), resourceName);
        podDNSConfig = spec.getDnsConfig();
        isRestartOnConfigMapChange = isPodDependantOnConfigMap(statefulSetSpec.getTemplate());
        priorityClassName = spec.getPriorityClassName();
        final Map<String, Object> extraConfigMapData = convertConfigMapData(configMapExtra);
        final Map<String, Object> mainConfigData =
                SerializationUtil.readYaml(configMap.getData().get("functions_worker.yml"), Map.class);
        config = new HashMap<>();
        config.putAll(extraConfigMapData);
        config.putAll(mainConfigData);
        tlsEntryConfig = createTlsEntryConfig(statefulSet);

        final NodeAffinity nodeAffinity = spec.getAffinity() == null ? null : spec.getAffinity().getNodeAffinity();
        final Map<String, String> matchLabels = getMatchLabels(statefulSetSpec.getSelector());
        final List<PersistentVolumeClaim> volumeClaimTemplates = statefulSetSpec.getVolumeClaimTemplates();
        final VolumeConfig logsVolumeConfig;
        if (!volumeClaimTemplates.isEmpty()) {
            logsVolumeConfig = createVolumeConfig(resourceName, volumeClaimTemplates.get(0));
        } else {
            logsVolumeConfig = null;
        }

        generatedSpec = FunctionsWorkerSpec.builder()
                .annotations(cleanupAnnotations(statefulSet.getMetadata().getAnnotations()))
                .podAnnotations(cleanupAnnotations(statefulSetSpec.getTemplate().getMetadata().getAnnotations()))
                .image(mainContainer.getImage())
                .imagePullPolicy(mainContainer.getImagePullPolicy())
                .nodeSelectors(spec.getNodeSelector())
                .replicas(statefulSetSpec.getReplicas())
                .nodeAffinity(nodeAffinity)
                .probes(createProbeConfig(mainContainer))
                .labels(statefulSet.getMetadata().getLabels())
                .skipVolumeClaimLabels(true)
                .podLabels(statefulSetSpec.getTemplate().getMetadata().getLabels())
                .matchLabels(matchLabels)
                .config(config)
                .gracePeriod(spec.getTerminationGracePeriodSeconds() == null ? null :
                        spec.getTerminationGracePeriodSeconds().intValue())
                .resources(mainContainer.getResources())
                .tolerations(statefulSetSpec.getTemplate().getSpec().getTolerations())
                .imagePullSecrets(statefulSetSpec.getTemplate().getSpec().getImagePullSecrets())
                .service(createServiceConfig(service))
                .updateStrategy(statefulSetSpec.getUpdateStrategy())
                .podManagementPolicy(statefulSetSpec.getPodManagementPolicy())
                .pdb(createPodDisruptionBudgetConfig(pdb))
                .logsVolume(logsVolumeConfig)
                .runtime(detectRuntime())
                .rbac(FunctionsWorkerSpec.RbacConfig.builder()
                        .create(false)
                        .build())
                .antiAffinity(createAntiAffinityConfig(spec))
                .env(getEnv(mainContainer, FunctionsWorkerResourcesFactory.DEFAULT_ENV))
                .initContainers(spec.getInitContainers())
                .sidecars(getSidecars(spec, FunctionsWorkerResourcesFactory.getContainerNames(resourceName)))
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

    private TlsConfig.FunctionsWorkerTlsEntryConfig createTlsEntryConfig(StatefulSet sts) {
        boolean tlsEnabled = parseConfigValueBool(getConfig(), "useTls");
        if (!tlsEnabled) {
            return null;
        }
        final Volume certs = sts.getSpec().getTemplate().getSpec().getVolumes().stream()
                .filter(v -> "certs".equals(v.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tls volume with name 'certs' found"));
        final String secretName = certs.getSecret().getSecretName();

        boolean enabledWithBroker = ((String) getConfig().get("pulsarServiceUrl")).startsWith("pulsar+ssl://");

        return TlsConfig.FunctionsWorkerTlsEntryConfig.functionsWorkerBuilder()
                .enabled(true)
                .enabledWithBroker(enabledWithBroker)
                .secretName(secretName)
                .build();
    }

    private FunctionsWorkerSpec.ServiceConfig createServiceConfig(Service service) {
        Map<String, String> annotations = service.getMetadata().getAnnotations();
        if (annotations == null) {
            annotations = new HashMap<>();
        }
        return FunctionsWorkerSpec.ServiceConfig.builder()
                .annotations(annotations)
                .additionalPorts(removeServicePorts(service.getSpec().getPorts(),
                        FunctionsWorkerResourcesFactory.DEFAULT_HTTP_PORT,
                        FunctionsWorkerResourcesFactory.DEFAULT_HTTPS_PORT
                ))
                .type(service.getSpec().getType())
                .build();
    }


    private String detectRuntime() {
        final Map<String, Object> config = getConfig();


        final String functionRuntimeFactoryClassName = (String) config.get("functionRuntimeFactoryClassName");
        if (functionRuntimeFactoryClassName != null) {
            switch (functionRuntimeFactoryClassName) {
                case "org.apache.pulsar.functions.runtime.process.ProcessRuntimeFactory":
                    return "process";
                case "org.apache.pulsar.functions.runtime.kubernetes.KubernetesRuntimeFactory":
                    return "kubernetes";
                default:
                    throw new IllegalArgumentException(
                            "unable to detect functions worker runtime mode, functionRuntimeFactoryClassName="
                                    + functionRuntimeFactoryClassName);
            }
        }
        if (config.containsKey("processContainerFactory")) {
            return "process";
        }
        if (config.containsKey("kubernetesContainerFactory")) {
            return "kubernetes";
        }
        throw new IllegalArgumentException("unable to detect functions worker runtime mode");
    }

}
