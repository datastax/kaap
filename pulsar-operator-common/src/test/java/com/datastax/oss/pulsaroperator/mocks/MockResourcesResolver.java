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
package com.datastax.oss.pulsaroperator.mocks;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.testng.internal.collections.Pair;

@Slf4j
public class MockResourcesResolver {

    private Map<Pair<String, String>, HasMetadata> resources = new HashMap<>();

    private static Pair<String, String> computeKey(HasMetadata resource) {
        return Pair.of(resource.getClass().getName(), resource.getMetadata().getName());
    }

    private static Pair<String, String> computeKey(Class<? extends HasMetadata> cl, String name) {
        return Pair.of(cl.getName(), name);
    }

    private <T extends HasMetadata> T getResourceByName(Class<T> clazz, String name) {
        return (T) resources.get(computeKey(clazz, name));
    }

    public Secret secretWithName(String name) {
        return getResourceByName(Secret.class, name);
    }

    public StatefulSet statefulSetWithName(String name) {
        return getResourceByName(StatefulSet.class, name);
    }

    public PodDisruptionBudget podDisruptionBudgetWithName(String name) {
        return getResourceByName(PodDisruptionBudget.class, name);
    }

    public ConfigMap configMapWithName(String name) {
        return getResourceByName(ConfigMap.class, name);
    }

    public Service serviceWithName(String name) {
        return getResourceByName(Service.class, name);
    }

    public Job jobWithName(String name) {
        return getResourceByName(Job.class, name);
    }

    public Deployment deploymentWithName(String name) {
        return getResourceByName(Deployment.class, name);
    }

    public StatefulSetBuilder newStatefulSetBuilder(String name, boolean ready) {
        return new StatefulSetBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withNewStatus()
                .withReplicas(1)
                .withReadyReplicas(ready ? 1 : 0)
                .endStatus();
    }

    public void importResource(String yaml) {
        final HasMetadata r = Serialization.unmarshal(yaml);
        resources.put(computeKey(r), r);
        log.info("imported resource {}/{}", r.getKind(), r.getMetadata().getName());
    }
}
