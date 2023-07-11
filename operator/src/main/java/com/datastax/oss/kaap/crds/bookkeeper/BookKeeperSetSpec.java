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
package com.datastax.oss.kaap.crds.bookkeeper;

import com.datastax.oss.kaap.crds.BaseComponentSpec;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.ConfigUtil;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.kaap.crds.configs.ProbesConfig;
import com.datastax.oss.kaap.crds.configs.VolumeConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.crd.generator.annotation.SchemaFrom;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategy;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategyBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BookKeeperSetSpec extends BaseComponentSpec<BookKeeperSetSpec> {

    private static final Supplier<StatefulSetUpdateStrategy> DEFAULT_UPDATE_STRATEGY =
            () -> new StatefulSetUpdateStrategyBuilder()
                    .withType("RollingUpdate")
                    .build();

    public static final Supplier<ProbesConfig> DEFAULT_PROBE = () -> ProbesConfig.builder()
            .liveness(ProbesConfig.ProbeConfig.builder()
                    .enabled(true)
                    .initialDelaySeconds(10)
                    .periodSeconds(30)
                    .timeoutSeconds(5)
                    .build())
            .readiness(ProbesConfig.ProbeConfig.builder()
                    .enabled(true)
                    .initialDelaySeconds(10)
                    .periodSeconds(30)
                    .timeoutSeconds(5)
                    .build())
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

    private static final Supplier<ResourceRequirements> DEFAULT_RESOURCE_REQUIREMENTS =
            () -> new ResourceRequirementsBuilder()
                    .withRequests(Map.of("memory", Quantity.parse("2Gi"), "cpu", Quantity.parse("1")))
                    .build();

    public static final Supplier<PodDisruptionBudgetConfig> DEFAULT_PDB = () -> PodDisruptionBudgetConfig.builder()
            .enabled(true)
            .maxUnavailable(1)
            .build();

    private static final Supplier<BookKeeperAutoscalerSpec> DEFAULT_BK_CONFIG = () -> BookKeeperAutoscalerSpec.builder()
            .enabled(false)
            .periodMs(TimeUnit.SECONDS.toMillis(10))
            .minWritableBookies(3)
            .scaleUpBy(1)
            .scaleDownBy(1)
            .scaleUpMaxLimit(30)
            .stabilizationWindowMs(TimeUnit.MINUTES.toMillis(5))
            .diskUsageToleranceHwm(0.92d)
            .diskUsageToleranceLwm(0.75d)
            .build();


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Volumes {
        @JsonPropertyDescription("Config for the journal volume.")
        private VolumeConfig journal;
        @JsonPropertyDescription("Config for the ledgers volume.")
        private VolumeConfig ledgers;
    }

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


    @JsonPropertyDescription(CRDConstants.DOC_CONFIG)
    // workaround to generate CRD spec that accepts any type as key
    @SchemaFrom(type = JsonNode.class)
    protected Map<String, Object> config;
    @JsonPropertyDescription(CRDConstants.DOC_PROBES)
    private ProbesConfig probes;
    @JsonPropertyDescription("Update strategy for the StatefulSet. Default value is rolling update.")
    private StatefulSetUpdateStrategy updateStrategy;
    @JsonPropertyDescription("Pod management policy. Default value is 'Parallel'.")
    private String podManagementPolicy;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription(CRDConstants.DOC_GRACE_PERIOD)
    private Integer gracePeriod;
    @JsonPropertyDescription(CRDConstants.DOC_RESOURCES)
    private ResourceRequirements resources;
    @JsonPropertyDescription("Volumes configuration.")
    private Volumes volumes;
    @JsonPropertyDescription("Prefix for each PVC created.")
    private String pvcPrefix;
    @JsonPropertyDescription("Service configuration.")
    private ServiceConfig service;
    @JsonPropertyDescription("Autoscaling config.")
    @Valid
    private BookKeeperAutoscalerSpec autoscaler;
    @JsonPropertyDescription("Override the resource names generated by the operator.")
    private String overrideResourceName;
    @JsonPropertyDescription("Cleanup PVCs after the bookie has been removed.")
    private Boolean cleanUpPvcs;

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
        if (probes == null) {
            probes = DEFAULT_PROBE.get();
        } else {
            probes = ConfigUtil.applyDefaultsWithReflection(probes, DEFAULT_PROBE);
        }
        if (cleanUpPvcs == null) {
            cleanUpPvcs = true;
        }

        applyAutoscalerDefaults();
    }

    @Override
    protected PodDisruptionBudgetConfig getDefaultPdb() {
        return DEFAULT_PDB.get();
    }

    @Override
    public boolean isValid(BookKeeperSetSpec value, ConstraintValidatorContext context) {
        return true;
    }

    private void applyAutoscalerDefaults() {
        if (autoscaler == null) {
            autoscaler = DEFAULT_BK_CONFIG.get();
        } else {
            autoscaler = ConfigUtil.applyDefaultsWithReflection(autoscaler, DEFAULT_BK_CONFIG);
        }
    }

}
