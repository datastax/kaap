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
package com.datastax.oss.pulsaroperator.crds.bookkeeper;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.ConfigUtil;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbesConfig;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
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
public class BookKeeperSpec extends BaseComponentSpec<BookKeeperSpec> {

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
            .stabilizationWindowMs(TimeUnit.SECONDS.toMillis(300))
            .diskUsageToleranceHwm(0.92d)
            .diskUsageToleranceLwm(0.75d)
            .cleanUpPvcs(true)
            .bookieUrl("http://localhost:8000")
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

        applyAutoscalerDefaults();
    }

    @Override
    protected PodDisruptionBudgetConfig getDefaultPdb() {
        return DEFAULT_PDB.get();
    }

    @Override
    public boolean isValid(BookKeeperSpec value, ConstraintValidatorContext context) {
        return true;
    }

    private void applyAutoscalerDefaults() {
        if (autoscaler == null) {
            autoscaler = DEFAULT_BK_CONFIG.get();
        }

        autoscaler.setEnabled(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getEnabled(),
                () -> DEFAULT_BK_CONFIG.get().getEnabled()
        ));
        autoscaler.setPeriodMs(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getPeriodMs(),
                () -> DEFAULT_BK_CONFIG.get().getPeriodMs()
        ));
        autoscaler.setScaleUpBy(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getScaleUpBy(),
                () -> DEFAULT_BK_CONFIG.get().getScaleUpBy()
        ));
        autoscaler.setScaleDownBy(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getScaleDownBy(),
                () -> DEFAULT_BK_CONFIG.get().getScaleDownBy()
        ));
        autoscaler.setStabilizationWindowMs(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getStabilizationWindowMs(),
                () -> DEFAULT_BK_CONFIG.get().getStabilizationWindowMs()
        ));
        autoscaler.setMinWritableBookies(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getMinWritableBookies(),
                () -> DEFAULT_BK_CONFIG.get().getMinWritableBookies()
        ));
        autoscaler.setDiskUsageToleranceHwm(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getDiskUsageToleranceHwm(),
                () -> DEFAULT_BK_CONFIG.get().getDiskUsageToleranceHwm()
        ));
        autoscaler.setDiskUsageToleranceLwm(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getDiskUsageToleranceLwm(),
                () -> DEFAULT_BK_CONFIG.get().getDiskUsageToleranceLwm()
        ));
        autoscaler.setCleanUpPvcs(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getCleanUpPvcs(),
                () -> DEFAULT_BK_CONFIG.get().getCleanUpPvcs()
        ));
        autoscaler.setBookieUrl(ObjectUtils.getFirstNonNull(
                () -> autoscaler.getBookieUrl(),
                () -> DEFAULT_BK_CONFIG.get().getBookieUrl()
        ));
    }

}
