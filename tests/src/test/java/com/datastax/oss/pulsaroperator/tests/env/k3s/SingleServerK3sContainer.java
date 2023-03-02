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
import org.testcontainers.containers.GenericContainer;

public class SingleServerK3sContainer extends AbstractK3sContainer {

    private final ReusableK3sContainer container = new ReusableK3sContainer(K3S_IMAGE);

    @Override
    public void start() {
        container.start();
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
    public CompletableFuture<Void> restoreDockerImageFromFile(String imageName, String filename) {
        return restoreDockerImageFromFile(imageName, filename, container);
    }

    @Override
    public CompletableFuture<Void> downloadDockerImage(String imageName) {
        return downloadDockerImage(imageName, container);
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
    }

}
