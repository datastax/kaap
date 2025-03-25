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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Identifier;
import java.io.File;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

@Slf4j
public class LocalRegistryContainer<T extends LocalRegistryContainer<T>> extends GenericContainer<T> {

    public LocalRegistryContainer() {
        super("registry:2");
        // workaround because withExposedPorts(5000) does not work
        addFixedExposedPort(nextFreePort(), 5000);
        withEnv("SETTINGS_FLAVOR", "local");
        final String registryCacheLocalPath = "/tmp/kaap-local-k3s-registry-cache";
        new File(registryCacheLocalPath).mkdirs();
        withFileSystemBind(registryCacheLocalPath, "/var/lib/registry");
    }


    public String getRegistryUrl() {
        return "localhost:" + getMappedPort(5000);
    }

    @SneakyThrows
    public CompletableFuture<Void> pushImages(List<String> images) {
        CompletableFuture[] futures = new CompletableFuture[images.size()];
        int i = 0;
        for (String image : images) {
            futures[i++] = pushImage(image);

        }
        return CompletableFuture.allOf(futures);
    }

    @SneakyThrows
    public CompletableFuture<Void> pushImage(String imageName) {
        final DockerClient client = DockerClientFactory.lazyClient();
        return pullImageIfNeeded(imageName, client)
                .thenCompose(ignore -> {
                    final Identifier identifier = Identifier.fromCompoundString(imageName);
                    final String imageNameWithLocalRegistry = getRegistryUrl() + "/" + identifier.repository.name;
                    final String tag = identifier.tag.orElse("latest");
                    final String imageNameWithLocalRegistryAndTag = imageNameWithLocalRegistry + ":" + tag;
                    log.info("tagging image {} to {}", imageName, imageNameWithLocalRegistryAndTag);
                    client.tagImageCmd(imageName, imageNameWithLocalRegistry, tag).exec();
                    log.info("pushing image {} to local registry", imageNameWithLocalRegistryAndTag);

                    return CompletableFuture.runAsync(() -> {
                        long start = System.currentTimeMillis();
                        try {
                            client.pushImageCmd(imageNameWithLocalRegistryAndTag)
                                    .start().awaitCompletion();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        log.info("Pushed image {} to local registry in {} seconds", imageName,
                                (System.currentTimeMillis() - start) / 1000.0);

                    }).thenAccept(ignore2 -> {
                        client.removeImageCmd(imageNameWithLocalRegistryAndTag).exec();
                    });
                });
    }

    private CompletableFuture<Void> pullImageIfNeeded(String imageName, DockerClient client) {
        try {
            dockerClient.inspectImageCmd(imageName).exec();
            return CompletableFuture.completedFuture(null);
        } catch (NotFoundException notFoundException) {
            log.info("image {} not found locally, pulling..", imageName);
            final String[] split = imageName.split(":");
            return CompletableFuture.runAsync(new Runnable() {
                @Override
                @SneakyThrows
                public void run() {

                    client.pullImageCmd(split[0])
                            .withTag(split[1])
                            .start()
                            .awaitCompletion();
                    log.info("image {} pulled", imageName);
                }
            });
        }
    }

    private static synchronized int nextFreePort() {
        int exceptionCount = 0;
        while (true) {
            try (ServerSocket ss = new ServerSocket(0)) {
                return ss.getLocalPort();
            } catch (Exception e) {
                exceptionCount++;
                if (exceptionCount > 100) {
                    throw new RuntimeException("Unable to allocate socket port", e);
                }
            }
        }
    }
}
