package com.datastax.oss.pulsaroperator.crds.zookeeper;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategy;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategyBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder

public class ZooKeeperSpec extends BaseComponentSpec<ZooKeeperSpec> {

    private static final StatefulSetUpdateStrategy DEFAULT_UPDATE_STRATEGY = new StatefulSetUpdateStrategyBuilder()
            .withType("RollingUpdate")
            .build();

    public static final ProbeConfig DEFAULT_PROBE = ProbeConfig.builder()
            .enabled(true)
            .initial(20)
            .period(30)
            .timeout(30)
            .build();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProbeConfig {
        private Boolean enabled;
        private Integer timeout;
        private Integer initial;
        private Integer period;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VolumeConfig {
        private String name = "data";
        private String size;
        private String storageClass;
        private String existingStorageClassName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServiceConfig {
        private Map<String, String> annotations;
        private List<ServicePort> additionalPorts;

    }

    private String component;
    @Min(1)
    private Integer replicas;
    private Map<String, String> config;
    private String podManagementPolicy;
    private StatefulSetUpdateStrategy updateStrategy;
    private Map<String, String> annotations;
    private List<Toleration> tolerations;
    private NodeAffinity nodeAffinity;
    private PodAntiAffinity podAntiAffinity;
    @Min(0)
    private Integer gracePeriod;
    private ResourceRequirements resources;
    private ProbeConfig probe;
    @NotNull
    private VolumeConfig dataVolume;
    private ServiceConfig service;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        super.applyDefaults(globalSpec);
        applyProbeDefault();
        if (podManagementPolicy == null) {
            podManagementPolicy = "Parallel";
        }
        if (replicas == null) {
            replicas = 3;
        }
        if (component == null) {
            component = "zookeeper";
        }
        if (updateStrategy == null) {
            updateStrategy = DEFAULT_UPDATE_STRATEGY;
        }
        if (gracePeriod == null) {
            gracePeriod = 60;
        }
    }

    private void applyProbeDefault() {
        if (probe == null) {
            probe = ZooKeeperSpec.DEFAULT_PROBE;
        } else {
            boolean enabled = probe.getEnabled() == null
                    ? ZooKeeperSpec.DEFAULT_PROBE.getEnabled() : probe.getEnabled();
            if (!enabled) {
                probe = null;
            } else {
                probe = ProbeConfig.builder()
                        .initial(Objects.requireNonNullElse(probe.getInitial(),
                                ZooKeeperSpec.DEFAULT_PROBE.getInitial()))
                        .period(Objects.requireNonNullElse(probe.getPeriod(),
                                ZooKeeperSpec.DEFAULT_PROBE.getPeriod()))
                        .timeout(Objects.requireNonNullElse(probe.getTimeout(),
                                ZooKeeperSpec.DEFAULT_PROBE.getTimeout()))
                        .build();
            }
        }
    }

    @Override
    public boolean isValid(ZooKeeperSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
