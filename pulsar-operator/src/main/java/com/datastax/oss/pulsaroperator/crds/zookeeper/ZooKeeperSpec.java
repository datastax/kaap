package com.datastax.oss.pulsaroperator.crds.zookeeper;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategy;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategyBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ZooKeeperSpec extends BaseComponentSpec<ZooKeeperSpec> {

    private static final StatefulSetUpdateStrategy DEFAULT_UPDATE_STRATEGY = new StatefulSetUpdateStrategyBuilder()
            .withType("RollingUpdate")
            .build();

    public static final ProbeConfig DEFAULT_PROBE = ProbeConfig.builder()
            .enabled(true)
            .initial(20)
            .period(30)
            .timeout(30)
            .build();


    private static final ResourceRequirements DEFAULT_RESOURCE_REQUIREMENTS = new ResourceRequirementsBuilder()
            .withRequests(Map.of("memory", Quantity.parse("1Gi"), "cpu", Quantity.parse("0.3")))
            .build();
    public static final VolumeConfig DEFAULT_DATA_VOLUME = new VolumeConfig.VolumeConfigBuilder()
            .name("data")
            .size("5Gi").build();
    public static final PodDisruptionBudgetConfig DEFAULT_PDB = PodDisruptionBudgetConfig.builder()
            .enabled(true)
            .maxUnavailable(1)
            .build();
    public static final MetadataInitializationJobConfig
            DEFAULT_METADATA_INITIALIZATION_JOB_CONFIG = MetadataInitializationJobConfig.builder()
            .initTimeout(60)
            .build();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProbeConfig {
        private Boolean enabled;
        private Integer timeout;
        private Integer initial;
        private Integer period;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VolumeConfig {
        private String name;
        private String size;
        private StorageClassConfig storageClass;
        private String existingStorageClassName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServiceConfig {
        private Map<String, String> annotations;
        private List<ServicePort> additionalPorts;

    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetadataInitializationJobConfig {
        private ResourceRequirements resources;
        private int initTimeout;
    }

    private String component;
    @Min(1)
    private Integer replicas;
    private Map<String, String> config;
    private String podManagementPolicy;
    private StatefulSetUpdateStrategy updateStrategy;
    private Map<String, String> annotations;
    @Min(0)
    private Integer gracePeriod;
    private ResourceRequirements resources;
    private ProbeConfig probe;
    private VolumeConfig dataVolume;
    private ServiceConfig service;
    private PodDisruptionBudgetConfig pdb;
    private MetadataInitializationJobConfig metadataInitializationJob;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        super.applyDefaults(globalSpec);
        applyProbeDefault();
        if (podManagementPolicy == null) {
            podManagementPolicy = "Parallel";
        }
        if (replicas == null) {
            replicas = 3;
        }
        if (component == null) {
            component = "zookeeper";
        }
        if (updateStrategy == null) {
            updateStrategy = DEFAULT_UPDATE_STRATEGY;
        }
        if (gracePeriod == null) {
            gracePeriod = 60;
        }
        if (resources == null) {
            resources = DEFAULT_RESOURCE_REQUIREMENTS;
        }
        if (dataVolume == null) {
            dataVolume = DEFAULT_DATA_VOLUME;
        }
        if (globalSpec.getStorage() != null) {
            if (dataVolume.getExistingStorageClassName() == null && dataVolume.getStorageClass() == null) {
                if (globalSpec.getStorage().getExistingStorageClassName() != null) {
                    dataVolume.setExistingStorageClassName(globalSpec.getStorage().getExistingStorageClassName());
                } else if (globalSpec.getStorage().getStorageClass() != null) {
                    dataVolume.setStorageClass(globalSpec.getStorage().getStorageClass());
                }
            }
        }
        if (pdb != null) {
            pdb.setEnabled(Objects.requireNonNullElse(pdb.getEnabled(), DEFAULT_PDB.getEnabled()));
            pdb.setMaxUnavailable(Objects.requireNonNullElse(pdb.getMaxUnavailable(),
                    DEFAULT_PDB.getMaxUnavailable()));
        } else {
            pdb = DEFAULT_PDB;
        }
        if (metadataInitializationJob != null) {
            metadataInitializationJob.setInitTimeout(Objects.requireNonNullElse(
                    metadataInitializationJob.getInitTimeout(),
                    DEFAULT_METADATA_INITIALIZATION_JOB_CONFIG.getInitTimeout()));
            metadataInitializationJob.setResources(Objects.requireNonNullElse(
                    metadataInitializationJob.getResources(),
                    DEFAULT_METADATA_INITIALIZATION_JOB_CONFIG.getResources()));
        } else {
            metadataInitializationJob = DEFAULT_METADATA_INITIALIZATION_JOB_CONFIG;
        }
    }

    private void applyProbeDefault() {
        if (probe == null) {
            probe = ZooKeeperSpec.DEFAULT_PROBE;
        } else {
            boolean enabled = probe.getEnabled() == null
                    ? ZooKeeperSpec.DEFAULT_PROBE.getEnabled() : probe.getEnabled();
            if (!enabled) {
                probe = null;
            } else {
                probe = ProbeConfig.builder()
                        .initial(Objects.requireNonNullElse(probe.getInitial(),
                                ZooKeeperSpec.DEFAULT_PROBE.getInitial()))
                        .period(Objects.requireNonNullElse(probe.getPeriod(),
                                ZooKeeperSpec.DEFAULT_PROBE.getPeriod()))
                        .timeout(Objects.requireNonNullElse(probe.getTimeout(),
                                ZooKeeperSpec.DEFAULT_PROBE.getTimeout()))
                        .build();
            }
        }
    }

    @Override
    public boolean isValid(ZooKeeperSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
