package com.datastax.oss.pulsaroperator.crds.cluster;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PulsarClusterStatus {
    private PulsarClusterSpec currentSpec;
}
