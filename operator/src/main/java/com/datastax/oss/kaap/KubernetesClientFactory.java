package com.datastax.oss.kaap;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.okhttp.OkHttpClientFactory;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class KubernetesClientFactory {
    @Produces
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder()
                .withHttpClientFactory(new OkHttpClientFactory())
                .build();
    }
}
