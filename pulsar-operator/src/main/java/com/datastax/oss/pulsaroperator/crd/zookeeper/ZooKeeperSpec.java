package com.datastax.oss.pulsaroperator.crd.zookeeper;

import com.datastax.oss.pulsaroperator.crd.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZooKeeperSpec {

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
        private String name;
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
        private List<ServicePort> ports;

    }

    @Builder.Default
    private String component = "zookeeper";
    @Builder.Default
    private int replicas = 1;
    private Map<String, String> config;
    @Builder.Default
    private String podManagementPolicy = "Parallel";
    private String containerImage;
    @Builder.Default
    private String imagePullPolicy = "IfNotPresent";
    private StatefulSetUpdateStrategy updateStrategy;
    private Map<String, String> annotations;
    private Map<String, String> nodeSelectors;
    private List<Toleration> tolerations;
    private NodeAffinity nodeAffinity;
    private PodAntiAffinity podAntiAffinity;
    private int gracePeriod;
    private ResourceRequirements resources;
    private boolean zookeepernp;
    @Builder.Default
    private ProbeConfig probe = new ProbeConfig();
    private VolumeConfig dataVolume;
    @Builder.Default
    private ServiceConfig service = new ServiceConfig();

}
