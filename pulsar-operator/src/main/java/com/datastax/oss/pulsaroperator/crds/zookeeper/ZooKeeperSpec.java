package com.datastax.oss.pulsaroperator.crds.zookeeper;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
            .timeout(60)
            .build();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProbeConfig {
        @JsonPropertyDescription("Indicates whether the probe is enabled or not.")
        private Boolean enabled;
        @JsonPropertyDescription("Indicates the timeout (in seconds) for the probe.")
        private Integer timeout;
        @JsonPropertyDescription("Indicates the initial delay (in seconds) for the probe.")
        private Integer initial;
        @JsonPropertyDescription("Indicates the period (in seconds) for the probe.")
        private Integer period;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VolumeConfig {
        @JsonPropertyDescription("Indicates the suffix for the volume. Default value is 'data'.")
        private String name;
        @JsonPropertyDescription("Indicates the requested size for the volume. The format follows the Kubernetes' "
                + "Quantity. Default value is '5Gi'.")
        private String size;
        @JsonPropertyDescription("Indicates if a StorageClass is used. The operator will create the StorageClass if "
                + "needed.")
        private StorageClassConfig storageClass;
        @JsonPropertyDescription("Indicates if an already existing storage class should be used.")
        private String existingStorageClassName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServiceConfig {
        @JsonPropertyDescription("Additional annotations to add to the ZooKeeper Service resources.")
        private Map<String, String> annotations;
        @JsonPropertyDescription("Additional ports for the ZooKeeper Service resources.")
        private List<ServicePort> additionalPorts;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetadataInitializationJobConfig {
        @JsonPropertyDescription("Resource requirements for the Job's Pod.")
        private ResourceRequirements resources;
        @JsonPropertyDescription("Timeout (in seconds) for the metadata initialization execution. Default value is 60.")
        private int timeout;
    }

    @JsonPropertyDescription("Base name of the ZooKeeper component. Default is 'zookeeper'.")
    private String component;
    @Min(1)
    @io.fabric8.generator.annotation.Min(1)
    @JsonPropertyDescription("Replicas of ZooKeeper instances.")
    private Integer replicas;
    @JsonPropertyDescription("Configuration entries directly passed to the ZooKeeper server.")
    private Map<String, String> config;
    @JsonPropertyDescription("Pod management policy for the ZooKeeper pod. Default value is 'Parallel'.")
    private String podManagementPolicy;
    @JsonPropertyDescription("Update strategy for the ZooKeeper pod. Default value is rolling update.")
    private StatefulSetUpdateStrategy updateStrategy;
    @JsonPropertyDescription("Annotations to add to each ZooKeeper resource.")
    private Map<String, String> annotations;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription("Termination grace period in seconds for the ZooKeeper pod. Default value is 60.")
    private Integer gracePeriod;
    @JsonPropertyDescription("Resource requirements for the ZooKeeper pod.")
    private ResourceRequirements resources;
    @JsonPropertyDescription("Liveness and readiness probe values.")
    private ProbeConfig probe;
    @JsonPropertyDescription("Volume configuration for ZooKeeper data.")
    private VolumeConfig dataVolume;
    @JsonPropertyDescription("Configurations for the Service resources associated to the ZooKeeper pod.")
    private ServiceConfig service;
    @JsonPropertyDescription("Pod disruption budget configuration for the ZooKeeper pod.")
    private PodDisruptionBudgetConfig pdb;
    @JsonPropertyDescription("Configuration about the job that initializes the Pulsar cluster creating the needed "
            + "ZooKeeper nodes.")
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
            metadataInitializationJob.setTimeout(Objects.requireNonNullElse(
                    metadataInitializationJob.getTimeout(),
                    DEFAULT_METADATA_INITIALIZATION_JOB_CONFIG.getTimeout()));
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
