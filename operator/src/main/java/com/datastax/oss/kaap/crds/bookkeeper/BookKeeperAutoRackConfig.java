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
package com.datastax.oss.kaap.crds.bookkeeper;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import io.fabric8.crd.generator.annotation.SchemaFrom;
import io.fabric8.generator.annotation.Min;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookKeeperAutoRackConfig {

    @JsonPropertyDescription("Enable rack configuration monitoring.")
    Boolean enabled;
    @Min(1000)
    @javax.validation.constraints.Min(1000)
    @JsonPropertyDescription("Period for the schedule of the monitoring thread.")
    Long periodMs;
    @JsonPropertyDescription("Additional configuration for the zookeeper client.")
    @SchemaFrom(type = JsonNode.class)
    Map<String, String> additionalZookeeperClientConfig;

}
