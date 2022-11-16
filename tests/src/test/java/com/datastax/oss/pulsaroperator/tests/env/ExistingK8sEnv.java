package com.datastax.oss.pulsaroperator.tests.env;

import com.dajudge.kindcontainer.helm.Helm3Container;
import io.fabric8.kubernetes.client.Config;
import java.util.function.Consumer;
import lombok.SneakyThrows;

public class ExistingK8sEnv implements K8sEnv {

    private static final String KUBECONFIG_CONTEXT =
            System.getProperty("pulsaroperator.tests.existingenv.kubeconfig.context");
    private static final String STORAGECLASS =
            System.getProperty("pulsaroperator.tests.existingenv.storageclass", "default");

    private final Config config;

    @SneakyThrows
    public ExistingK8sEnv() {
        if (KUBECONFIG_CONTEXT == null) {
            // hard parameter in order to ensure the caller has the right environment set
            throw new RuntimeException("pulsaroperator.tests.externalenv.kubeconfig.context not set");
        }
        config = Config.autoConfigure(KUBECONFIG_CONTEXT);
    }


    @Override
    public void start() {
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public String getStorageClass() {
        return STORAGECLASS;
    }

    @Override
    public Helm3Container withHelmContainer(Consumer<Helm3Container> preInit) {
        throw new UnsupportedOperationException("helm not supported");
    }

    @Override
    public Helm3Container helmContainer() {
        throw new UnsupportedOperationException("helm not supported");
    }

    @Override
    public void cleanup() {
    }

    @Override
    public void close() {
    }
}
