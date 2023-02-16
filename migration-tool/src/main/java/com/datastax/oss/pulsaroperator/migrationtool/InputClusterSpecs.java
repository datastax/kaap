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
package com.datastax.oss.pulsaroperator.migrationtool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InputClusterSpecs {

    String context;
    String namespace;
    String clusterName;

    String kubernetesClusterDomain;
    @Builder.Default
    ZooKeeperSpecs zookeeper = new ZooKeeperSpecs();

    @Builder.Default
    BookKeeperSpecs bookkeeper = new BookKeeperSpecs();

    @Builder.Default
    BrokerSpecs broker = new BrokerSpecs();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ZooKeeperSpecs {
        String baseName = "zookeeper";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookKeeperSpecs {
        String baseName = "bookkeeper";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerSpecs {
        String baseName = "broker";
    }
}
