package com.datastax.oss.pulsaroperator.crds.cluster;

import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PulsarClusterSpec {

    private GlobalSpec global;
    private ZooKeeperSpec zookeeper;

}
