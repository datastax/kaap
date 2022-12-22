package com.datastax.oss.pulsaroperator.crds.zookeeper;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategy;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategyBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.ObjectUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ZooKeeperSpec extends BaseComponentSpec<ZooKeeperSpec> {

    private static final Supplier<StatefulSetUpdateStrategy> DEFAULT_UPDATE_STRATEGY = () -> new StatefulSetUpdateStrategyBuilder()
            .withType("RollingUpdate")
            .build();

    public static final Supplier<ProbeConfig> DEFAULT_PROBE = () -> ProbeConfig.builder()
            .enabled(true)
            .initial(20)
            .period(30)
            .timeout(30)
            .build();


    private static final Supplier<ResourceRequirements> DEFAULT_RESOURCE_REQUIREMENTS = () -> new ResourceRequirementsBuilder()
            .withRequests(Map.of("memory", Quantity.parse("1Gi"), "cpu", Quantity.parse("0.3")))
            .build();

    public static final Supplier<VolumeConfig> DEFAULT_DATA_VOLUME = () -> VolumeConfig.builder()
            .name("data")
            .size("5Gi").build();

    public static final Supplier<PodDisruptionBudgetConfig> DEFAULT_PDB = () -> PodDisruptionBudgetConfig.builder()
            .enabled(true)
            .maxUnavailable(1)
            .build();

    public static final Supplier<MetadataInitializationJobConfig>
            DEFAULT_METADATA_INITIALIZATION_JOB_CONFIG = () -> MetadataInitializationJobConfig.builder()
            .timeout(60)
            .build();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServiceConfig {
        @JsonPropertyDescription(CRDConstants.DOC_SERVICE_ANNOTATIONS)
        private Map<String, String> annotations;
        @JsonPropertyDescription(CRDConstants.DOC_SERVICE_PORTS)
        private List<ServicePort> additionalPorts;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetadataInitializationJobConfig {
        @JsonPropertyDescription(CRDConstants.DOC_RESOURCES)
        private ResourceRequirements resources;
        @JsonPropertyDescription("Timeout (in seconds) for the metadata initialization execution. Default value is 60.")
        private int timeout;
    }

    @JsonPropertyDescription(CRDConstants.DOC_CONFIG)
    protected Map<String, String> config;
    @JsonPropertyDescription("Pod management policy. Default value is 'Parallel'.")
    private String podManagementPolicy;
    @JsonPropertyDescription("Update strategy for the StatefulSet. Default value is rolling update.")
    private StatefulSetUpdateStrategy updateStrategy;
    @JsonPropertyDescription(CRDConstants.DOC_ANNOTATIONS)
    private Map<String, String> annotations;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription(CRDConstants.DOC_GRACE_PERIOD)
    private Integer gracePeriod;
    @JsonPropertyDescription(CRDConstants.DOC_RESOURCES)
    private ResourceRequirements resources;
    @JsonPropertyDescription("Volume configuration for ZooKeeper data.")
    private VolumeConfig dataVolume;
    @JsonPropertyDescription("Service configuration.")
    private ServiceConfig service;
    @JsonPropertyDescription("Configuration about the job that initializes the Pulsar cluster creating the needed "
            + "ZooKeeper nodes.")
    private MetadataInitializationJobConfig metadataInitializationJob;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        super.applyDefaults(globalSpec);
        if (podManagementPolicy == null) {
            podManagementPolicy = "Parallel";
        }
        if (replicas == null) {
            replicas = 3;
        }
        if (updateStrategy == null) {
            updateStrategy = DEFAULT_UPDATE_STRATEGY.get();
        }
        if (gracePeriod == null) {
            gracePeriod = 60;
        }
        if (resources == null) {
            resources = DEFAULT_RESOURCE_REQUIREMENTS.get();
        }
        if (dataVolume == null) {
            dataVolume = DEFAULT_DATA_VOLUME.get();
        }
        dataVolume.mergeVolumeConfigWithGlobal(globalSpec.getStorage());
        dataVolume.merge(DEFAULT_DATA_VOLUME.get());
        if (metadataInitializationJob != null) {
            metadataInitializationJob.setTimeout(ObjectUtils.firstNonNull(
                    metadataInitializationJob.getTimeout(),
                    DEFAULT_METADATA_INITIALIZATION_JOB_CONFIG.get().getTimeout()));
            metadataInitializationJob.setResources(ObjectUtils.firstNonNull(
                    metadataInitializationJob.getResources(),
                    DEFAULT_METADATA_INITIALIZATION_JOB_CONFIG.get().getResources()));
        } else {
            metadataInitializationJob = DEFAULT_METADATA_INITIALIZATION_JOB_CONFIG.get();
        }
    }



    @Override
    protected ProbeConfig getDefaultProbeConfig() {
        return DEFAULT_PROBE.get();
    }

    @Override
    protected PodDisruptionBudgetConfig getDefaultPdb() {
        return DEFAULT_PDB.get();
    }

    @Override
    public boolean isValid(ZooKeeperSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
