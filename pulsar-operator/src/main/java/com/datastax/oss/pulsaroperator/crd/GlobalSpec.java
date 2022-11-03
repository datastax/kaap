package com.datastax.oss.pulsaroperator.crd;

import io.fabric8.kubernetes.api.model.PodDNSConfig;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSpec {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AntiAffinityConfig {
        AntiAffinityHostConfig host = new AntiAffinityHostConfig();
        AntiAffinityZoneConfig zone = new AntiAffinityZoneConfig();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AntiAffinityHostConfig {
        boolean enabled = true;
        String mode = "required";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AntiAffinityZoneConfig {
        boolean enabled = false;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsConfig {
        TlsEntryConfig zookeeper = new TlsEntryConfig();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TlsEntryConfig {
        boolean enabled;
        String tlsSecretName;
    }


    private String name;
    private String fullname;
    private PodDNSConfig dnsConfig;
    private boolean priorityClass;
    private Map<String, String> globalNodeSelectors;
    private boolean enableAntiAffinity;
    private AntiAffinityConfig antiAffinity;
    private boolean enableTls;
    private TlsConfig tls = new TlsConfig();
    private boolean persistence;
    String image;


}
