/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.k8saap.crds.configs;

import com.datastax.oss.k8saap.crds.GlobalSpec;
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
