package com.datastax.oss.pulsaroperator.crds.configs;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageClassConfig {
    @JsonPropertyDescription("Indicates the reclaimPolicy property for the StorageClass.")
    private String reclaimPolicy;
    @JsonPropertyDescription("Indicates the provisioner property for the StorageClass.")
    private String provisioner;
    @JsonPropertyDescription("Indicates the 'type' parameter for the StorageClass.")
    private String type;
    @JsonPropertyDescription("Indicates the 'fsType' parameter for the StorageClass.")
    private String fsType;
    @JsonPropertyDescription("Adds extra parameters for the StorageClass.")
    private Map<String, String> extraParams;

}
