package com.datastax.oss.pulsaroperator.migrationtool;

import com.datastax.oss.pulsaroperator.controllers.zookeeper.ZooKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZooKeeperSpecGenerator extends BaseSpecGenerator<ZooKeeperSpec> {

    private final String resourceName;
    private List<HasMetadata> resources = new ArrayList<>();
    private ZooKeeperSpec generatedSpec;

    public ZooKeeperSpecGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        super(inputSpecs, client);
        final String clusterName = inputSpecs.getClusterName();
        final String zkBaseName = inputSpecs.getZookeeper().getBaseName();
        resourceName = ZooKeeperResourcesFactory.getResourceName(clusterName, zkBaseName);
        internalGenerateSpec();

    }

    @Override
    public List<HasMetadata> getAllResources() {
        return resources;
    }


    @Override
    public ZooKeeperSpec generateSpec() {
        return generatedSpec;
    }

    public void internalGenerateSpec() {
        log.info("looking for existing zookeeper with name {}", resourceName);
        final PodDisruptionBudget podDisruptionBudget = getPodDisruptionBudget(resourceName);
        final ConfigMap configMap = requireConfigMap(resourceName);
        final Service mainService = requireService(resourceName);
        final Service caService = requireService(resourceName + "-ca");
        final StatefulSet statefulSet = requireStatefulSet(resourceName);
        resources.add(podDisruptionBudget);
        resources.add(configMap);
        resources.add(mainService);
        resources.add(statefulSet);
        resources.add(caService);

        log.info("found existing zookeeper resources");
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
        verifyProbesSameValues(container.getReadinessProbe(), container.getLivenessProbe());
        final ProbeConfig readinessProbeConfig = createProbeConfig(container);
        PodDisruptionBudgetConfig podDisruptionBudgetConfig =
                createPodDisruptionBudgetConfig(podDisruptionBudget);


        Map<String, Object> configMapData = new HashMap<>(configMap.getData());

        final PersistentVolumeClaim persistentVolumeClaim = statefulSetSpec.getVolumeClaimTemplates()
                .get(0);

        generatedSpec = ZooKeeperSpec.builder()
                .annotations(addHelmPolicyAnnotation(statefulSet.getMetadata().getAnnotations()))
                .podAnnotations(statefulSetSpec.getTemplate().getMetadata().getAnnotations())
                .image(container.getImage())
                .imagePullPolicy(container.getImagePullPolicy())
                .nodeSelectors(spec.getNodeSelector())
                .replicas(statefulSetSpec.getReplicas())
                .probe(readinessProbeConfig)
                .pdb(podDisruptionBudgetConfig)
                .labels(statefulSet.getMetadata().getLabels())
                .podLabels(statefulSetSpec.getTemplate().getMetadata().getLabels())
                .config(configMapData)
                .podManagementPolicy(statefulSetSpec.getPodManagementPolicy())
                .updateStrategy(statefulSetSpec.getUpdateStrategy())
                .gracePeriod(spec.getTerminationGracePeriodSeconds() == null ? null :
                        spec.getTerminationGracePeriodSeconds().intValue())
                .resources(container.getResources())
                .dataVolume(createDataVolumeConfig(resourceName, persistentVolumeClaim))
                .service(createServiceConfig(mainService))
                .imagePullSecrets(statefulSetSpec.getTemplate().getSpec().getImagePullSecrets())
                .build();
    }

    @Override
    public PodDNSConfig getPodDnsConfig() {
        return getStatefulSet(resourceName).getSpec().getTemplate().getSpec().getDnsConfig();
    }

    private ZooKeeperSpec.ServiceConfig createServiceConfig(Service mainService) {
        return ZooKeeperSpec.ServiceConfig.builder()
                .annotations(addHelmPolicyAnnotation(mainService.getMetadata().getAnnotations()))
                .build();
    }

    private VolumeConfig createDataVolumeConfig(String resourceName, PersistentVolumeClaim persistentVolumeClaim) {
        return VolumeConfig.builder()
                .name(replaceResourceNamePrefix(persistentVolumeClaim
                        .getMetadata().getName(), resourceName))
                .size(persistentVolumeClaim
                        .getSpec().getResources().getRequests().get("storage").toString())
                .existingStorageClassName(persistentVolumeClaim
                        .getSpec().getStorageClassName())
                .build();
    }

    private PodDisruptionBudgetConfig createPodDisruptionBudgetConfig(PodDisruptionBudget podDisruptionBudget) {
        PodDisruptionBudgetConfig podDisruptionBudgetConfig = null;
        if (podDisruptionBudget != null) {
            podDisruptionBudgetConfig = PodDisruptionBudgetConfig.builder()
                    .enabled(true)
                    .maxUnavailable(podDisruptionBudget.getSpec().getMaxUnavailable().getIntVal())
                    .build();
        }
        return podDisruptionBudgetConfig;
    }

    private ProbeConfig createProbeConfig(Container container) {
        final Integer timeout = container.getReadinessProbe().getTimeoutSeconds();
        final Integer period = container.getReadinessProbe().getPeriodSeconds();
        final Integer initial = container.getReadinessProbe().getInitialDelaySeconds();

        final ProbeConfig readinessProbeConfig = ProbeConfig.builder()
                .enabled(true)
                .initial(initial)
                .period(period)
                .timeout(timeout)
                .build();
        return readinessProbeConfig;
    }

    private void verifyProbeCompatible(Probe probe) {
        if (probe.getExec() == null) {
            throw new IllegalStateException("current probe is not compatible, must be exec");
        }
    }

    private void verifyProbesSameValues(Probe probe1, Probe probe2) {
        if (!Objects.equals(probe1, probe2)) {
            throw new IllegalStateException(
                    "probes are not equals, the operator handles a single configuration for readiness and liveness "
                            + "probe");
        }
    }

    private String replaceResourceNamePrefix(String str, String resourceName) {
        return str.replaceFirst(resourceName + "-", "");
    }

    private void verifyLabelsEquals(HasMetadata... resources) {
        for (int i = 0; i < resources.length; i++) {
            if (!Objects.equals(resources[0].getMetadata().getLabels(), resources[i].getMetadata().getLabels())) {
                throw new IllegalStateException("labels are not equals");
            }
        }
    }
}
