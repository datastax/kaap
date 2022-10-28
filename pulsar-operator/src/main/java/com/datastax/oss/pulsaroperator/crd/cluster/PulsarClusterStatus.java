package com.datastax.oss.pulsaroperator.crd.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PulsarClusterStatus {
    private PulsarClusterSpec currentSpec;
}
