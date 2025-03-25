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
package com.datastax.oss.kaap.tests.env.k3s;

import com.dajudge.kindcontainer.K3sContainerVersion;
import com.dajudge.kindcontainer.KubernetesImageSpec;
import com.dajudge.kindcontainer.helm.Helm3Container;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public abstract class AbstractK3sContainer implements AutoCloseable {

    public static final KubernetesImageSpec<K3sContainerVersion> K3S_IMAGE =
            new KubernetesImageSpec<>(K3sContainerVersion.VERSION_1_31_0)
                    .withImage("rancher/k3s:v1.31.6-k3s1");

    public abstract CompletableFuture<Void> start();

    public abstract LocalRegistryContainer getRegistry();

    public abstract GenericContainer getServerContainer();

    public abstract List<GenericContainer> getContainers();

    public abstract String getKubeconfigContent();

    public abstract CompletableFuture<Void> downloadDockerImages(List<String> images);

    public abstract Helm3Container withHelmContainer(Consumer<Helm3Container> preInit);

    public abstract Helm3Container helm3();

    public abstract void closeHelm3();

    public abstract void close();

    @SneakyThrows
    public static CompletableFuture<Void> restoreDockerImageFromFile(String imageName,
                                                                        final String mountedImageFilename,
                                                                        GenericContainer container) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            @SneakyThrows
            public void run() {
                log.info("Restoring docker image {} in k3s", imageName);
                long start = System.currentTimeMillis();

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

    public CompletableFuture<Void> downloadDockerImages(List<String> images,
                                                        LocalRegistryContainer registryContainer,
                                                        GenericContainer container) {
        CompletableFuture[] futures = new CompletableFuture[images.size()];
        int i = 0;
        for (String image : images) {
            futures[i++] = downloadDockerImage(image, registryContainer, container);

        }
        return CompletableFuture.allOf(futures);
    }

    private CompletableFuture<Void> downloadDockerImage(String imageName, LocalRegistryContainer registryContainer,
                                                        GenericContainer container) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            @SneakyThrows
            public void run() {
                log.info("Downloading docker image {} in k3s ({}-{})", imageName, container.getContainerName(),
                        container.getImage());
                long start = System.currentTimeMillis();
                final Container.ExecResult execResult;
                try {
                    if (container.execInContainer("ctr", "images", "list").getStdout().contains(imageName)) {
                        log.info("Image {} already exists in the k3s", imageName);
                        return;
                    }
                    final String registryIp =
                            registryContainer.getDockerClient().inspectContainerCmd(registryContainer.getContainerId())
                                    .exec()
                                    .getNetworkSettings().getNetworks().values().iterator().next().getIpAddress();

                    final String imageNameWithRegistry = registryIp + ":5000/" + imageName;
                    execResult =
                            container.execInContainer("ctr", "images", "pull", "--plain-http", imageNameWithRegistry);
                    if (execResult.getExitCode() != 0) {
                        throw new RuntimeException("ctr images download failed: " + execResult.getStderr());
                    }
                    container.execInContainer("ctr", "images", "tag", imageNameWithRegistry, "docker.io/" + imageName);
                    container.execInContainer("ctr", "images", "rm", imageNameWithRegistry);
                    log.info("Downloaded docker image {} in {} ms", imageName, (System.currentTimeMillis() - start));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
