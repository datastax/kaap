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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import com.dajudge.kindcontainer.K3sContainer;
import com.dajudge.kindcontainer.KubernetesImageSpec;
import com.dajudge.kindcontainer.client.TinyK8sClient;
import com.dajudge.kindcontainer.client.model.v1.Node;
import com.dajudge.kindcontainer.client.model.v1.NodeCondition;
import com.dajudge.kindcontainer.helm.Helm3Container;
import com.github.dockerjava.api.command.InspectContainerResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

@Slf4j
public class MultiNodesK3sContainer extends AbstractK3sContainer {

    public static final String TOKEN = "mytoken";

    private final int numAgents;
    private final Network network;
    private final ReusableK3sContainer server;
    private final List<K3sAgentContainer> agents = new ArrayList<>();


    public MultiNodesK3sContainer(int numAgents) {
        this(numAgents, Network.newNetwork());
    }

    public MultiNodesK3sContainer(int numAgents, Network network) {
        this.numAgents = numAgents;
        this.network = network;

        server = new K3sMasterContainer(K3S_IMAGE);
        server.withNetwork(network);

        for (int i = 0; i < numAgents; i++) {
            K3sAgentContainer agent = new K3sAgentContainer(K3S_IMAGE);
            agent.withNetwork(network);
            agents.add(agent);
        }
    }

    @Override
    public GenericContainer getServerContainer() {
        return server;
    }

    @Override
    public List<GenericContainer> getContainers() {
        return allContainers();
    }

    @Override
    public String getKubeconfigContent() {
        return server.getKubeconfig();
    }

    @Override
    public CompletableFuture<Void> restoreDockerImageFromFile(String imageName, String filename) {
        final CompletableFuture[] all =
                allContainers().stream().map(c -> restoreDockerImageFromFile(imageName, filename, c))
                        .collect(Collectors.toList()).toArray(new CompletableFuture[]{});
        return CompletableFuture.allOf(all);
    }

    @Override
    public CompletableFuture<Void> downloadDockerImage(String imageName) {
        final CompletableFuture[] all = allContainers().stream().map(c -> downloadDockerImage(imageName, c))
                .collect(Collectors.toList()).toArray(new CompletableFuture[]{});
        return CompletableFuture.allOf(all);
    }

    @Override
    public Helm3Container withHelmContainer(Consumer<Helm3Container> preInit) {
        server.beforeStartHelm3(preInit);
        return helm3();
    }

    @Override
    public Helm3Container helm3() {
        return server.helm3();
    }

    @Override
    public void closeHelm3() {
        server.stopHelm3();
    }

    public synchronized void start() {
        if (server.getContainerId() != null) {
            return;
        }

        server.start();
        log.info("K3s server started");
        final String ipAddress = server.getDockerClient().inspectContainerCmd(server.getContainerId()).exec()
                .getNetworkSettings().getNetworks().values().iterator().next().getIpAddress();
        final String masterUrl = "https://" + ipAddress + ":6443";
        for (K3sAgentContainer agent : agents) {
            agent.configure(masterUrl, TOKEN, server.getKubeconfig());
            agent.start();
        }
        waitForAgentsReady();
    }


    @Override
    public void close() {
        agents.forEach(K3sContainer::close);
        if (server != null) {
            server.close();
        }
        if (network != null) {
            network.close();
        }
    }

    private void waitForAgentsReady() {
        Awaitility.await("Agents ready")
                .pollInSameThread()
                .pollDelay(0, MILLISECONDS)
                .pollInterval(100, MILLISECONDS)
                .ignoreExceptions()
                .timeout(30, SECONDS)
                .until(this::checkAgentsReady);
    }

    private boolean checkAgentsReady() {
        final Predicate<NodeCondition> isReadyStatus = cond ->
                "Ready".equals(cond.getType()) && "True".equals(cond.getStatus());
        final Predicate<Node> nodeIsReady = node -> node.getStatus().getConditions().stream()
                .anyMatch(isReadyStatus);
        final TinyK8sClient client = TinyK8sClient.fromKubeconfig(server.getKubeconfig());
        try {
            final long readyCount = client.v1().nodes().list().getItems().stream()
                    .filter(nodeIsReady)
                    .count();
            log.info("Found {} ready nodes (including master server)", readyCount);
            if (readyCount == numAgents + 1) {
                return true;
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static class K3sMasterContainer extends ReusableK3sContainer {

        public K3sMasterContainer(KubernetesImageSpec imageSpec) {
            super(imageSpec);
        }

        @Override
        protected void configure() {
            super.configure();
            this.withCommand(
                    "server",
                    "--cluster-init",
                    "--disable=traefik",
                    "--token=mytoken",
                    "--tls-san=" + this.getHost(),
                    String.format("--service-node-port-range=%d-%d", 30000, 32767)
            );
        }
    }

    private static class K3sAgentContainer<T> extends K3sContainer {

        String masterUrl;
        String token;
        String kubeconfig;

        public K3sAgentContainer(KubernetesImageSpec imageSpec) {
            super(imageSpec);
        }

        protected void configure(String masterUrl, String token, String kubeconfig) {
            this.masterUrl = masterUrl;
            this.token = token;
            this.kubeconfig = kubeconfig;
        }

        protected void configure() {
            super.configure();
            withEnv(Map.of(
                    "K3S_URL", masterUrl,
                    "K3S_TOKEN", token
            ));

            withCommand(new String[]{"agent", "--token", token, "--server", masterUrl});
        }

        @Override
        protected void containerIsStarting(final InspectContainerResponse containerInfo, final boolean reused) {
        }
    }

    private List<GenericContainer> allContainers() {
        List<GenericContainer> containers = new ArrayList<>(agents);
        containers.add(server);
        return containers;
    }

}
