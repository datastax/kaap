package com.datastax.oss.pulsaroperator.crds.configs;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PodDisruptionBudgetConfig {
    @JsonPropertyDescription("Enable Pdb policy.")
    private Boolean enabled;
    @JsonPropertyDescription("Number of maxUnavailable pods.")
    private Integer maxUnavailable;
}
