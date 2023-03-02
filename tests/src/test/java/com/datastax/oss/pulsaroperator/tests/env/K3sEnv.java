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
package com.datastax.oss.pulsaroperator.tests.env;

import com.dajudge.kindcontainer.helm.Helm3Container;
import com.datastax.oss.pulsaroperator.tests.BaseK8sEnvTest;
import com.datastax.oss.pulsaroperator.tests.env.k3s.AbstractK3sContainer;
import com.datastax.oss.pulsaroperator.tests.env.k3s.MultiNodesK3sContainer;
import com.datastax.oss.pulsaroperator.tests.env.k3s.SingleServerK3sContainer;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import io.fabric8.kubernetes.client.Config;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public class K3sEnv implements K8sEnv {

    private static final boolean DOWNLOAD_PULSAR_IMAGE = Boolean
            .getBoolean("pulsaroperator.tests.container.images.pulsar.download");

    private static final DockerClient hostDockerClient = DockerClientFactory.lazyClient();

    private final int numAgents;

    public K3sEnv(int agents) {
        this.numAgents = agents;
    }

    private AbstractK3sContainer container;

    @Override
    @SneakyThrows
    public void start() {
        boolean containerWasNull = container == null;
        if (containerWasNull) {
            log.info("Creating new K3s container");
            if (numAgents == 0) {
                container = new SingleServerK3sContainer();
            } else {
                container = new MultiNodesK3sContainer(numAgents);
            }
            container = new SingleServerK3sContainer();
            if (DOWNLOAD_PULSAR_IMAGE) {
                CompletableFuture.allOf(
                        createAndMountImageDigest(BaseK8sEnvTest.OPERATOR_IMAGE)
                ).get();
            } else {
                CompletableFuture.allOf(
                        createAndMountImageDigest(BaseK8sEnvTest.OPERATOR_IMAGE),
                        createAndMountImageDigest(BaseK8sEnvTest.PULSAR_IMAGE)
                ).get();
            }


        } else {
            log.info("Reusing existing K3s container");
        }
        container.start();
        printDebugInfo();
        if (DOWNLOAD_PULSAR_IMAGE) {
            CompletableFuture.allOf(
                    restoreDockerImageInK3s(BaseK8sEnvTest.OPERATOR_IMAGE),
                    container.downloadDockerImage(BaseK8sEnvTest.PULSAR_IMAGE)
            ).get();

        } else {
            CompletableFuture.allOf(
                    restoreDockerImageInK3s(BaseK8sEnvTest.OPERATOR_IMAGE),
                    restoreDockerImageInK3s(BaseK8sEnvTest.PULSAR_IMAGE)
            ).get();
        }
    }

    @Override
    public Config getConfig() {
        return Config.fromKubeconfig(container.getKubeconfigContent());
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
        container.closeHelm3();
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
        container.withHelmContainer(preInit);
        return container.helm3();
    }


    private CompletableFuture<Void> restoreDockerImageInK3s(String imageName) {
        return container.restoreDockerImageFromFile(imageName, getMountedImageFilename(hostDockerClient, imageName));
    }


    private CompletableFuture<Void> createAndMountImageDigest(String image) {
        return createAndMountImageDigest(image, container.getContainers());

    }

    @SneakyThrows
    public static CompletableFuture<Void> createAndMountImageDigest(String image,
                                                                       List<GenericContainer> containers) {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        final DockerClient hostDockerClient = containers.get(0).getDockerClient();

                        String imageFilename;
                        try {
                            imageFilename = getMountedImageFilename(hostDockerClient, image);
                        } catch (NotFoundException notFoundException) {
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
                            try (final InputStream saved = hostDockerClient.saveImageCmd(image).exec();) {
                                writeImage(saved, imageBinPath);
                            }
                            log.info("Local image {} digest generated in {} ms", image,
                                    (System.currentTimeMillis() - start));
                        }
                        return Pair.of(imageBinPath.toFile().getAbsolutePath(), "/" + imageFilename);
                    } catch (Throwable ex) {
                        throw new RuntimeException(ex);
                    }

                })
                .thenAccept(info -> {
                    for (GenericContainer container : containers) {
                        container.withFileSystemBind(info.getLeft(), info.getRight());
                    }
                });
    }

    @SneakyThrows
    private static void writeImage(InputStream image, Path target) {
        Files.copy(image, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static String getMountedImageFilename(DockerClient dockerClient, String image) {
        final String dockerImageId = dockerClient.inspectImageCmd(image).exec()
                .getId()
                .replace("sha256:", "");

        return "docker-digest-" + dockerImageId + ".bin";
    }

    @SneakyThrows
    private String getTmpKubeConfig() {
        File tmpKubeConfig = Paths.get("/tmp", "pulsaroperator-local-k3s-kube-config").toFile();
        tmpKubeConfig.deleteOnExit();
        Files.writeString(tmpKubeConfig.toPath(), container.getKubeconfigContent());
        return tmpKubeConfig.getAbsolutePath();
    }

    private void printDebugInfo() {
        log.info("export KUBECONFIG={}", getTmpKubeConfig());
    }

}
