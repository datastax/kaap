package com.datastax.oss.pulsaroperator.crds.configs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PodDisruptionBudgetConfig {
    private Boolean enabled;
    private Integer maxUnavailable;
}
