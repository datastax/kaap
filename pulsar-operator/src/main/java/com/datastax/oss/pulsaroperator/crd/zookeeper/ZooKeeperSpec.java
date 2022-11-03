package com.datastax.oss.pulsaroperator.crd.zookeeper;

import com.datastax.oss.pulsaroperator.crd.BaseComponentSpec;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategy;
import java.util.List;
import java.util.Map;
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
public class ZooKeeperSpec extends BaseComponentSpec {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProbeConfig {
        private boolean enabled;
        private int timeout;
        private int initial;
        private int period;
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

    @Builder.Default
    private String component = "zookeeper";
    @Builder.Default
    private int replicas = 1;
    private Map<String, String> config;
    @Builder.Default
    private String podManagementPolicy = "Parallel";
    @Builder.Default
    private String imagePullPolicy = "IfNotPresent";
    private StatefulSetUpdateStrategy updateStrategy;
    private Map<String, String> annotations;
    private List<Toleration> tolerations;
    private NodeAffinity nodeAffinity;
    private PodAntiAffinity podAntiAffinity;
    private int gracePeriod;
    private ResourceRequirements resources;
    private boolean zookeepernp;
    @Builder.Default
    private ProbeConfig probe = new ProbeConfig();
    @NotNull
    private VolumeConfig dataVolume;
    @Builder.Default
    private ServiceConfig service = new ServiceConfig();

}
