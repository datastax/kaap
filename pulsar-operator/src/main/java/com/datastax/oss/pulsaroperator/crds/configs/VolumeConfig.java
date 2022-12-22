package com.datastax.oss.pulsaroperator.crds.configs;

import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeConfig {
    @JsonPropertyDescription("Indicates the suffix for the volume. Default value is 'data'.")
    private String name;
    @JsonPropertyDescription("Indicates the requested size for the volume. The format follows the Kubernetes' "
            + "Quantity.")
    private String size;
    @JsonPropertyDescription("Indicates if a StorageClass is used. The operator will create the StorageClass if "
            + "needed.")
    private StorageClassConfig storageClass;
    @JsonPropertyDescription("Indicates if an already existing storage class should be used.")
    private String existingStorageClassName;


    public void mergeVolumeConfigWithGlobal(GlobalSpec.GlobalStorageConfig globalStorageConfig) {
        if (globalStorageConfig != null) {
            if (getExistingStorageClassName() == null && getStorageClass() == null) {
                if (globalStorageConfig.getExistingStorageClassName() != null) {
                    setExistingStorageClassName(globalStorageConfig.getExistingStorageClassName());
                } else if (globalStorageConfig.getStorageClass() != null) {
                    setStorageClass(globalStorageConfig.getStorageClass());
                }
            }
        }
    }

    public void merge(VolumeConfig defaultValue) {
        name = ObjectUtils.firstNonNull(name, defaultValue.getName());
        size = ObjectUtils.firstNonNull(size, defaultValue.getSize());
        storageClass = ObjectUtils.firstNonNull(storageClass, defaultValue.getStorageClass());
        existingStorageClassName = ObjectUtils.firstNonNull(existingStorageClassName, defaultValue.getExistingStorageClassName());
    }
}
