package com.datastax.oss.pulsaroperator.crd.zookeeper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ZooKeeperStatus {

    private ZooKeeperFullSpec currentSpec;
}
