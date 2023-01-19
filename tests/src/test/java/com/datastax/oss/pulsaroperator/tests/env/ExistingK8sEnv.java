package com.datastax.oss.pulsaroperator.tests.env;

import com.dajudge.kindcontainer.helm.Helm3Container;
import io.fabric8.kubernetes.client.Config;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.testcontainers.utility.DockerImageName;

public class ExistingK8sEnv implements K8sEnv {

    public static final DockerImageName HELM_DOCKER_IMAGE = DockerImageName.parse("alpine/helm:3.7.2");
    private static final String KUBECONFIG_CONTEXT =
            System.getProperty("pulsaroperator.tests.existingenv.kubeconfig.context");
    private static final String STORAGECLASS =
            System.getProperty("pulsaroperator.tests.existingenv.storageclass", "default");

    private final Config config;
    private Helm3Container helm3Container;

    @SneakyThrows
    public ExistingK8sEnv() {
        if (KUBECONFIG_CONTEXT == null) {
            // hard parameter in order to ensure the caller has the right environment set
            throw new RuntimeException("pulsaroperator.tests.externalenv.kubeconfig.context not set");
        }
        final String context = KUBECONFIG_CONTEXT.equals("current") ? null : KUBECONFIG_CONTEXT;
        config = Config.autoConfigure(context);
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
    @SneakyThrows
    public Helm3Container withHelmContainer(Consumer<Helm3Container> preInit) {
        if (helm3Container != null) {
            throw new IllegalStateException("Helm container already initialized");
        }
        final Path localKubeConfig = Paths.get(System.getProperty("user.home"), ".kube", "config");
        final String kubeConfigContent = Files.readString(localKubeConfig, StandardCharsets.UTF_8);

        helm3Container = new Helm3Container<>(HELM_DOCKER_IMAGE, () -> kubeConfigContent);
        if (preInit != null) {
            preInit.accept(helm3Container);
        }
        helm3Container.start();
        return helm3Container;
    }

    @Override
    public Helm3Container helmContainer() {
        if (helm3Container == null) {
            return helmContainer();
        }
        return helm3Container;
    }

    @Override
    public void cleanup() {
        if (helm3Container != null) {
            helm3Container.stop();
        }
    }

    @Override
    public void close() {
    }
}
