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
package com.datastax.oss.kaap.crds.configs;

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
