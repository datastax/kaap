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
package com.datastax.oss.kaap.mocks;

import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.okhttp.OkHttpClientFactory;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.concurrent.atomic.AtomicReference;

public class MockKubeServer extends ExternalResource {

    private AtomicReference<NamespacedKubernetesClient> client = new AtomicReference(null);

    public MockKubeServer() {
        super(false);
    }

    @Override
    public void before() {
        super.before();
        client.set(getKubernetesMockServer().createClient(new OkHttpClientFactory()));
    }

    @Override
    public NamespacedKubernetesClient getClient() {
        return client.get();
    }

    @Override
    public void after() {
        super.after();
        if (client.get() != null) {
            client.get().close();
        }
    }
}
