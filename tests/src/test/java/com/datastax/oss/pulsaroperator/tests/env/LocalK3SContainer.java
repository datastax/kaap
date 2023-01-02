package com.datastax.oss.pulsaroperator.tests.env;

import com.dajudge.kindcontainer.K3sContainer;
import com.dajudge.kindcontainer.K3sContainerVersion;
import com.dajudge.kindcontainer.KubernetesImageSpec;
import com.dajudge.kindcontainer.helm.Helm3Container;
import com.datastax.oss.pulsaroperator.tests.BaseK8sEnvTest;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import io.fabric8.kubernetes.client.Config;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.utility.DockerImageName;

@Slf4j
public class LocalK3SContainer implements K8sEnv {

    private static final boolean DEBUG_LOG_CONTAINER = Boolean
            .getBoolean("pulsaroperator.tests.container.log.debug");

    private static final DockerClient hostDockerClient = DockerClientFactory.lazyClient();

    public static class ReusableK3sContainer<T extends ReusableK3sContainer<T>> extends K3sContainer<T> {

        Boolean reused;
        private Consumer<Helm3Container> helm3ContainerConsumer;
        private Helm3Container helm3;

        public ReusableK3sContainer(KubernetesImageSpec<K3sContainerVersion> imageSpec) {
            super(imageSpec);
            withReuse(true);
        }

        @Override
        protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
            this.reused = reused;
            super.containerIsStarting(containerInfo, reused);
        }

        @Override
        protected void configure() {
            super.configure();
            withCommand(new String[]{
                    "server",
                    //"--kubelet-arg", "eviction-minimum-reclaim=imagefs.available=2%,nodefs.available=2%",
                    //"--kubelet-arg", "eviction-hard=memory.available<500Mi,nodefs.available<10Gi",
                    "--disable=traefik",
                    "--tls-san=" + this.getHost(),
                    String.format("--service-node-port-range=%d-%d", 30000, 32767)});
        }


        public void beforeStartHelm3(Consumer<Helm3Container> consumer) {
            helm3ContainerConsumer = consumer;
        }

        @Override
        public synchronized Helm3Container<?> helm3() {
            if (this.helm3 == null) {
                this.helm3 = (Helm3Container) (new Helm3Container(DockerImageName.parse("alpine/helm:3.7.2"), this::getInternalKubeconfig))
                        .withNetworkMode("container:" + this.getContainerId());
                if (helm3ContainerConsumer != null) {
                    helm3ContainerConsumer.accept(helm3);
                }
                this.helm3.start();
            }

            return this.helm3;
        }

        @Override
        public void stop() {
            if (helm3 != null) {
                helm3.stop();
            }
            super.stop();
        }
    }

    private ReusableK3sContainer container;

    @Override
    @SneakyThrows
    public void start() {
        boolean containerWasNull = container == null;
        if (containerWasNull) {
            final KubernetesImageSpec<K3sContainerVersion> k3sImage =
                    new KubernetesImageSpec<>(K3sContainerVersion.VERSION_1_25_0)
                            .withImage("rancher/k3s:v1.25.3-k3s1");
            container = new ReusableK3sContainer(k3sImage);
            CompletableFuture.allOf(
                    createAndMountImageDigest(BaseK8sEnvTest.OPERATOR_IMAGE),
                    createAndMountImageDigest(BaseK8sEnvTest.PULSAR_IMAGE)
            ).get();
        }
        container.start();
        printDebugInfo();
        CompletableFuture.allOf(
                restoreDockerImageInK3s(BaseK8sEnvTest.OPERATOR_IMAGE),
                restoreDockerImageInK3s(BaseK8sEnvTest.PULSAR_IMAGE)
        ).get();
        container.followOutput((Consumer<OutputFrame>) outputFrame -> {
            if (DEBUG_LOG_CONTAINER) {
                log.debug("k3s > {}", outputFrame.getUtf8String());
            }
        });

    }

    @Override
    public Config getConfig() {
        return Config.fromKubeconfig(container.getKubeconfig());
    }

    @Override
    public String getStorageClass() {
        return "local-path";
    }

    @Override
    public Helm3Container helmContainer() {
        return container.helm3();
    }

    @Override
    public void cleanup() {
        close();
    }

    @Override
    public void close() {
        if (container != null) {
            container.close();
            container = null;
        }
    }

    @Override
    public Helm3Container withHelmContainer(Consumer<Helm3Container> preInit) {
        container.beforeStartHelm3(preInit);
        return container.helm3();
    }

    private CompletableFuture<Void> createAndMountImageDigest(String image) {
        return createAndMountImageDigest(image, container);
    }

    @SneakyThrows
    protected static CompletableFuture<Void> createAndMountImageDigest(String image, GenericContainer container) {
        return CompletableFuture.runAsync(
                new Runnable() {
                    @Override
                    @SneakyThrows
                    public void run() {

                        String imageFilename;
                        try {
                            imageFilename = getMountedImageFilename(hostDockerClient, image);
                        } catch (com.github.dockerjava.api.exception.NotFoundException notFoundException) {
                            log.info("image {} not found locally, pulling..", image);
                            final String[] split = image.split(":");
                            hostDockerClient.pullImageCmd(split[0])
                                    .withTag(split[1])
                                    .start().awaitCompletion();
                            log.info("image {} pulled", image);
                            imageFilename = getMountedImageFilename(hostDockerClient, image);
                        }

                        Paths.get("target").toFile().mkdir();
                        final Path imageBinPath = Paths.get("target", imageFilename);
                        if (imageBinPath.toFile().exists()) {
                            log.info("Local image {} digest already exists, reusing it", image);
                        } else {
                            long start = System.currentTimeMillis();
                            log.info("Local image {} digest not found in {}, generating", image,
                                    imageBinPath.toFile().getAbsolutePath());
                            final InputStream saved = hostDockerClient.saveImageCmd(image).exec();
                            try {
                                Files.copy(saved, imageBinPath, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            log.info("Local image {} digest generated in {} ms", image,
                                    (System.currentTimeMillis() - start));
                        }
                        container.withFileSystemBind(imageBinPath.toFile().getAbsolutePath(), "/" + imageFilename);
                    }
                });
    }

    public static String getMountedImageFilename(DockerClient dockerClient, String image) {
        final String dockerImageId = dockerClient.inspectImageCmd(image).exec()
                .getId()
                .replace("sha256:", "");

        return "docker-digest-" + dockerImageId + ".bin";
    }

    private CompletableFuture<Void> restoreDockerImageInK3s(String imageName) {
        return restoreDockerImageInK3s(imageName, container);
    }

    @SneakyThrows
    protected static CompletableFuture<Void> restoreDockerImageInK3s(String imageName, GenericContainer container) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            @SneakyThrows
            public void run() {
                log.info("Restoring docker image {} in k3s", imageName);
                long start = System.currentTimeMillis();
                final String mountedImageFilename = getMountedImageFilename(hostDockerClient, imageName);


                final Container.ExecResult execResult;
                try {
                    if (container.execInContainer("ctr", "images", "list").getStdout().contains(imageName)) {
                        log.info("Image {} already exists in the k3s", imageName);
                        return;
                    }
                    execResult = container.execInContainer("ctr", "-a", "/run/k3s/containerd/containerd.sock",
                            "image", "import", mountedImageFilename);
                    if (execResult.getExitCode() != 0) {
                        throw new RuntimeException("ctr images import failed: " + execResult.getStderr());
                    }
                    log.info("Restored docker image {} in {} ms", imageName, (System.currentTimeMillis() - start));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        });
    }

    @SneakyThrows
    public static String getTmpKubeConfig(K3sContainer container) {
        File tmpKubeConfig = Paths.get("/tmp", "pulsaroperator-local-k3s-kube-config").toFile();
        tmpKubeConfig.deleteOnExit();
        Files.writeString(tmpKubeConfig.toPath(), container.getKubeconfig());
        return tmpKubeConfig.getAbsolutePath();
    }

    protected void printDebugInfo() {
        log.info("export KUBECONFIG={}", getTmpKubeConfig(container));
    }

}
