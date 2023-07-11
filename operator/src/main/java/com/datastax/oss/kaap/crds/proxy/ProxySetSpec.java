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
package com.datastax.oss.kaap.crds.proxy;

import com.datastax.oss.kaap.crds.BaseComponentSpec;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.ConfigUtil;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.configs.KafkaConfig;
import com.datastax.oss.kaap.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.kaap.crds.configs.ProbesConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.crd.generator.annotation.SchemaFrom;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategy;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategyBuilder;
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
public class ProxySetSpec extends BaseComponentSpec<ProxySetSpec> {

    private static final Supplier<DeploymentStrategy> DEFAULT_UPDATE_STRATEGY = () -> new DeploymentStrategyBuilder()
            .withType("RollingUpdate")
            .withNewRollingUpdate()
            .withNewMaxSurge().withValue(1).endMaxSurge()
            .withNewMaxUnavailable().withValue(0).endMaxUnavailable()
            .endRollingUpdate()
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

    private static final Supplier<ResourceRequirements> DEFAULT_RESOURCE_REQUIREMENTS =
            () -> new ResourceRequirementsBuilder()
                    .withRequests(Map.of("memory", Quantity.parse("1Gi"), "cpu", Quantity.parse("1")))
                    .build();

    public static final Supplier<PodDisruptionBudgetConfig> DEFAULT_PDB = () -> PodDisruptionBudgetConfig.builder()
            .enabled(true)
            .maxUnavailable(1)
            .build();

    public static final Supplier<ServiceConfig> DEFAULT_SERVICE_CONFIG = () -> ServiceConfig.builder()
            .enablePlainTextWithTLS(false)
            .type("LoadBalancer")
            .build();

    private static final Supplier<WebSocketConfig> DEFAULT_WEB_SOCKET_CONFIG = () -> WebSocketConfig.builder()
            .enabled(true)
            .resources(
                    new ResourceRequirementsBuilder()
                            .withRequests(Map.of("memory", Quantity.parse("1Gi"), "cpu", Quantity.parse("1")))
                            .build()
            )
            .probes(ProbesConfig.builder().build())
            .build();

    private static final Supplier<KafkaConfig> DEFAULT_KAFKA_CONFIG =
            () -> KafkaConfig.builder()
                    .enabled(false)
                    .exposePorts(true)
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
        @JsonPropertyDescription("Assign a load balancer IP.")
        private String loadBalancerIP;
        @JsonPropertyDescription("Service type. Default value is 'ClusterIP'")
        private String type;
        @JsonPropertyDescription("Enable plain text connections even if TLS is enabled.")
        private Boolean enablePlainTextWithTLS;

    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WebSocketConfig {
        @JsonPropertyDescription("Enable WebSocket standalone as container in the proxy pod.")
        private Boolean enabled;
        @JsonPropertyDescription(CRDConstants.DOC_RESOURCES)
        private ResourceRequirements resources;
        @JsonPropertyDescription(CRDConstants.DOC_CONFIG)
        // workaround to generate CRD spec that accepts any type as key
        @SchemaFrom(type = JsonNode.class)
        protected Map<String, Object> config;
        @JsonPropertyDescription(CRDConstants.DOC_PROBES)
        private ProbesConfig probes;
    }

    @JsonPropertyDescription(CRDConstants.DOC_CONFIG)
    // workaround to generate CRD spec that accepts any type as key
    @SchemaFrom(type = JsonNode.class)
    protected Map<String, Object> config;
    @JsonPropertyDescription(CRDConstants.DOC_PROBES)
    private ProbesConfig probes;
    @JsonPropertyDescription("Strategy for the proxy deployment.")
    private DeploymentStrategy updateStrategy;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription(CRDConstants.DOC_GRACE_PERIOD)
    private Integer gracePeriod;
    @JsonPropertyDescription(CRDConstants.DOC_RESOURCES)
    private ResourceRequirements resources;
    @JsonPropertyDescription("Service configuration.")
    private ServiceConfig service;
    @JsonPropertyDescription("WebSocket configuration.")
    private WebSocketConfig webSocket;
    @JsonPropertyDescription("Whether or not the functions worker is in standalone mode.")
    private Boolean standaloneFunctionsWorker;
    @JsonPropertyDescription("Override the resource names generated by the operator.")
    private String overrideResourceName;
    @JsonPropertyDescription("Enable Kafka protocol.")
    private KafkaConfig kafka;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        super.applyDefaults(globalSpec);
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
        if (probes == null) {
            probes = DEFAULT_PROBE.get();
        } else {
            probes = ConfigUtil.applyDefaultsWithReflection(probes, DEFAULT_PROBE);
        }
        applyServiceDefaults();
        applyWebSocketDefaults();
        applyKafkaDefaults();
    }


    private void applyKafkaDefaults() {
        if (kafka == null) {
            kafka = DEFAULT_KAFKA_CONFIG.get();
        } else {
            kafka = ConfigUtil.applyDefaultsWithReflection(kafka, DEFAULT_KAFKA_CONFIG);
        }
    }

    private void applyWebSocketDefaults() {
        if (webSocket == null) {
            webSocket = DEFAULT_WEB_SOCKET_CONFIG.get();
        } else {
            webSocket = ConfigUtil.applyDefaultsWithReflection(webSocket, DEFAULT_WEB_SOCKET_CONFIG);
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

        service.setEnablePlainTextWithTLS(ObjectUtils.getFirstNonNull(
                () -> service.getEnablePlainTextWithTLS(),
                () -> DEFAULT_SERVICE_CONFIG.get().getEnablePlainTextWithTLS()));

        service.setAdditionalPorts(ObjectUtils.getFirstNonNull(
                () -> service.getAdditionalPorts(),
                () -> DEFAULT_SERVICE_CONFIG.get().getAdditionalPorts()));
    }

    @Override
    protected PodDisruptionBudgetConfig getDefaultPdb() {
        return DEFAULT_PDB.get();
    }

    @Override
    public boolean isValid(ProxySetSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
