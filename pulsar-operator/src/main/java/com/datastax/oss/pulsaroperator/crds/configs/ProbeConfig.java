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
public class ProbeConfig {
    @JsonPropertyDescription("Enables the probe.")
    private Boolean enabled;
    @JsonPropertyDescription("Indicates the timeout (in seconds) for the probe.")
    private Integer timeout;
    @JsonPropertyDescription("Indicates the initial delay (in seconds) for the probe.")
    private Integer initial;
    @JsonPropertyDescription("Indicates the period (in seconds) for the probe.")
    private Integer period;
}
