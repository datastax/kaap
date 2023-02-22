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
package com.datastax.oss.pulsaroperator.crds.broker;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.ConfigUtil;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbesConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.crd.generator.annotation.SchemaFrom;
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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.ObjectUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BrokerSpec extends BaseComponentSpec<BrokerSpec> {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class BrokerProbesConfig extends ProbesConfig {
        @JsonPropertyDescription("Use healthcheck for the liveness probe. If false, the /metrics endpoint will be "
                + "used.")
        private Boolean useHealthCheckForLiveness;
        @JsonPropertyDescription("Use healthcheck for the readiness probe. If false, the /metrics endpoint will be "
                + "used.")
        private Boolean useHealthCheckForReadiness;

        @Builder(builderMethodName = "brokerProbeConfigBuilder")
        public BrokerProbesConfig(ProbeConfig readiness,
                                  ProbeConfig liveness, Boolean useHealthCheckForLiveness,
                                  Boolean useHealthCheckForReadiness) {
            super(readiness, liveness);
            this.useHealthCheckForLiveness = useHealthCheckForLiveness;
            this.useHealthCheckForReadiness = useHealthCheckForReadiness;
        }
    }

    public static final Supplier<BrokerWithSetsSpec.BrokerProbesConfig>
            DEFAULT_PROBE = () -> BrokerWithSetsSpec.BrokerProbesConfig.brokerProbeConfigBuilder()
            .readiness(ProbesConfig.ProbeConfig.builder()
                    .enabled(true)
                    .initialDelaySeconds(10)
                    .periodSeconds(30)
                    .timeoutSeconds(5)
                    .build())
            .liveness(ProbesConfig.ProbeConfig.builder()
                    .enabled(true)
                    .initialDelaySeconds(10)
                    .periodSeconds(30)
                    .timeoutSeconds(5)
                    .build())
            .useHealthCheckForLiveness(true)
            .useHealthCheckForReadiness(true)
            .build();

    private static final Supplier<ResourceRequirements> DEFAULT_RESOURCE_REQUIREMENTS =
            () -> new ResourceRequirementsBuilder()
                    .withRequests(Map.of("memory", Quantity.parse("2Gi"), "cpu", Quantity.parse("1")))
                    .build();

    public static final Supplier<PodDisruptionBudgetConfig> DEFAULT_PDB = () -> PodDisruptionBudgetConfig.builder()
            .enabled(true)
            .maxUnavailable(1)
            .build();

    public static final Supplier<BrokerWithSetsSpec.ServiceConfig> DEFAULT_SERVICE_CONFIG = () -> BrokerWithSetsSpec.ServiceConfig.builder()
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

    private static final Supplier<BrokerWithSetsSpec.TransactionCoordinatorConfig> DEFAULT_TRANSACTION_COORDINATOR_CONFIG = () ->
            BrokerWithSetsSpec.TransactionCoordinatorConfig.builder()
                    .enabled(false)
                    .partitions(16)
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
        @JsonPropertyDescription("Service type. Default value is 'ClusterIP'")
        private String type;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionCoordinatorConfig {
        @JsonPropertyDescription("Initialize the transaction coordinator if it's not yet and configure the broker to "
                + "accept transactions.")
        private Boolean enabled;
        @JsonPropertyDescription("Number of coordinators to create.")
        private Integer partitions;
        @JsonPropertyDescription("Config for the init job.")
        private BrokerWithSetsSpec.TransactionCoordinatorInitJobConfig initJob;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionCoordinatorInitJobConfig {
        @JsonPropertyDescription(CRDConstants.DOC_RESOURCES)
        private ResourceRequirements resources;
    }

    @JsonPropertyDescription(CRDConstants.DOC_CONFIG)
    // workaround to generate CRD spec that accepts any type as key
    @SchemaFrom(type = JsonNode.class)
    protected Map<String, Object> config;
    @JsonPropertyDescription(CRDConstants.DOC_PROBES)
    private BrokerWithSetsSpec.BrokerProbesConfig probes;
    @JsonPropertyDescription("Enable functions worker embedded in the broker.")
    private Boolean functionsWorkerEnabled;
    @JsonPropertyDescription("Enable transactions in the broker.")
    private BrokerWithSetsSpec.TransactionCoordinatorConfig transactions;
    @JsonPropertyDescription("Update strategy for the StatefulSet.")
    private StatefulSetUpdateStrategy updateStrategy;
    @JsonPropertyDescription("Pod management policy.")
    private String podManagementPolicy;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription(CRDConstants.DOC_GRACE_PERIOD)
    private Integer gracePeriod;
    @JsonPropertyDescription(CRDConstants.DOC_RESOURCES)
    private ResourceRequirements resources;
    @JsonPropertyDescription("Service configuration.")
    private BrokerWithSetsSpec.ServiceConfig service;
    @JsonPropertyDescription("Service account name for the Broker StatefulSet.")
    private String serviceAccountName;
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
        if (probes == null) {
            probes = DEFAULT_PROBE.get();
        } else {
            probes = ConfigUtil.applyDefaultsWithReflection(probes, DEFAULT_PROBE);
        }
        applyServiceDefaults();
        if (functionsWorkerEnabled == null) {
            functionsWorkerEnabled = false;
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
    protected PodDisruptionBudgetConfig getDefaultPdb() {
        return DEFAULT_PDB.get();
    }

    @Override
    public boolean isValid(BrokerSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
