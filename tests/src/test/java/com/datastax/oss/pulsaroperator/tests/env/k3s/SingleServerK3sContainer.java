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

import com.dajudge.kindcontainer.helm.Helm3Container;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

@Slf4j
public class SingleServerK3sContainer extends AbstractK3sContainer {

    private final Network network;
    private final ReusableK3sContainer container = new ReusableK3sContainer(K3S_IMAGE);
    private final LocalRegistryContainer registryContainer = new LocalRegistryContainer();
    private final List<String> preloadImages;

    public SingleServerK3sContainer(Network network, List<String> preloadImages) {
        this.network = network;
        this.preloadImages = preloadImages;
    }

    @Override
    @SneakyThrows
    public synchronized CompletableFuture<Void> start() {
        if (container.getContainerId() != null) {
            return CompletableFuture.completedFuture(null);
        }
        registryContainer
                .withReuse(true)
                .withNetwork(network)
                .start();
        CompletableFuture<Void> preloadImagesFuture = null;
        if (preloadImages != null) {
            preloadImagesFuture = registryContainer.pushImages(preloadImages);
        }

        container.withNetwork(network).start();
        if (preloadImagesFuture != null) {
            return preloadImagesFuture.
                    thenCompose(v -> downloadDockerImages(preloadImages));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public LocalRegistryContainer getRegistry() {
        return registryContainer;
    }

    @Override
    public CompletableFuture<Void> downloadDockerImages(List<String> images) {
        return downloadDockerImages(images, registryContainer, container);
    }

    @Override
    public GenericContainer getServerContainer() {
        return container;
    }

    @Override
    public List<GenericContainer> getContainers() {
        return List.of(container);
    }

    @Override
    public String getKubeconfigContent() {
        return container.getKubeconfig();
    }


    @Override
    public Helm3Container withHelmContainer(Consumer<Helm3Container> preInit) {
        container.beforeStartHelm3(preInit);
        return helm3();
    }

    @Override
    public Helm3Container helm3() {
        return container.helm3();
    }

    @Override
    public void closeHelm3() {
        container.stopHelm3();
    }

    @Override
    public void close() {
        container.close();
        registryContainer.close();
    }

}
