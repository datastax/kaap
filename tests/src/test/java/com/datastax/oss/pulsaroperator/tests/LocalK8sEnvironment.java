package com.datastax.oss.pulsaroperator.tests;

import static com.datastax.oss.pulsaroperator.tests.BaseK8sEnvTest.OPERATOR_IMAGE;
import static com.datastax.oss.pulsaroperator.tests.BaseK8sEnvTest.PULSAR_IMAGE;
import com.dajudge.kindcontainer.K3sContainer;
import com.dajudge.kindcontainer.K3sContainerVersion;
import com.dajudge.kindcontainer.KubernetesImageSpec;
import com.datastax.oss.pulsaroperator.tests.env.LocalK3SContainer;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.DockerClientFactory;

@Slf4j
public class LocalK8sEnvironment extends LocalK3SContainer {

    // not needed if you use quarkus dev mode
    private static final boolean DEPLOY_OPERATOR_IMAGE = true;
    // not needed if you use quarkus dev mode
    private static final boolean HELM_MODE = false;

    private static final List<String> PROMETHEUS_OPERATOR_IMAGES = List.of("quay.io/prometheus/prometheus:v2.39.1",
            "quay.io/kiwigrid/k8s-sidecar:1.19.2");

    public static void main(String[] args) throws Exception {
        final KubernetesImageSpec<K3sContainerVersion> k3sImage =
                new KubernetesImageSpec<>(K3sContainerVersion.VERSION_1_25_0)
                        .withImage("rancher/k3s:v1.25.3-k3s1");
        try (final K3sContainer container = new K3sContainer(k3sImage)) {
            final ExecutorService executorService = Executors.newCachedThreadPool();

            container.withCreateContainerCmdModifier(
                    (Consumer<CreateContainerCmd>)
                            createContainerCmd -> createContainerCmd.withName("pulsaroperator-local-k3s"));
            createAndMountImages(executorService, container);

            container.start();
            container.kubectl().create.namespace.run("ns");
            log.info("To see k3s logs: docker logs {}", container.getContainerName());
            log.info("You can now access the K8s cluster, namespace 'ns'.");
            final String tmpKubeConfig = getTmpKubeConfig(container);
            log.info(
                    "Now paste this in a new terminal:\nexport KUBECONFIG={} && kubectl config set-context --current "
                            + "--namespace=ns "
                            + "&& mvn quarkus:dev -pl pulsar-operator", tmpKubeConfig);

            log.info(
                    "To install a sample cluster open another terminal and paste:\nexport KUBECONFIG={} && kubectl "
                            + "config set-context --current --namespace=ns "
                            + "&& kubectl apply -f helm/examples/local-k3s.yaml", tmpKubeConfig);
            log.info("You can even run the integration test using this cluster:\n"
                    + "export KUBECONFIG={} && mvn test -pl tests -Dpulsaroperator.tests.env.existing "
                    + "-Dpulsaroperator.tests.existingenv"
                    + ".kubeconfig.context=default -Dpulsaroperator.tests.existingenv.storageclass=local-path "
                    + "-Dtest='PulsarClusterTest'\n", tmpKubeConfig);


            restoreImages(executorService, container);
            executorService.shutdownNow();
            Thread.sleep(Integer.MAX_VALUE);
        }
    }

    private static void createAndMountImages(final ExecutorService executorService, K3sContainer container) {
        List<CompletableFuture<Void>> all = new ArrayList<>();

        all.add(CompletableFuture.runAsync(() -> createAndMountImageDigest(PULSAR_IMAGE, container), executorService));
        if (DEPLOY_OPERATOR_IMAGE) {
            all.add(CompletableFuture.runAsync(() -> createAndMountImageDigest(OPERATOR_IMAGE, container),
                    executorService));
        }
        if (HELM_MODE) {
            PROMETHEUS_OPERATOR_IMAGES.forEach(i -> {
                all.add(CompletableFuture.runAsync(() -> createAndMountImageDigest(i, container), executorService));
            });
        }
        CompletableFuture.allOf(all.toArray(new CompletableFuture[]{}))
                .exceptionally(throwable -> {
                    throw new RuntimeException(throwable);
                })
                .join();
    }

    private static void restoreImages(final ExecutorService executorService, K3sContainer container) {
        List<CompletableFuture<Void>> all = new ArrayList<>();

        all.add(CompletableFuture.runAsync(() -> restoreDockerImageInK3s(PULSAR_IMAGE, container), executorService));
        if (DEPLOY_OPERATOR_IMAGE) {
            all.add(CompletableFuture.runAsync(() -> restoreDockerImageInK3s(OPERATOR_IMAGE, container),
                    executorService));
        }
        if (HELM_MODE) {
            PROMETHEUS_OPERATOR_IMAGES.forEach(i -> {
                all.add(CompletableFuture.runAsync(() -> restoreDockerImageInK3s(i, container), executorService));
            });
        }
        CompletableFuture.allOf(all.toArray(new CompletableFuture[]{}))
                .exceptionally(throwable -> {
                    throw new RuntimeException(throwable);
                })
                .join();
    }

    public static class GenerateImageDigest {
        @SneakyThrows
        public static void main(String[] args) {
            final Path target = Path.of(args[0]);
            long start = System.currentTimeMillis();
            final DockerClient dockerClient = DockerClientFactory.lazyClient();
            final InputStream saved = dockerClient.saveImageCmd(OPERATOR_IMAGE).exec();
            Files.copy(saved, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Local image {} digest generated in {} ms", OPERATOR_IMAGE, (System.currentTimeMillis() - start));

        }
    }

}
