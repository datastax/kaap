package com.datastax.oss.pulsaroperator.crd.cluster;

import com.datastax.oss.pulsaroperator.crd.GlobalSpec;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeperSpec;
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
