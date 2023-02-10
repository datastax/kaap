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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseSpecGenerator<T> {
    protected final InputClusterSpecs inputSpecs;
    protected final KubernetesClient client;

    public static final List<String> HELM_ANNOTATIONS = List.of(
            "meta.helm.sh/release-name",
            "meta.helm.sh/release-namespace"
    );

    public static final List<String> HELM_LABELS = List.of(
            "app.kubernetes.io/managed-by",
            "chart",
            "heritage",
            "release"
    );


    public BaseSpecGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        this.inputSpecs = inputSpecs;
        this.client = client;
    }


    public abstract List<HasMetadata> getAllResources();

    public abstract T generateSpec();

    public abstract PodDNSConfig getPodDnsConfig();

    protected PodDisruptionBudget getPodDisruptionBudget(String name) {
        return client.policy()
                .v1()
                .podDisruptionBudget()
                .inNamespace(inputSpecs.getNamespace())
                .withName(name)
                .get();
    }

    protected ConfigMap getConfigMap(String name) {
        return getConfigMap(name, false);
    }

    protected ConfigMap requireConfigMap(String name) {
        return getConfigMap(name, true);
    }

    private ConfigMap getConfigMap(String name, boolean required) {
        final ConfigMap configMap = client.configMaps()
                .inNamespace(inputSpecs.getNamespace())
                .withName(name)
                .get();
        if (required && configMap == null) {
            throw new IllegalStateException("Expected configMap with name " + name + " not found");
        }
        return configMap;
    }


    protected Service getService(String name) {
        return getService(name, false);
    }

    protected Service requireService(String name) {
        return getService(name, true);
    }

    private Service getService(String name, boolean required) {
        final Service service = client.services()
                .inNamespace(inputSpecs.getNamespace())
                .withName(name)
                .get();
        if (required && service == null) {
            throw new IllegalStateException("Expected service with name " + name + " not found");
        }
        return service;
    }

    protected StatefulSet getStatefulSet(String name) {
        return getStatefulSet(name, false);
    }

    protected StatefulSet requireStatefulSet(String name) {
        return getStatefulSet(name, true);
    }

    private StatefulSet getStatefulSet(String name, boolean required) {
        final StatefulSet sts = client.apps()
                .statefulSets()
                .inNamespace(inputSpecs.getNamespace())
                .withName(name)
                .get();
        if (required && sts == null) {
            throw new IllegalStateException("Expected StatefulSet with name " + name + " not found");
        }
        return sts;
    }

    protected Map<String, String> addHelmPolicyAnnotation(Map<String, String> annotations) {
        if (annotations == null) {
            annotations = new HashMap<>();
        } else {
            annotations = new HashMap<>(annotations);
        }
        annotations.put("meta.helm.sh/policy", "keep");
        return annotations;
    }
}
