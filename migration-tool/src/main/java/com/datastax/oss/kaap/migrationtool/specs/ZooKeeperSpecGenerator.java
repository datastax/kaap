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

import com.datastax.oss.kaap.controllers.zookeeper.ZooKeeperResourcesFactory;
import com.datastax.oss.kaap.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeperSpec;
import com.datastax.oss.kaap.migrationtool.InputClusterSpecs;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZooKeeperSpecGenerator extends BaseSpecGenerator<ZooKeeperSpec> {

    public static final String SPEC_NAME = "ZooKeeper";
    private final String resourceName;
    private ZooKeeperSpec generatedSpec;
    private PodDNSConfig podDNSConfig;
    private boolean isRestartOnConfigMapChange;
    private String priorityClassName;
    private Map<String, Object> config;
    private TlsConfig.TlsEntryConfig tlsEntryConfig;

    public ZooKeeperSpecGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        super(inputSpecs, client);
        final String clusterName = inputSpecs.getClusterName();
        final String zkBaseName = inputSpecs.getZookeeper().getBaseName();
        resourceName = ZooKeeperResourcesFactory.getResourceName(clusterName, zkBaseName);
        internalGenerateSpec();

    }

    @Override
    public String getSpecName() {
        return SPEC_NAME;
    }

    @Override
    public ZooKeeperSpec generateSpec() {
        return generatedSpec;
    }

    @Override
    public boolean isEnabled() {
        return generatedSpec.getReplicas() > 0;
    }

    public void internalGenerateSpec() {
        final PodDisruptionBudget podDisruptionBudget = getPodDisruptionBudget(resourceName);
        final ConfigMap configMap = requireConfigMap(resourceName);
        final Service mainService = requireService(resourceName);
        final Service caService = requireService(resourceName + "-ca");
        final StatefulSet statefulSet = requireStatefulSet(resourceName);

        verifyLabelsEquals(mainService, caService);
        verifyLabelsEquals(podDisruptionBudget, statefulSet, configMap);


        final StatefulSetSpec statefulSetSpec = statefulSet.getSpec();
        final PodSpec spec = statefulSetSpec.getTemplate()
                .getSpec();
        final Container container = spec
                .getContainers()
                .get(0);
        verifyProbeCompatible(container.getReadinessProbe());
        verifyProbeCompatible(container.getLivenessProbe());
        PodDisruptionBudgetConfig podDisruptionBudgetConfig =
                createPodDisruptionBudgetConfig(podDisruptionBudget);


        final PersistentVolumeClaim persistentVolumeClaim = statefulSetSpec.getVolumeClaimTemplates()
                .get(0);

        config = convertConfigMapData(configMap);
        podDNSConfig = spec.getDnsConfig();
        isRestartOnConfigMapChange = isPodDependantOnConfigMap(statefulSetSpec.getTemplate());
        priorityClassName = spec.getPriorityClassName();
        tlsEntryConfig = createTlsEntryConfig(statefulSet);

        final NodeAffinity nodeAffinity = spec.getAffinity() == null ? null : spec.getAffinity().getNodeAffinity();
        final Map<String, String> matchLabels = getMatchLabels(statefulSetSpec);
        generatedSpec = ZooKeeperSpec.builder()
                .annotations(cleanupAnnotations(statefulSet.getMetadata().getAnnotations()))
                .podAnnotations(cleanupAnnotations(statefulSetSpec.getTemplate().getMetadata().getAnnotations()))
                .image(container.getImage())
                .imagePullPolicy(container.getImagePullPolicy())
                .nodeSelectors(spec.getNodeSelector())
                .replicas(statefulSetSpec.getReplicas())
                .probes(createProbeConfig(container))
                .nodeAffinity(nodeAffinity)
                .pdb(podDisruptionBudgetConfig)
                .labels(statefulSet.getMetadata().getLabels())
                .skipVolumeClaimLabels(true)
                .podLabels(statefulSetSpec.getTemplate().getMetadata().getLabels())
                .matchLabels(matchLabels)
                .config(config)
                .podManagementPolicy(statefulSetSpec.getPodManagementPolicy())
                .updateStrategy(statefulSetSpec.getUpdateStrategy())
                .gracePeriod(spec.getTerminationGracePeriodSeconds() == null ? null :
                        spec.getTerminationGracePeriodSeconds().intValue())
                .resources(container.getResources())
                .tolerations(spec.getTolerations())
                .dataVolume(createVolumeConfig(resourceName, persistentVolumeClaim))
                .service(createServiceConfig(mainService))
                .imagePullSecrets(statefulSetSpec.getTemplate().getSpec().getImagePullSecrets())
                .antiAffinity(createAntiAffinityConfig(spec))
                .env(getEnv(container, ZooKeeperResourcesFactory.DEFAULT_ENV))
                .initContainers(spec.getInitContainers())
                .sidecars(getSidecars(spec, ZooKeeperResourcesFactory.getContainerNames(resourceName)))
                .build();
    }




    private TlsConfig.TlsEntryConfig createTlsEntryConfig(StatefulSet statefulSet) {
        final String serverCnxnFactory = (String) getConfig().get("serverCnxnFactory");
        if (!"org.apache.zookeeper.server.NettyServerCnxnFactory".equals(serverCnxnFactory)) {
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
        return null;
    }

    @Override
    public String getAuthPublicKeyFile() {
        return null;
    }

    private ZooKeeperSpec.ServiceConfig createServiceConfig(Service mainService) {
        Map<String, String> annotations = mainService.getMetadata().getAnnotations();
        if (annotations == null) {
            annotations = new HashMap<>();
        }
        annotations.remove("service.alpha.kubernetes.io/tolerate-unready-endpoints");
        return ZooKeeperSpec.ServiceConfig.builder()
                .annotations(cleanupAnnotations(annotations))
                .additionalPorts(
                        removeServicePorts(mainService.getSpec().getPorts(),
                                ZooKeeperResourcesFactory.DEFAULT_CLIENT_PORT,
                                ZooKeeperResourcesFactory.DEFAULT_SERVER_PORT,
                                ZooKeeperResourcesFactory.DEFAULT_CLIENT_TLS_PORT,
                                ZooKeeperResourcesFactory.DEFAULT_LEADER_ELECTION_PORT
                        )
                )
                .build();
    }

    private void verifyProbeCompatible(Probe probe) {
        if (probe.getExec() == null) {
            throw new IllegalStateException("current probe is not compatible, must be exec");
        }
    }
}
