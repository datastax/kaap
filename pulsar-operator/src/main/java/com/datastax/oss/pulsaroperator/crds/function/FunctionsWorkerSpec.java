package com.datastax.oss.pulsaroperator.crds.function;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.crd.generator.annotation.SchemaFrom;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
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
public class FunctionsWorkerSpec extends BaseComponentSpec<FunctionsWorkerSpec> {

    private static final Supplier<StatefulSetUpdateStrategy> DEFAULT_UPDATE_STRATEGY = () -> new StatefulSetUpdateStrategyBuilder()
            .withType("RollingUpdate")
            .build();

    public static final Supplier<VolumeConfig> DEFAULT_LOGS_VOLUME = () -> VolumeConfig.builder()
            .name("logs")
            .size("5Gi").build();

    public static final Supplier<ProbeConfig> DEFAULT_PROBE = () -> ProbeConfig.builder()
            .enabled(true)
            .initial(10)
            .period(30)
            .timeout(5)
            .build();

    private static final Supplier<ResourceRequirements> DEFAULT_RESOURCE_REQUIREMENTS = () -> new ResourceRequirementsBuilder()
            .withRequests(Map.of("memory", Quantity.parse("4Gi"), "cpu", Quantity.parse("1")))
            .build();

    public static final Supplier<PodDisruptionBudgetConfig> DEFAULT_PDB = () -> PodDisruptionBudgetConfig.builder()
            .enabled(true)
            .maxUnavailable(1)
            .build();

    public static final Supplier<ServiceConfig> DEFAULT_SERVICE_CONFIG = () -> ServiceConfig.builder()
            .type("ClusterIP")
            .build();


    private static final Supplier<RbacConfig> DEFAULT_RBAC_CONFIG = () ->
            RbacConfig.builder()
                    .create(true)
                    .namespaced(true)
                    .build();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RbacConfig {
        @JsonPropertyDescription("Create needed RBAC to run the Functions Worker.")
        private Boolean create;
        @JsonPropertyDescription("Whether or not the RBAC is created per-namespace or for the cluster.")
        private Boolean namespaced;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServiceConfig {
        @JsonPropertyDescription("Additional annotations to add to the Broker Service resources.")
        private Map<String, String> annotations;
        @JsonPropertyDescription("Additional ports for the Broker Service resources.")
        private List<ServicePort> additionalPorts;
        @JsonPropertyDescription("Service type. Default value is 'ClusterIP'")
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InitContainerConfig {
        @JsonPropertyDescription("The image used to run the container.")
        private String image;
        @JsonPropertyDescription("The image pull policy used for the container.")
        private String imagePullPolicy;
        @JsonPropertyDescription("The command used for the container.")
        private List<String> command;
        @JsonPropertyDescription("The command args used for the container.")
        private List<String> args;
        @JsonPropertyDescription("The container path where the emptyDir volume is mounted.")
        private String emptyDirPath;
    }

    @JsonPropertyDescription("Configuration entries directly passed to this component.")
    @SchemaFrom(type = JsonNode.class)
    protected Map<String, Object> config;
    @JsonPropertyDescription("Update strategy for the Broker pod/s. ")
    private StatefulSetUpdateStrategy updateStrategy;
    @JsonPropertyDescription("Pod management policy for the Broker pod.")
    private String podManagementPolicy;
    @JsonPropertyDescription("Update strategy for the Broker pod/s. ")
    private List<LocalObjectReference> imagePullSecrets;
    @JsonPropertyDescription("Annotations to add to each Broker resource.")
    private Map<String, String> annotations;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription("Termination grace period in seconds for the Broker pod. Default value is 60.")
    private Integer gracePeriod;
    @JsonPropertyDescription("Resource requirements for the Broker pod.")
    private ResourceRequirements resources;
    @JsonPropertyDescription("Configurations for the Service resources associated to the Broker pod.")
    private ServiceConfig service;
    @JsonPropertyDescription("Additional init container.")
    private InitContainerConfig initContainer;
    @JsonPropertyDescription("Volume configuration for export function logs.")
    private VolumeConfig logsVolume;
    @JsonPropertyDescription("Runtime mode for functions.")
    private String runtime;
    @JsonPropertyDescription("Function runtime resources.")
    private RbacConfig rbac;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        super.applyDefaults(globalSpec);

        if (replicas == null) {
            // disabled by default
            replicas = 0;
        }
        if (gracePeriod == null) {
            gracePeriod = 60;
        }
        if (resources == null) {
            resources = DEFAULT_RESOURCE_REQUIREMENTS.get();
        }
        if (updateStrategy == null) {
            updateStrategy = DEFAULT_UPDATE_STRATEGY.get();
        }
        if (podManagementPolicy == null) {
            podManagementPolicy = "Parallel";
        }
        if (logsVolume == null) {
            logsVolume = DEFAULT_LOGS_VOLUME.get();
        }
        logsVolume.mergeVolumeConfigWithGlobal(globalSpec.getStorage());
        logsVolume.merge(DEFAULT_LOGS_VOLUME.get());

        if (runtime == null) {
            runtime = "process";
        }
        applyServiceDefaults();
        applyRbacDefaults();
    }

    private void applyRbacDefaults() {
        if (rbac == null) {
            rbac = DEFAULT_RBAC_CONFIG.get();
        }
        rbac.setCreate(
                ObjectUtils.getFirstNonNull(
                        () -> rbac.getCreate(),
                        () -> DEFAULT_RBAC_CONFIG.get().getCreate()
                )
        );
        rbac.setNamespaced(
                ObjectUtils.getFirstNonNull(
                        () -> rbac.getNamespaced(),
                        () -> DEFAULT_RBAC_CONFIG.get().getNamespaced()
                )
        );
    }

    private void applyServiceDefaults() {
        if (service == null) {
            service = DEFAULT_SERVICE_CONFIG.get();
        }
        service.setAnnotations(ObjectUtils.getFirstNonNull(
                () -> service.getAnnotations(),
                () -> DEFAULT_SERVICE_CONFIG.get().getAnnotations()));

        service.setType(ObjectUtils.getFirstNonNull(
                () -> service.getType(),
                () -> DEFAULT_SERVICE_CONFIG.get().getType()));

        service.setAdditionalPorts(ObjectUtils.getFirstNonNull(
                () -> service.getAdditionalPorts(),
                () -> DEFAULT_SERVICE_CONFIG.get().getAdditionalPorts()));
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
    public boolean isValid(FunctionsWorkerSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
