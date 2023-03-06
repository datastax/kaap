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
package com.datastax.oss.pulsaroperator.tests.env.k3s;

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
            new KubernetesImageSpec<>(K3sContainerVersion.VERSION_1_25_0)
                    .withImage("rancher/k3s:v1.25.3-k3s1");

    public abstract void start();

    public abstract GenericContainer getServerContainer();

    public abstract List<GenericContainer> getContainers();

    public abstract String getKubeconfigContent();

    public abstract CompletableFuture<Void> restoreDockerImageFromFile(String imageName, String filename);

    public abstract CompletableFuture<Void> downloadDockerImage(String imageName);

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

    @SneakyThrows
    public static CompletableFuture<Void> downloadDockerImage(String imageName, GenericContainer container) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            @SneakyThrows
            public void run() {
                log.info("Downloading docker image {} in k3s", imageName);
                long start = System.currentTimeMillis();
                final Container.ExecResult execResult;
                try {
                    if (container.execInContainer("ctr", "images", "list").getStdout().contains(imageName)) {
                        log.info("Image {} already exists in the k3s", imageName);
                        return;
                    }
                    execResult = container.execInContainer("ctr", "images", "pull", "docker.io/" + imageName);
                    if (execResult.getExitCode() != 0) {
                        throw new RuntimeException("ctr images download failed: " + execResult.getStderr());
                    }
                    log.info("Downloaded docker image {} in {} ms", imageName, (System.currentTimeMillis() - start));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        });
    }
}
