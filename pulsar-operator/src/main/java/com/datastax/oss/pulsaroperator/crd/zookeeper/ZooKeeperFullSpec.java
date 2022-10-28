package com.datastax.oss.pulsaroperator.crd.zookeeper;

import com.datastax.oss.pulsaroperator.crd.GlobalSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZooKeeperFullSpec {
    GlobalSpec global;
    ZooKeeperSpec zookeeper;
}
