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
package com.datastax.oss.kaap.crds.function;

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
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
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

    public static final Supplier<ProbesConfig> DEFAULT_PROBES = () -> ProbesConfig.builder()
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
        @JsonPropertyDescription("Whether or not the RBAC is created per-namespace or cluster-wise.")
        private Boolean namespaced;
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
        @JsonPropertyDescription("Service type. Default value is 'ClusterIP'")
        private String type;
    }

    @JsonPropertyDescription(CRDConstants.DOC_CONFIG)
    // workaround to generate CRD spec that accepts any type as key
    @SchemaFrom(type = JsonNode.class)
    protected Map<String, Object> config;
    @JsonPropertyDescription(CRDConstants.DOC_PROBES)
    private ProbesConfig probes;
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
    private ServiceConfig service;
    @JsonPropertyDescription("Volume configuration for export function logs.")
    private VolumeConfig logsVolume;
    @JsonPropertyDescription("Runtime mode for functions.")
    private String runtime;
    @JsonPropertyDescription("RBAC config.")
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
        if (probes == null) {
            probes = DEFAULT_PROBES.get();
        } else {
            probes = ConfigUtil.applyDefaultsWithReflection(probes, DEFAULT_PROBES);
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
    protected PodDisruptionBudgetConfig getDefaultPdb() {
        return DEFAULT_PDB.get();
    }

    @Override
    public boolean isValid(FunctionsWorkerSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
