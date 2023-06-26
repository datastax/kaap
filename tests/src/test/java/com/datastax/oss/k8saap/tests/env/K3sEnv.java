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
package com.datastax.oss.k8saap.tests.env;

import com.dajudge.kindcontainer.helm.Helm3Container;
import com.datastax.oss.k8saap.tests.BaseK8sEnvTest;
import com.datastax.oss.k8saap.tests.env.k3s.AbstractK3sContainer;
import com.datastax.oss.k8saap.tests.env.k3s.MultiNodesK3sContainer;
import com.datastax.oss.k8saap.tests.env.k3s.SingleServerK3sContainer;
import io.fabric8.kubernetes.client.Config;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.Network;

@Slf4j
public class K3sEnv implements K8sEnv {

    private static final boolean PRELOAD_PULSAR_IMAGE = Boolean
            .parseBoolean(System.getProperty("k8saap.tests.operator.image.k3s.preload.pulsar", "true"));

    private final int numAgents;

    public K3sEnv(int agents) {
        this.numAgents = agents;
    }

    private AbstractK3sContainer container;
    private Network network;

    @Override
    @SneakyThrows
    public void start() {
        boolean containerWasNull = container == null;
        if (containerWasNull) {
            log.info("Creating new K3s container");
            network = Network.newNetwork();
            final List<String> preloadImages = getPreloadImages();
            if (numAgents == 0) {
                container = new SingleServerK3sContainer(network, preloadImages);
            } else {
                container = new MultiNodesK3sContainer(numAgents, network, preloadImages);
            }
        } else {
            log.info("Reusing existing K3s container");
            refreshImages();
        }
        container.start().get();
        printDebugInfo();
    }

    @Override
    @SneakyThrows
    public void refreshImages() {
        final List<String> preloadImages = getPreloadImages();
        container.getRegistry().pushImages(preloadImages)
                .thenCompose(v -> container.downloadDockerImages(preloadImages))
                .get();
    }

    private List<String> getPreloadImages() {
        final List<String> preloadImages = new ArrayList<>();
        preloadImages.add(BaseK8sEnvTest.OPERATOR_IMAGE);
        if (PRELOAD_PULSAR_IMAGE) {
            preloadImages.add(BaseK8sEnvTest.PULSAR_IMAGE);
        }
        return preloadImages;
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
        if (network != null) {
            network.close();
        }
    }

    @Override
    public Helm3Container withHelmContainer(Consumer<Helm3Container> preInit) {
        container.withHelmContainer(preInit);
        return container.helm3();
    }


    @SneakyThrows
    private String getTmpKubeConfig() {
        File tmpKubeConfig = Paths.get("/tmp", "k8saap-local-k3s-kube-config").toFile();
        tmpKubeConfig.deleteOnExit();
        Files.writeString(tmpKubeConfig.toPath(), container.getKubeconfigContent());
        return tmpKubeConfig.getAbsolutePath();
    }

    private void printDebugInfo() {
        log.info("export KUBECONFIG={}", getTmpKubeConfig());
    }

}
