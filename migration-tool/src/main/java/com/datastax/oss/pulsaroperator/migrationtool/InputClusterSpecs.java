package com.datastax.oss.pulsaroperator.migrationtool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputClusterSpecs {

    String context;
    String namespace;
    String clusterName;

    String kubernetesClusterDomain;
    @Builder.Default
    ZooKeeperSpecs zookeeper = new ZooKeeperSpecs();
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZooKeeperSpecs {
        String baseName = "zookeeper";
    }
}
