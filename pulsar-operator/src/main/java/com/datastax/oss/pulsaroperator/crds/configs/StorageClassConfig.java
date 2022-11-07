package com.datastax.oss.pulsaroperator.crds.configs;

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
    private String reclaimPolicy;
    private String provisioner;
    private String type;
    private String fsType;
    private Map<String, String> extraParams;

}
