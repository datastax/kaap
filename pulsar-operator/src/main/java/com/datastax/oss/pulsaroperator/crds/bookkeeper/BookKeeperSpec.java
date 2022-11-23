package com.datastax.oss.pulsaroperator.crds.bookkeeper;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
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

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BookKeeperSpec extends BaseComponentSpec<BookKeeperSpec> {

    private static final Supplier<StatefulSetUpdateStrategy> DEFAULT_UPDATE_STRATEGY = () -> new StatefulSetUpdateStrategyBuilder()
            .withType("RollingUpdate")
            .build();

    public static final Supplier<ProbeConfig> DEFAULT_PROBE = () -> ProbeConfig.builder()
            .enabled(true)
            .initial(10)
            .period(30)
            .timeout(5)
            .build();

    public static final Supplier<Volumes> DEFAULT_VOLUMES = () -> Volumes.builder()
            .journal(VolumeConfig.builder()
                    .name("journal")
                    .size("20Gi")
                    .build())
            .ledgers(VolumeConfig.builder()
                    .name("ledgers")
                    .size("50Gi")
                    .build())
            .build();



    private static final Supplier<ResourceRequirements> DEFAULT_RESOURCE_REQUIREMENTS = () -> new ResourceRequirementsBuilder()
            .withRequests(Map.of("memory", Quantity.parse("2Gi"), "cpu", Quantity.parse("1")))
            .build();

    public static final Supplier<PodDisruptionBudgetConfig> DEFAULT_PDB = () -> PodDisruptionBudgetConfig.builder()
            .enabled(true)
            .maxUnavailable(1)
            .build();


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Volumes {
        @JsonPropertyDescription("Indicates the volume config for the journal.")
        private VolumeConfig journal;
        @JsonPropertyDescription("Indicates the volume config for the ledgers.")
        private VolumeConfig ledgers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServiceConfig {
        @JsonPropertyDescription("Additional annotations to add to the BookKeeper Service resources.")
        private Map<String, String> annotations;
        @JsonPropertyDescription("Additional ports for the BookKeeper Service resources.")
        private List<ServicePort> additionalPorts;
    }


    @JsonPropertyDescription("Configuration entries directly passed to this component.")
    protected Map<String, String> config;
    @JsonPropertyDescription("Update strategy for the BookKeeper pod/s. Default value is rolling update.")
    private StatefulSetUpdateStrategy updateStrategy;
    @JsonPropertyDescription("Pod management policy for the BookKeeper pod. Default value is 'Parallel'.")
    private String podManagementPolicy;
    @JsonPropertyDescription("Annotations to add to each BookKeeper resource.")
    private Map<String, String> annotations;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription("Termination grace period in seconds for the BookKeeper pod. Default value is 60.")
    private Integer gracePeriod;
    @JsonPropertyDescription("Resource requirements for the BookKeeper pod.")
    private ResourceRequirements resources;
    @JsonPropertyDescription("Volumes configuration.")
    private Volumes volumes;
    @JsonPropertyDescription("Prefix for each PVC created.")
    private String pvcPrefix;
    @JsonPropertyDescription("Configurations for the Service resources associated to the BookKeeper pod.")
    private ServiceConfig service;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        super.applyDefaults(globalSpec);
        if (replicas == null) {
            replicas = 3;
        }
        if (updateStrategy == null) {
            updateStrategy = DEFAULT_UPDATE_STRATEGY.get();
        }
        if (podManagementPolicy == null) {
            podManagementPolicy = "Parallel";
        }
        if (gracePeriod == null) {
            gracePeriod = 60;
        }
        if (resources == null) {
            resources = DEFAULT_RESOURCE_REQUIREMENTS.get();
        }
        if (volumes == null) {
            volumes = DEFAULT_VOLUMES.get();
        }
        if (volumes.getJournal() == null) {
            volumes.setJournal(DEFAULT_VOLUMES.get().getJournal());
        }
        if (volumes.getLedgers() == null) {
            volumes.setLedgers(DEFAULT_VOLUMES.get().getLedgers());
        }
        volumes.getJournal().mergeVolumeConfigWithGlobal(globalSpec.getStorage());
        volumes.getJournal().merge(DEFAULT_VOLUMES.get().getJournal());
        volumes.getLedgers().mergeVolumeConfigWithGlobal(globalSpec.getStorage());
        volumes.getLedgers().merge(DEFAULT_VOLUMES.get().getLedgers());
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
    public boolean isValid(BookKeeperSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
