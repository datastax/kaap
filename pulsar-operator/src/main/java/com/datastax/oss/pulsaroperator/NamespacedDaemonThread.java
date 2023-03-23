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

import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import lombok.Data;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public abstract class NamespacedDaemonThread<T> implements AutoCloseable {

    private final Map<String, NamespaceContext<T>> namespaces = new HashMap<>();
    protected final List<ScheduledFuture<?>> tasks = new ArrayList<>();

    @Data
    private static class NamespaceContext<T> {
        private T current;

        boolean isChanged(T spec) {
            if (current != null
                    && spec != null
                    && Objects.equals(spec, current)) {
                return false;
            }
            return true;
        }
    }

    public void onSpecChange(PulsarClusterSpec clusterSpec, String namespace) {
        final NamespaceContext namespaceContext = namespaces.getOrDefault(namespace,
                new NamespaceContext());
        final T newSpec = getSpec(clusterSpec);
        final boolean isChanged = namespaceContext.isChanged(newSpec);
        if (isChanged) {
            cancelTasks();
            final List<ScheduledFuture<?>> newTasks = specChanged(namespace, newSpec, clusterSpec);
            if (newTasks != null) {
                tasks.addAll(newTasks);
            }
        }
        namespaceContext.setCurrent(newSpec);
        namespaces.put(namespace, namespaceContext);
    }


    protected abstract T getSpec(PulsarClusterSpec clusterSpec);

    protected abstract List<ScheduledFuture<?>> specChanged(String namespace, T newSpec, PulsarClusterSpec clusterSpec);


    public void cancelTasks() {
        tasks.forEach(f -> {
            f.cancel(true);
            try {
                f.get();
            } catch (Throwable ignore) {
            }
        });
        tasks.clear();
    }

    @Override
    public void close() {
        cancelTasks();
    }
}
