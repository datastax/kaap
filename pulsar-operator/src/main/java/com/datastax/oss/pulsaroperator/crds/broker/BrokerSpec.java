package com.datastax.oss.pulsaroperator.crds.broker;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategy;
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
public class BrokerSpec extends BaseComponentSpec<BrokerSpec> {

    public static final Supplier<ProbeConfig> DEFAULT_PROBE = () -> ProbeConfig.builder()
            .enabled(true)
            .initial(10)
            .period(30)
            .timeout(5)
            .build();

    private static final Supplier<ResourceRequirements> DEFAULT_RESOURCE_REQUIREMENTS = () -> new ResourceRequirementsBuilder()
            .withRequests(Map.of("memory", Quantity.parse("2Gi"), "cpu", Quantity.parse("1")))
            .build();

    public static final Supplier<PodDisruptionBudgetConfig> DEFAULT_PDB = () -> PodDisruptionBudgetConfig.builder()
            .enabled(true)
            .maxUnavailable(1)
            .build();

    public static final Supplier<ServiceConfig> DEFAULT_SERVICE_CONFIG = () -> ServiceConfig.builder()
            .headless(false)
            .type("ClusterIP")
            .build();


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServiceConfig {
        @JsonPropertyDescription("Additional annotations to add to the Broker Service resources.")
        private Map<String, String> annotations;
        @JsonPropertyDescription("Additional ports for the Broker Service resources.")
        private List<ServicePort> additionalPorts;
        @JsonPropertyDescription("Do not assign an IP to the Service.")
        private Boolean headless;
        @JsonPropertyDescription("Service type. Default value is 'ClusterIP'")
        private String type;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionCoordinatorConfig {
        @JsonPropertyDescription("Enable the transaction coordinator in the broker.")
        private Boolean enabled;
        @JsonPropertyDescription("Partitions count for the transaction's topic.")
        private Integer partitions;
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


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LedgerConfig {
        @JsonPropertyDescription("Ensemble value.")
        private int defaultEnsembleSize;
        @JsonPropertyDescription("Ack quorum value.")
        private int defaultAckQuorum;
        @JsonPropertyDescription("Write quorum value.")
        private int defaultWriteQuorum;
    }

    @JsonPropertyDescription("Enable functions worker embedded in the broker.")
    private Boolean functionsWorkerEnabled;
    @JsonPropertyDescription("Enable websocket service in the broker.")
    private Boolean webSocketServiceEnabled;
    @JsonPropertyDescription("Enable transactions in the broker.")
    private TransactionCoordinatorConfig transactions;
    @JsonPropertyDescription("Update strategy for the Broker pod/s. ")
    private StatefulSetUpdateStrategy updateStrategy;
    @JsonPropertyDescription("Pod management policy for the Broker pod.")
    private String podManagementPolicy;
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
    @JsonPropertyDescription("Service account name for the Broker StatefulSet.")
    private String serviceAccountName;
    @JsonPropertyDescription("Additional init container.")
    private InitContainerConfig initContainer;
    @JsonPropertyDescription("Ledger config.")
    private LedgerConfig ledger;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        super.applyDefaults(globalSpec);
        if (replicas == null) {
            replicas = 3;
        }
        if (gracePeriod == null) {
            gracePeriod = 60;
        }
        if (resources == null) {
            resources = DEFAULT_RESOURCE_REQUIREMENTS.get();
        }
        applyServiceDefaults();
        if (functionsWorkerEnabled == null) {
            functionsWorkerEnabled = false;
        }
        if (webSocketServiceEnabled == null) {
            webSocketServiceEnabled = false;
        }
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

        service.setHeadless(ObjectUtils.getFirstNonNull(
                () -> service.getHeadless(),
                () -> DEFAULT_SERVICE_CONFIG.get().getHeadless()));

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
    public boolean isValid(BrokerSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
