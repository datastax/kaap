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
package com.datastax.oss.pulsaroperator.tests;

import static com.datastax.oss.pulsaroperator.tests.BaseK8sEnvTest.OPERATOR_IMAGE;
import static com.datastax.oss.pulsaroperator.tests.BaseK8sEnvTest.PULSAR_IMAGE;
import com.dajudge.kindcontainer.K3sContainer;
import com.dajudge.kindcontainer.K3sContainerVersion;
import com.dajudge.kindcontainer.KubernetesImageSpec;
import com.datastax.oss.pulsaroperator.tests.env.LocalK3SContainer;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import io.fabric8.kubernetes.api.model.Config;
import io.fabric8.kubernetes.api.model.NamedAuthInfo;
import io.fabric8.kubernetes.api.model.NamedAuthInfoBuilder;
import io.fabric8.kubernetes.api.model.NamedCluster;
import io.fabric8.kubernetes.api.model.NamedClusterBuilder;
import io.fabric8.kubernetes.api.model.NamedContext;
import io.fabric8.kubernetes.api.model.NamedContextBuilder;
import io.fabric8.kubernetes.client.internal.KubeConfigUtils;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testng.annotations.Test;

@Slf4j
public class LocalK8sEnvironment extends LocalK3SContainer {

    // not needed if you use quarkus dev mode
    private static final boolean DEPLOY_OPERATOR_IMAGE = true;

    // not needed if you use quarkus dev mode
    private static final boolean DEPLOY_PULSAR_IMAGE = false;

    public static final String NETWORK = "pulsaroperator-local-k3s-network";
    public static final String CONTAINER_NAME = "pulsaroperator-local-k3s";

    @Test
    public void main() throws Exception {
        main(null);
    }

    @Test
    public void updateImage() throws Exception {
        new GenerateImageDigest()
                .main(null);
    }

    public static void main(String[] args) throws Exception {
        final KubernetesImageSpec<K3sContainerVersion> k3sImage =
                new KubernetesImageSpec<>(K3sContainerVersion.VERSION_1_25_0)
                        .withImage("rancher/k3s:v1.25.3-k3s1");


        cleanupOldRuns();

        @Cleanup final Network network = Network.builder()
                .createNetworkCmdModifier(cmd -> cmd.withName(NETWORK))
                .build();
        try (final K3sContainer container = new K3sContainer(k3sImage)) {
            container.withCreateContainerCmdModifier(
                    (Consumer<CreateContainerCmd>)
                            createContainerCmd -> createContainerCmd.withName(CONTAINER_NAME));
            container.withNetwork(network);
            createAndMountImages(container);

            container.start();
            container.kubectl().create.namespace.run("ns");
            log.info("To see k3s logs: docker logs {}", container.getContainerName());
            log.info("You can now access the K8s cluster, namespace 'ns'.");
            final String tmpKubeConfig = getTmpKubeConfig(container);
            final Config containerKubeConfig = KubeConfigUtils.parseConfigFromString(container.getKubeconfig());
            addClusterToUserKubeConfig(containerKubeConfig);
            log.info("Checkout 'local-k3s-*' scripts at tests/src/test/scripts.");
            restoreImages(container);
            Thread.sleep(Integer.MAX_VALUE);
        }
    }

    @SneakyThrows
    private static void addClusterToUserKubeConfig(Config containerKubeConfig) {
        final File defaultKubeConfigPath = Paths.get(System.getProperty("user.home"), ".kube", "config")
                .toFile();
        final Config currentKubeConfig = KubeConfigUtils.parseConfig(defaultKubeConfigPath);

        final String clusterName = CONTAINER_NAME;
        boolean foundCluster = false;
        for (NamedCluster cluster : currentKubeConfig.getClusters()) {
            if (cluster.getName().equals(clusterName)) {
                cluster.setCluster(containerKubeConfig.getClusters().get(0).getCluster());
                foundCluster = true;
                break;
            }
        }
        if (!foundCluster) {
            currentKubeConfig.getClusters().add(new NamedClusterBuilder()
                    .withName(clusterName)
                    .withCluster(containerKubeConfig.getClusters().get(0).getCluster())
                    .build());

        }
        boolean foundContext = false;
        for (NamedContext context : currentKubeConfig.getContexts()) {
            if (context.getName().equals(clusterName)) {
                foundContext = true;
            }
        }
        if (!foundContext) {
            currentKubeConfig.getContexts().add(new NamedContextBuilder()
                    .withName(clusterName)
                    .withNewContext()
                    .withCluster(clusterName)
                    .withNamespace("ns")
                    .withUser(clusterName)
                    .endContext()
                    .build()
            );
        }
        boolean foundUser = false;
        for (NamedAuthInfo user : currentKubeConfig.getUsers()) {
            if (user.equals(clusterName)) {
                foundUser = true;
                user.setUser(containerKubeConfig.getUsers().get(0).getUser());
                break;
            }
        }
        if (!foundUser) {
            currentKubeConfig.getUsers().add(new NamedAuthInfoBuilder()
                    .withName(clusterName)
                    .withUser(containerKubeConfig.getUsers().get(0).getUser())
                    .build());
        }
        currentKubeConfig.setCurrentContext(clusterName);
        KubeConfigUtils.persistKubeConfigIntoFile(currentKubeConfig, defaultKubeConfigPath.getAbsolutePath());
        log.info("Persisted new cluster {} to {}", clusterName, defaultKubeConfigPath.getAbsolutePath());
        log.info("Persisted new context {} to {}", clusterName, defaultKubeConfigPath.getAbsolutePath());
        log.info("Persisted new user {} to {}", clusterName, defaultKubeConfigPath.getAbsolutePath());
        log.info("Persisted current context {} to {}", clusterName, defaultKubeConfigPath.getAbsolutePath());
    }

    private static void cleanupOldRuns() {
        final DockerClient client = DockerClientFactory.lazyClient();
        client.listContainersCmd()
                .exec()
                .stream()
                .filter(c -> c.getNetworkSettings().getNetworks().containsKey(NETWORK))
                .forEach(c -> cleanupContainer(c.getId(), client));
        cleanupContainer(CONTAINER_NAME, client);
        try {
            client.removeNetworkCmd(NETWORK).exec();
        } catch (NotFoundException notFoundException) {
            log.info(notFoundException.getMessage());
        }
    }

    private static void cleanupContainer(String name, DockerClient client) {
        try {
            client.removeContainerCmd(name).withForce(true).withRemoveVolumes(true).exec();
        } catch (NotFoundException notFoundException) {
            log.info(notFoundException.getMessage());
        }
    }

    private static void createAndMountImages(K3sContainer container) {
        List<CompletableFuture<Void>> all = new ArrayList<>();

        if (DEPLOY_PULSAR_IMAGE) {
            all.add(createAndMountImageDigest(PULSAR_IMAGE, container));
        }
        if (DEPLOY_OPERATOR_IMAGE) {
            all.add(createAndMountImageDigest(OPERATOR_IMAGE, container));
        }
        CompletableFuture.allOf(all.toArray(new CompletableFuture[]{}))
                .exceptionally(throwable -> {
                    throw new RuntimeException(throwable);
                })
                .join();
    }

    private static void restoreImages(K3sContainer container) {
        List<CompletableFuture<Void>> all = new ArrayList<>();

        if (DEPLOY_PULSAR_IMAGE) {
            all.add(restoreDockerImageInK3s(PULSAR_IMAGE, container));
        } else {
            all.add(downloadDockerImageInK3s(PULSAR_IMAGE, container));
        }
        if (DEPLOY_OPERATOR_IMAGE) {
            all.add(restoreDockerImageInK3s(OPERATOR_IMAGE, container));
        }
        CompletableFuture.allOf(all.toArray(new CompletableFuture[]{}))
                .exceptionally(throwable -> {
                    throw new RuntimeException(throwable);
                })
                .join();
    }


    @SneakyThrows
    protected static CompletableFuture<Void> downloadDockerImageInK3s(String imageName, GenericContainer container) {
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


    public static class GenerateImageDigest {

        public static final String GENERATE_IMAGE_DIGEST_TARGET = System.getenv("GENERATE_IMAGE_DIGEST_TARGET");

        @SneakyThrows
        @Test
        public static void main(String[] args) {
            final Path target = Path.of(GENERATE_IMAGE_DIGEST_TARGET);
            long start = System.currentTimeMillis();
            final DockerClient dockerClient = DockerClientFactory.lazyClient();
            final InputStream saved = dockerClient.saveImageCmd(OPERATOR_IMAGE).exec();
            Files.copy(saved, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Local image {} digest generated in {} ms", OPERATOR_IMAGE, (System.currentTimeMillis() - start));

        }
    }

}
