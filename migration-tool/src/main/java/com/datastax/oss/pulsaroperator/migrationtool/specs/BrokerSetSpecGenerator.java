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

import com.datastax.oss.pulsaroperator.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.tls.TlsConfig;
import com.datastax.oss.pulsaroperator.migrationtool.InputClusterSpecs;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BrokerSetSpecGenerator extends BaseSpecGenerator<BrokerSetSpec> {

    public static final String SPEC_NAME = "Broker";
    private final String resourceName;
    private BrokerSpec generatedSpec;
    private PodDNSConfig podDNSConfig;
    private boolean isRestartOnConfigMapChange;
    private String priorityClassName;
    private Map<String, Object> config;
    private TlsConfig.TlsEntryConfig tlsEntryConfig;

    public BrokerSetSpecGenerator(InputClusterSpecs inputSpecs,
                                  InputClusterSpecs.BrokerSpecs.AdditionalBroker additionalBroker,
                                  KubernetesClient client) {
        super(inputSpecs, client);
        final String clusterName = inputSpecs.getClusterName();
        resourceName = BrokerResourcesFactory.getResourceName(clusterName,
                inputSpecs.getBroker().getBaseName(),
                additionalBroker.getName(),
                additionalBroker.getOverrideName());
        internalGenerateSpec();

    }

    @Override
    public String getSpecName() {
        return SPEC_NAME;
    }

    @Override
    public BrokerSpec generateSpec() {
        return generatedSpec;
    }

    public void internalGenerateSpec() {
        final PodDisruptionBudget podDisruptionBudget = getPodDisruptionBudget(resourceName);
        final ConfigMap configMap = requireConfigMap(resourceName);
        final Service mainService = requireService(resourceName);
        final StatefulSet statefulSet = requireStatefulSet(resourceName);
        verifyLabelsEquals(podDisruptionBudget, statefulSet, configMap);


        final StatefulSetSpec statefulSetSpec = statefulSet.getSpec();
        final PodSpec spec = statefulSetSpec.getTemplate()
                .getSpec();
        final Container container = spec
                .getContainers()
                .get(0);
        PodDisruptionBudgetConfig podDisruptionBudgetConfig =
                createPodDisruptionBudgetConfig(podDisruptionBudget);


        config = convertConfigMapData(configMap);
        container.getEnv().forEach(envVar -> {
            config.put(envVar.getName().replace("PULSAR_PREFIX_", ""), envVar.getValue());
        });


        podDNSConfig = spec.getDnsConfig();
        isRestartOnConfigMapChange = isPodDependantOnConfigMap(statefulSetSpec.getTemplate());
        priorityClassName = spec.getPriorityClassName();
        tlsEntryConfig = createTlsEntryConfig(statefulSet);
        final NodeAffinity nodeAffinity = spec.getAffinity() == null ? null : spec.getAffinity().getNodeAffinity();
        final Map<String, String> matchLabels = getMatchLabels(statefulSetSpec);


        generatedSpec = BrokerSpec.builder()
                .annotations(statefulSet.getMetadata().getAnnotations())
                .podAnnotations(statefulSetSpec.getTemplate().getMetadata().getAnnotations())
                .image(container.getImage())
                .imagePullPolicy(container.getImagePullPolicy())
                .nodeSelectors(spec.getNodeSelector())
                .replicas(statefulSetSpec.getReplicas())
                .probes(createBrokerProbeConfig(container))
                .nodeAffinity(nodeAffinity)
                .pdb(podDisruptionBudgetConfig)
                .labels(statefulSet.getMetadata().getLabels())
                .podLabels(statefulSetSpec.getTemplate().getMetadata().getLabels())
                .matchLabels(matchLabels)
                .config(config)
                .podManagementPolicy(statefulSetSpec.getPodManagementPolicy())
                .updateStrategy(statefulSetSpec.getUpdateStrategy())
                .gracePeriod(spec.getTerminationGracePeriodSeconds() == null ? null :
                        spec.getTerminationGracePeriodSeconds().intValue())
                .resources(container.getResources())
                .tolerations(spec.getTolerations())
                .service(createServiceConfig(mainService))
                .imagePullSecrets(statefulSetSpec.getTemplate().getSpec().getImagePullSecrets())
                .functionsWorkerEnabled(isFunctionsWorkerEnabled(config))
                .transactions(getTransactionCoordinatorConfig(config))
                .serviceAccountName(spec.getServiceAccountName())
                .antiAffinity(createAntiAffinityConfig(spec))
                .env(container.getEnv())
                .initContainers(spec.getInitContainers())
                .sidecars(getSidecars(spec, BrokerResourcesFactory.getContainerNames(resourceName)))
                .build();
    }


    private BrokerSetSpec.TransactionCoordinatorConfig getTransactionCoordinatorConfig(Map<String, Object> configMapData) {
        final boolean transactionCoordinatorEnabled = boolConfigMapValue(configMapData, "functionsWorkerEnabled");
        return BrokerSetSpec.TransactionCoordinatorConfig.builder()
                .enabled(transactionCoordinatorEnabled)
                .build();
    }


    private boolean isFunctionsWorkerEnabled(Map<String, Object> configMapData) {
        return boolConfigMapValue(configMapData, "functionsWorkerEnabled");
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


    private BrokerSetSpec.ServiceConfig createServiceConfig(Service service) {
        Map<String, String> annotations = service.getMetadata().getAnnotations();
        if (annotations == null) {
            annotations = new HashMap<>();
        }
        return BrokerSetSpec.ServiceConfig.builder()
                .annotations(annotations)
                .additionalPorts(removeServicePorts(service.getSpec().getPorts(),
                        BrokerResourcesFactory.DEFAULT_HTTP_PORT,
                        BrokerResourcesFactory.DEFAULT_HTTPS_PORT,
                        BrokerResourcesFactory.DEFAULT_PULSAR_PORT,
                        BrokerResourcesFactory.DEFAULT_PULSARSSL_PORT
                ))
                .type(service.getSpec().getType())
                .build();
    }


    private TlsConfig.TlsEntryConfig createTlsEntryConfig(StatefulSet statefulSet) {
        boolean tlsEnabled = parseConfigValueBool(getConfig(), "tlsEnabled");
        if (!tlsEnabled) {
            return null;
        }
        final Volume certs = statefulSet.getSpec().getTemplate().getSpec().getVolumes().stream()
                .filter(v -> "certs".equals(v.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tls volume with name 'certs' found"));
        final String secretName = certs.getSecret().getSecretName();

        return TlsConfig.TlsEntryConfig.builder()
                .enabled(true)
                .secretName(secretName)
                .build();
    }

    protected static BrokerSetSpec.BrokerProbesConfig createBrokerProbeConfig(Container container) {
        return BrokerSetSpec.BrokerProbesConfig.brokerProbeConfigBuilder()
                .liveness(createProbeConfig(container.getLivenessProbe()))
                .readiness(createProbeConfig(container.getReadinessProbe()))
                .useHealthCheckForLiveness(detectedProbeUsingHealthCheck(container.getLivenessProbe()))
                .useHealthCheckForReadiness(detectedProbeUsingHealthCheck(container.getReadinessProbe()))
                .build();
    }

    private static boolean detectedProbeUsingHealthCheck(Probe probe) {
        if (probe.getHttpGet() != null) {
            return probe.getHttpGet().getPath().contains("/brokers/health");
        }
        return true;
    }

}
