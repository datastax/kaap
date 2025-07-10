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
package com.datastax.oss.kaap.migrationtool;

import java.util.List;
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

    @Builder.Default
    AutorecoverySpecs autorecovery = new AutorecoverySpecs();

    @Builder.Default
    BastionSpecs bastion = new BastionSpecs();

    @Builder.Default
    ProxySpecs proxy = new ProxySpecs();

    @Builder.Default
    FunctionsWorkerSpecs functionsWorker = new FunctionsWorkerSpecs();
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
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class BookKeeperSetSpecs {
            String name;
            String overrideName;
        }
        String baseName = "bookkeeper";
        List<BookKeeperSetSpecs> bookkeeperSets = List.of();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerSpecs {
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class BrokerSetSpecs {
            String name;
            String overrideName;
        }
        String baseName = "broker";
        List<BrokerSetSpecs> brokerSets = List.of();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutorecoverySpecs {
        String baseName = "autorecovery";
    }
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BastionSpecs {
        String baseName = "bastion";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProxySpecs {

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ProxySetSpecs {
            String name;
            String overrideName;
        }
        String baseName = "proxy";
        String serviceName = "";
        List<ProxySetSpecs> proxySets = List.of();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionsWorkerSpecs {
        String baseName = "function";
    }
}
