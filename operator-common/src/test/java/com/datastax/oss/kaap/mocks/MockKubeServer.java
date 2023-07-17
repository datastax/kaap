package com.datastax.oss.kaap.mocks;

import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.okhttp.OkHttpClientFactory;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.concurrent.atomic.AtomicReference;

public class MockKubeServer extends KubernetesServer {

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
