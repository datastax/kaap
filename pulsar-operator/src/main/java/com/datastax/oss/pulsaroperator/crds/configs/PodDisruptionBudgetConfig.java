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
    @JsonPropertyDescription("Indicates if the Pdb policy is enabled for this component.")
    private Boolean enabled;
    @JsonPropertyDescription("Indicates the maxUnavailable property for the Pdb.")
    private Integer maxUnavailable;
}
