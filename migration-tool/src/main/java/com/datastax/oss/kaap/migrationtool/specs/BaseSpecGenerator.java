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

import com.datastax.oss.kaap.controllers.AffinityRulesCustomizer;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.configs.AntiAffinityConfig;
import com.datastax.oss.kaap.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.kaap.crds.configs.ProbesConfig;
import com.datastax.oss.kaap.crds.configs.VolumeConfig;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.migrationtool.InputClusterSpecs;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodAffinityTerm;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class BaseSpecGenerator<T> {

    public static final List<String> HELM_LABELS = List.of(
            "app.kubernetes.io/managed-by",
            "chart",
            "heritage",
            "release"
    );
    protected final InputClusterSpecs inputSpecs;
    protected final KubernetesClient client;


    public BaseSpecGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        this.inputSpecs = inputSpecs;
        this.client = client;
    }

    public abstract String getSpecName();

    public abstract T generateSpec();

    public abstract boolean isEnabled();

    public abstract PodDNSConfig getPodDnsConfig();

    public abstract boolean isRestartOnConfigMapChange();

    public abstract String getDnsName();

    public abstract String getPriorityClassName();

    public abstract Map<String, Object> getConfig();

    public abstract TlsConfig.TlsEntryConfig getTlsEntryConfig();

    public abstract String getTlsCaPath();

    public abstract String getAuthPublicKeyFile();

    protected PodDisruptionBudget getPodDisruptionBudget(String name) {
        return client.policy()
                .v1()
                .podDisruptionBudget()
                .inNamespace(inputSpecs.getNamespace())
                .withName(name)
                .get();
    }

    protected ConfigMap getConfigMap(String name) {
        return getConfigMap(name, false);
    }

    protected ConfigMap requireConfigMap(String name) {
        return getConfigMap(name, true);
    }

    private ConfigMap getConfigMap(String name, boolean required) {
        final ConfigMap configMap = client.configMaps()
                .inNamespace(inputSpecs.getNamespace())
                .withName(name)
                .get();
        if (required && configMap == null) {
            throw new IllegalStateException("Expected configMap with name " + name + " not found");
        }
        return configMap;
    }


    protected Service getService(String name) {
        return getService(name, false);
    }

    protected Service requireService(String name) {
        return getService(name, true);
    }

    private Service getService(String name, boolean required) {
        final Service service = client.services()
                .inNamespace(inputSpecs.getNamespace())
                .withName(name)
                .get();
        if (required && service == null) {
            throw new IllegalStateException("Expected service with name " + name + " not found");
        }
        return service;
    }

    protected StatefulSet getStatefulSet(String name) {
        return getStatefulSet(name, false);
    }

    protected StatefulSet requireStatefulSet(String name) {
        return getStatefulSet(name, true);
    }

    private StatefulSet getStatefulSet(String name, boolean required) {
        final StatefulSet sts = client.apps()
                .statefulSets()
                .inNamespace(inputSpecs.getNamespace())
                .withName(name)
                .get();
        if (required && sts == null) {
            throw new IllegalStateException("Expected StatefulSet with name " + name + " not found");
        }
        return sts;
    }

    protected Deployment requireDeployment(String name) {
        return getDeployment(name, true);
    }

    private Deployment getDeployment(String name, boolean required) {
        final Deployment sts = client.apps()
                .deployments()
                .inNamespace(inputSpecs.getNamespace())
                .withName(name)
                .get();
        if (required && sts == null) {
            throw new IllegalStateException("Expected Deployment with name " + name + " not found");
        }
        return sts;
    }

    protected static VolumeConfig createVolumeConfig(String resourceName, PersistentVolumeClaim persistentVolumeClaim) {
        return VolumeConfig.builder()
                .name(replaceResourceNamePrefix(persistentVolumeClaim
                        .getMetadata().getName(), resourceName))
                .size(persistentVolumeClaim
                        .getSpec().getResources().getRequests().get("storage").toString())
                .existingStorageClassName(persistentVolumeClaim
                        .getSpec().getStorageClassName())
                .build();
    }

    protected static String replaceResourceNamePrefix(String str, String resourceName) {
        return str.replaceFirst(resourceName + "-", "");
    }

    protected static PersistentVolumeClaim requirePvc(String pvcFullName, List<PersistentVolumeClaim> claims) {
        if (claims == null) {
            throw new IllegalArgumentException(
                    "Expected PersistentVolumeClaim with name " + pvcFullName + " but none found");
        }
        for (PersistentVolumeClaim claim : claims) {
            if (claim.getMetadata().getName().equals(pvcFullName)) {
                return claim;
            }
        }
        throw new IllegalArgumentException("Expected PersistentVolumeClaim with name " + pvcFullName + ", only "
                + claims.stream().map(c -> c.getMetadata().getName()).collect(Collectors.toList()));
    }

    protected static List<ServicePort> removeServicePorts(List<ServicePort> ports, int... portNumbers) {
        if (ports == null) {
            return null;
        }
        return ports.stream()
                .filter(port -> {
                    for (int portNumber : portNumbers) {
                        if (port.getPort() == portNumber) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    protected static AntiAffinityConfig createAntiAffinityConfig(PodSpec podSpec) {
        final AntiAffinityConfig.AntiAffinityConfigBuilder builder = AntiAffinityConfig.builder()
                .host(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                        .enabled(false)
                        .build())
                .zone(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                        .enabled(false)
                        .build());

        final Affinity affinity = podSpec.getAffinity();
        if (affinity == null || affinity.getPodAntiAffinity() == null) {
            return builder.build();
        }

        final PodAntiAffinity podAntiAffinity = affinity.getPodAntiAffinity();
        final List<PodAffinityTerm> required =
                podAntiAffinity.getRequiredDuringSchedulingIgnoredDuringExecution();
        final List<WeightedPodAffinityTerm> preferred =
                podAntiAffinity.getPreferredDuringSchedulingIgnoredDuringExecution();
        if (required != null) {
            for (PodAffinityTerm podAffinityTerm : required) {
                if (AffinityRulesCustomizer.TOPOLOGY_KEY_HOST.equals(podAffinityTerm.getTopologyKey())) {
                    builder.host(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                            .enabled(true)
                            .required(true)
                            .build());
                } else if (AffinityRulesCustomizer.TOPOLOGY_KEY_ZONE.equals(podAffinityTerm.getTopologyKey())) {
                    builder.zone(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                            .enabled(true)
                            .required(true)
                            .build());
                } else {
                    throw new IllegalArgumentException("Unsupported "
                            + "topology key in podAntiAffinity " + podAffinityTerm.getTopologyKey());
                }

            }
        }
        if (preferred != null) {
            for (WeightedPodAffinityTerm weightedPodAffinityTerm : preferred) {
                final String topologyKey = weightedPodAffinityTerm.getPodAffinityTerm().getTopologyKey();
                if (AffinityRulesCustomizer.TOPOLOGY_KEY_HOST.equals(topologyKey)) {
                    builder.host(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                            .enabled(true)
                            .required(false)
                            .build());
                } else if (AffinityRulesCustomizer.TOPOLOGY_KEY_ZONE.equals(topologyKey)) {
                    builder.zone(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                            .enabled(true)
                            .build());
                } else {
                    throw new IllegalArgumentException("Unsupported "
                            + "topology key in podAntiAffinity " + topologyKey);
                }
            }
        }
        return builder.build();
    }


    protected static PodDisruptionBudgetConfig createPodDisruptionBudgetConfig(
            PodDisruptionBudget podDisruptionBudget) {
        PodDisruptionBudgetConfig podDisruptionBudgetConfig = null;
        if (podDisruptionBudget != null) {
            podDisruptionBudgetConfig = PodDisruptionBudgetConfig.builder()
                    .enabled(true)
                    .build();
            if (podDisruptionBudget.getSpec().getMaxUnavailable() != null) {
                podDisruptionBudgetConfig.setMaxUnavailable(podDisruptionBudget.getSpec()
                        .getMaxUnavailable().getIntVal());
            }
            if (podDisruptionBudget.getSpec().getMinAvailable() != null) {
                podDisruptionBudgetConfig.setMinAvailable(podDisruptionBudget.getSpec()
                    .getMinAvailable().getIntVal());
            }
        }
        return podDisruptionBudgetConfig;
    }

    protected static Map<String, String> getMatchLabels(StatefulSetSpec statefulSetSpec) {
        return getMatchLabels(statefulSetSpec.getSelector());
    }

    protected static Map<String, String> getMatchLabels(LabelSelector selector) {

        Map<String, String> matchLabels = selector.getMatchLabels();
        if (matchLabels == null) {
            matchLabels = new HashMap<>();
        }
        if (!matchLabels.containsKey(CRDConstants.LABEL_CLUSTER)) {
            matchLabels.put(CRDConstants.LABEL_CLUSTER, "");
        }
        return matchLabels;
    }

    protected static void verifyLabelsEquals(HasMetadata... resources) {
        final List<HasMetadata> list =
                Arrays.stream(resources).filter(Objects::nonNull).collect(Collectors.toList());
        for (int i = 0; i < list.size(); i++) {
            if (!Objects.equals(list.get(0).getMetadata().getLabels(), list.get(i).getMetadata().getLabels())) {
                throw new IllegalStateException("labels are not equals");
            }
        }
    }

    protected static ProbesConfig createProbeConfig(Container container) {
        return ProbesConfig.builder()
                .liveness(createProbeConfig(container.getLivenessProbe()))
                .readiness(createProbeConfig(container.getReadinessProbe()))
                .build();
    }

    protected static ProbesConfig.ProbeConfig createProbeConfig(Probe containerProbe) {
        if (containerProbe == null) {
            return ProbesConfig.ProbeConfig.builder()
                    .enabled(false)
                    .build();
        }
        return ProbesConfig.ProbeConfig.builder()
                .enabled(true)
                .initialDelaySeconds(containerProbe.getInitialDelaySeconds())
                .periodSeconds(containerProbe.getPeriodSeconds())
                .timeoutSeconds(containerProbe.getTimeoutSeconds())
                .failureThreshold(containerProbe.getFailureThreshold())
                .successThreshold(containerProbe.getSuccessThreshold())
                .terminationGracePeriodSeconds(containerProbe.getTerminationGracePeriodSeconds())
                .build();
    }

    protected static boolean boolConfigMapValue(Map<String, ?> configMapData, String property) {
        return configMapData.containsKey(property)
                && Boolean.parseBoolean(configMapData.get(property).toString());
    }

    protected static boolean isPodDependantOnConfigMap(PodTemplateSpec podTemplate) {
        return podTemplate
                .getMetadata()
                .getAnnotations()
                .containsKey("checksum/config");
    }

    protected static boolean parseConfigValueBool(Map<String, Object> config, String key) {
        return Boolean.parseBoolean(String.valueOf(config.get(key)));
    }

    protected static Map<String, Object> convertConfigMapData(ConfigMap configMap) {
        Map<String, Object> res = new HashMap<>();
        configMap.getData().forEach((k, v) -> {
            res.put(k.replace(BaseResourcesFactory.CONFIG_PULSAR_PREFIX, ""), v);
        });
        return res;
    }

    protected static Container getContainerByName(List<Container> containers, String name) {
        for (Container container : containers) {
            if (container.getName().equals(name)) {
                return container;
            }
        }
        return null;
    }

    protected static String getPublicKeyFileFromFileURL(String url) {
        if (!url.startsWith("file://")) {
            throw new IllegalArgumentException("Invalid public key file url: " + url);
        }
        return url.substring(url.lastIndexOf("/") + 1);
    }

    protected List<EnvVar> getEnv(Container container, List<String> excludes) {
        final List<EnvVar> env = container.getEnv();
        if (env == null) {
            return null;
        }
        if (excludes == null) {
            return env;
        }
        return env.stream().filter(e -> !excludes.contains(e.getName()))
                .collect(Collectors.toList());
    }


    protected List<Container> getInitContainers(PodSpec podSpec, List<String> excludes) {
        final List<Container> initContainers = podSpec.getInitContainers();
        if (initContainers == null) {
            return null;
        }
        if (excludes == null) {
            return initContainers;
        }
        return initContainers.stream().filter(e -> !excludes.contains(e.getName()))
                .collect(Collectors.toList());
    }

    protected List<Container> getSidecars(PodSpec podSpec, List<String> excludes) {
        final List<Container> initContainers = podSpec.getContainers();
        if (initContainers == null) {
            return null;
        }
        if (excludes == null) {
            return initContainers;
        }
        return initContainers.stream().filter(e -> !excludes.contains(e.getName()))
                .collect(Collectors.toList());
    }


    public static Map<String, String> cleanupAnnotations(Map<String, ?> annotations) {
        if (annotations == null) {
            return Map.of();
        }
        return annotations.entrySet()
                        .stream()
                        .filter(e -> keepAnnotation(e))
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
    }

    private static boolean keepAnnotation(Map.Entry<String, ?> e) {
        final String key = e.getKey();
        if (key.equals("checksum/config")) {
            return false;
        }
        if (key.startsWith("kubectl.")) {
            return false;
        }
        if (key.startsWith("meta.helm.sh/")) {
            return false;
        }
        if (key.startsWith("deployment.kubernetes.io")) {
            return false;
        }
        return true;
    }

}
