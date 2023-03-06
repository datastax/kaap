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
import com.datastax.oss.pulsaroperator.tests.env.K3sEnv;
import com.datastax.oss.pulsaroperator.tests.env.k3s.AbstractK3sContainer;
import com.datastax.oss.pulsaroperator.tests.env.k3s.MultiNodesK3sContainer;
import com.datastax.oss.pulsaroperator.tests.env.k3s.SingleServerK3sContainer;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testng.annotations.Test;

@Slf4j
public class LocalK8sEnvironment {

    // not needed if you use quarkus dev mode
    private static final boolean DEPLOY_OPERATOR_IMAGE = true;

    // not needed if you use quarkus dev mode
    private static final boolean DOWNLOAD_PULSAR_IMAGE =
            Boolean.getBoolean("pulsaroperator.tests.container.pulsar.download");

    public static final String NETWORK = "pulsaroperator-local-k3s-network";
    public static final String CONTAINER_NAME = "pulsaroperator-local-k3s";

    @Test
    public void main() throws Exception {
        main(null);
    }

    @Test
    public void mainMulti() throws Exception {
        cleanupOldRuns();
        final Integer agents = Integer.getInteger("pulsaroperator.tests.container.k3s.agents", 1);
        try (
                final Network network = Network.builder()
                        .createNetworkCmdModifier(cmd -> cmd.withName(NETWORK))
                        .build();
                MultiNodesK3sContainer container = new MultiNodesK3sContainer(agents, network)) {
            start(container);
        }
    }

    @Test
    public void updateImage() throws Exception {
        new GenerateImageDigest()
                .main(null);
    }

    public static void main(String[] args) throws Exception {
        cleanupOldRuns();

        try (final Network network = Network.builder()
                .createNetworkCmdModifier(cmd -> cmd.withName(NETWORK))
                .build();
             SingleServerK3sContainer container = new SingleServerK3sContainer()) {
            container.getServerContainer().setNetwork(network);
            start(container);
        }
    }

    private static void start(final AbstractK3sContainer container) throws Exception {
        final GenericContainer server = container.getServerContainer();
        server.withCreateContainerCmdModifier(
                (Consumer<CreateContainerCmd>)
                        createContainerCmd -> createContainerCmd.withName(CONTAINER_NAME));
        createAndMountImages(container);

        container.start();

        ((K3sContainer) server).kubectl().create.namespace.run("ns");
        log.info("To see k3s logs: docker logs {}", server.getContainerName());
        log.info("You can now access the K8s cluster, namespace 'ns'.");
        final Config containerKubeConfig = KubeConfigUtils.parseConfigFromString(container.getKubeconfigContent());
        addClusterToUserKubeConfig(containerKubeConfig);
        log.info("Checkout 'local-k3s-*' scripts at tests/src/test/scripts.");
        restoreImages(container);
        Thread.sleep(Integer.MAX_VALUE);
    }

    @SneakyThrows
    private static void addClusterToUserKubeConfig(Config containerKubeConfig) {
        final File defaultKubeConfigPath = Paths.get(System.getProperty("user.home"), ".kube", "config")
                .toFile();
        final Config currentKubeConfig;
        if (defaultKubeConfigPath.exists()) {
            currentKubeConfig = KubeConfigUtils.parseConfig(defaultKubeConfigPath);
        } else {
            defaultKubeConfigPath.createNewFile();
            currentKubeConfig = new Config();
        }

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
            if (user.getName().equals(clusterName)) {
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

    private static void createAndMountImages(AbstractK3sContainer container) {
        List<CompletableFuture<Void>> all = new ArrayList<>();

        if (!DOWNLOAD_PULSAR_IMAGE) {
            all.add(K3sEnv.createAndMountImageDigest(PULSAR_IMAGE, container.getContainers()));
        }
        if (DEPLOY_OPERATOR_IMAGE) {
            all.add(K3sEnv.createAndMountImageDigest(OPERATOR_IMAGE, container.getContainers()));
        }
        CompletableFuture.allOf(all.toArray(new CompletableFuture[]{}))
                .exceptionally(throwable -> {
                    throw new RuntimeException(throwable);
                })
                .join();
    }

    private static void restoreImages(AbstractK3sContainer container) {
        List<CompletableFuture<Void>> all = new ArrayList<>();

        if (!DOWNLOAD_PULSAR_IMAGE) {
            all.add(restoreDockerImage(PULSAR_IMAGE, container));
        } else {
            all.add(container.downloadDockerImage(PULSAR_IMAGE));
        }
        if (DEPLOY_OPERATOR_IMAGE) {
            all.add(restoreDockerImage(OPERATOR_IMAGE, container));
        }
        CompletableFuture.allOf(all.toArray(new CompletableFuture[]{}))
                .exceptionally(throwable -> {
                    throw new RuntimeException(throwable);
                })
                .join();
    }

    private static CompletableFuture<Void> restoreDockerImage(String image, AbstractK3sContainer container) {
        return container.restoreDockerImageFromFile(image,
                K3sEnv.getMountedImageFilename(DockerClientFactory.lazyClient(), image));
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
