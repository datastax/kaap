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
package com.datastax.oss.pulsaroperator;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;

public class MockResourcesResolver {

    public Secret secretWithName(String name) {
        return null;
    }

    public StatefulSet statefulSetWithName(String name) {
        return null;
    }

    protected StatefulSetBuilder baseStatefulSetBuilder(String name, boolean ready) {
        return new StatefulSetBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withNewStatus()
                .withReplicas(1)
                .withReadyReplicas(ready ? 1 : 0)
                .endStatus();
    }

}
