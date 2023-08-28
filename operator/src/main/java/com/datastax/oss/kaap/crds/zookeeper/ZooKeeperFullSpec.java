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
package com.datastax.oss.kaap.crds.zookeeper;

import com.datastax.oss.kaap.crds.FullSpecWithDefaults;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.validation.ValidSpec;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZooKeeperFullSpec implements FullSpecWithDefaults {
    @Valid
    @ValidSpec
    GlobalSpec global;

    @Valid
    @ValidSpec
    ZooKeeperSpec zookeeper;

    @Override
    public GlobalSpec getGlobalSpec() {
        return global;
    }

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (zookeeper == null) {
            zookeeper = new ZooKeeperSpec();
        }
        zookeeper.applyDefaults(globalSpec);
    }
}
