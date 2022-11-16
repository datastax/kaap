package com.datastax.oss.pulsaroperator.crds.proxy;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategy;
import io.fabric8.kubernetes.api.model.apps.DeploymentStrategyBuilder;
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
public class ProxySpec extends BaseComponentSpec<ProxySpec> {

    private static final Supplier<DeploymentStrategy> DEFAULT_UPDATE_STRATEGY = () -> new DeploymentStrategyBuilder()
            .withType("RollingUpdate")
            .withNewRollingUpdate()
            .withNewMaxSurge().withValue(1).endMaxSurge()
            .withNewMaxUnavailable().withValue(0).endMaxUnavailable()
            .endRollingUpdate()
            .build();

    public static final Supplier<ProbeConfig> DEFAULT_PROBE = () -> ProbeConfig.builder()
            .enabled(true)
            .initial(10)
            .period(30)
            .timeout(5)
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
            .build();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServiceConfig {
        @JsonPropertyDescription("Additional annotations to add to the Service resources.")
        private Map<String, String> annotations;
        @JsonPropertyDescription("Additional ports for the Service resources.")
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
    public static class WebSocketConfig {
        @JsonPropertyDescription("Enable WebSocket standalone as container in the proxy pod.")
        private Boolean enabled;
        @JsonPropertyDescription("Resource requirements for the pod.")
        private ResourceRequirements resources;
    }


    @JsonPropertyDescription("Strategy for the proxy deployment.")
    private DeploymentStrategy updateStrategy;
    @JsonPropertyDescription("Annotations to add to each Proxy resource.")
    private Map<String, String> annotations;
    @Min(0)
    @io.fabric8.generator.annotation.Min(0)
    @JsonPropertyDescription("Termination grace period in seconds for the Proxy pod. Default value is 60.")
    private Integer gracePeriod;
    @JsonPropertyDescription("Resource requirements for the Proxy container.")
    private ResourceRequirements resources;
    @JsonPropertyDescription("Configurations for the Service resource associated to the Proxy pod.")
    private ServiceConfig service;
    @JsonPropertyDescription("Additional init container.")
    private InitContainerConfig initContainer;
    @JsonPropertyDescription("WebSocket proxy configuration.")
    private WebSocketConfig webSocket;

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
        applyServiceDefaults();

        if (webSocket == null) {
            webSocket = DEFAULT_WEB_SOCKET_CONFIG.get();
        }
        webSocket.setEnabled(ObjectUtils.getFirstNonNull(
                () -> webSocket.getEnabled(),
                () -> DEFAULT_WEB_SOCKET_CONFIG.get().getEnabled()));
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
    protected ProbeConfig getDefaultProbeConfig() {
        return DEFAULT_PROBE.get();
    }

    @Override
    protected PodDisruptionBudgetConfig getDefaultPdb() {
        return DEFAULT_PDB.get();
    }

    @Override
    public boolean isValid(ProxySpec value, ConstraintValidatorContext context) {
        return true;
    }
}
