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
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Valid;
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
            .type("ClusterIP")
            .build();


    private static final Supplier<BrokerAutoscalerSpec> DEFAULT_BROKER_CONFIG = () -> BrokerAutoscalerSpec.builder()
            .enabled(false)
            .periodMs(TimeUnit.SECONDS.toMillis(10))
            .min(1)
            .lowerCpuThreshold(0.3d)
            .higherCpuThreshold(0.8d)
            .scaleUpBy(1)
            .scaleDownBy(1)
            .stabilizationWindowMs(TimeUnit.SECONDS.toMillis(300))
            .build();

    private static final Supplier<TransactionCoordinatorConfig> DEFAULT_TRANSACTION_COORDINATOR_CONFIG = () ->
            TransactionCoordinatorConfig.builder()
                    .enabled(false)
                    .partitions(16)
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
        @JsonPropertyDescription("Initialization job configuration.")
        private TransactionCoordinatorInitJobConfig initJob;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionCoordinatorInitJobConfig {
        @JsonPropertyDescription("Resource requirements for the Job's Pod.")
        private ResourceRequirements resources;
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
    @JsonPropertyDescription("Autoscaling config.")
    @Valid
    private BrokerAutoscalerSpec autoscaler;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        super.applyDefaults(globalSpec);

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
        applyAutoscalerDefaults();
        if (replicas == null) {
            if (!autoscaler.getEnabled()) {
                replicas = 3;
            }
        }
        applyTransactionsDefaults();
    }

    private void applyTransactionsDefaults() {
        if (transactions == null) {
            transactions = DEFAULT_TRANSACTION_COORDINATOR_CONFIG.get();
        }
        transactions.setEnabled(
                ObjectUtils.getFirstNonNull(
                        () -> transactions.getEnabled(),
                        () -> DEFAULT_TRANSACTION_COORDINATOR_CONFIG.get().getEnabled()
                )
        );
        transactions.setPartitions(
                ObjectUtils.getFirstNonNull(
                        () -> transactions.getPartitions(),
                        () -> DEFAULT_TRANSACTION_COORDINATOR_CONFIG.get().getPartitions()
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


    private void applyAutoscalerDefaults() {
        if (autoscaler == null) {
            autoscaler = DEFAULT_BROKER_CONFIG.get();
        }
        autoscaler.setEnabled(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getEnabled(),
                () -> DEFAULT_BROKER_CONFIG.get().getEnabled()
        ));
        autoscaler.setPeriodMs(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getPeriodMs(),
                () -> DEFAULT_BROKER_CONFIG.get().getPeriodMs()
        ));
        autoscaler.setMin(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getMin(),
                () -> DEFAULT_BROKER_CONFIG.get().getMin()
        ));
        autoscaler.setMax(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getMax(),
                () -> DEFAULT_BROKER_CONFIG.get().getMax()
        ));
        autoscaler.setLowerCpuThreshold(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getLowerCpuThreshold(),
                () -> DEFAULT_BROKER_CONFIG.get().getLowerCpuThreshold()
        ));
        autoscaler.setHigherCpuThreshold(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getHigherCpuThreshold(),
                () -> DEFAULT_BROKER_CONFIG.get().getHigherCpuThreshold()
        ));
        autoscaler.setScaleUpBy(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getScaleUpBy(),
                () -> DEFAULT_BROKER_CONFIG.get().getScaleUpBy()
        ));
        autoscaler.setScaleDownBy(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getScaleDownBy(),
                () -> DEFAULT_BROKER_CONFIG.get().getScaleDownBy()
        ));
        autoscaler.setStabilizationWindowMs(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getStabilizationWindowMs(),
                () -> DEFAULT_BROKER_CONFIG.get().getStabilizationWindowMs()
        ));
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
