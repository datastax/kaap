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
package com.datastax.oss.kaap.tests.env;

import com.dajudge.kindcontainer.client.KubeConfigUtils;
import com.dajudge.kindcontainer.client.config.Cluster;
import com.dajudge.kindcontainer.client.config.KubeConfig;
import com.dajudge.kindcontainer.helm.Helm3Container;
import io.fabric8.kubernetes.client.Config;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public class ExistingK8sEnv implements K8sEnv {

    public static final DockerImageName HELM_DOCKER_IMAGE = DockerImageName.parse("alpine/helm:3.17.2");
    private static final String KUBECONFIG_CONTEXT =
            System.getProperty("kaap.tests.existingenv.kubeconfig.context");
    private static final String STORAGECLASS =
            System.getProperty("kaap.tests.existingenv.storageclass", "default");
    private static final String KUBECONFIG_OVERRIDE_SERVER =
            System.getProperty("kaap.tests.existingenv.kubeconfig.overrideserver", null);
    private static final String HELM_NETWORK =
            System.getProperty("kaap.tests.existingenv.helmcontainer.network", null);

    private final Config config;
    private Helm3Container helm3Container;

    @SneakyThrows
    public ExistingK8sEnv() {
        if (KUBECONFIG_CONTEXT == null) {
            // hard parameter in order to ensure the caller has the right environment set
            throw new RuntimeException("kaap.tests.externalenv.kubeconfig.context not set");
        }
        final String context = "current".equals(KUBECONFIG_CONTEXT) ? null : KUBECONFIG_CONTEXT;
        config = Config.autoConfigure(context);
    }


    @Override
    public void start() {
    }

    @Override
    public void refreshImages() {
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public String getStorageClass() {
        return STORAGECLASS;
    }

    @Override
    @SneakyThrows
    public Helm3Container withHelmContainer(Consumer<Helm3Container> preInit) {
        if (helm3Container != null) {
            throw new IllegalStateException("Helm container already initialized");
        }

        final String kubeConfigContent;
        final String kubeRawContent;
        final String kubeconfigEnv = System.getenv("KUBECONFIG");

        if (kubeconfigEnv != null) {
            kubeRawContent = Files.readString(Paths.get(kubeconfigEnv), StandardCharsets.UTF_8);
        } else {
            final Path config = Paths.get(System.getProperty("user.home"), ".kube", "config");
            kubeRawContent = Files.readString(config, StandardCharsets.UTF_8);
        }

        if (KUBECONFIG_OVERRIDE_SERVER != null) {
            kubeConfigContent = replaceServerInKubeconfig(KUBECONFIG_OVERRIDE_SERVER, kubeRawContent);
        } else {
            kubeConfigContent = kubeRawContent;
        }

        System.out.println("with helm container " + KUBECONFIG_OVERRIDE_SERVER + " content\n" + kubeConfigContent);

        helm3Container = new Helm3Container<>(HELM_DOCKER_IMAGE, () -> kubeConfigContent);
        if (preInit != null) {
            preInit.accept(helm3Container);
        }
        if (HELM_NETWORK != null) {
            helm3Container.withNetwork(
                    new Network() {
                        @Override
                        public String getId() {
                            return HELM_NETWORK;
                        }

                        @Override
                        public void close() {
                        }

                        @Override
                        public Statement apply(Statement statement, Description description) {
                            return null;
                        }
                    }
            );
        }
        helm3Container.start();
        return helm3Container;
    }

    @Override
    public Helm3Container helmContainer() {
        if (helm3Container == null) {
            return withHelmContainer(null);
        }
        return helm3Container;
    }

    @Override
    public void cleanup() {
        if (helm3Container != null) {
            helm3Container.stop();
            helm3Container = null;
        }
    }

    @Override
    public void close() {
    }

    private String replaceServerInKubeconfig(final String server, final String string) {
        final KubeConfig kubeconfig = KubeConfigUtils.parseKubeConfig(string);
        for (Cluster cluster : kubeconfig.getClusters()) {
            if (cluster.getName().equals(config.getCurrentContext().getName())) {
                cluster.getCluster().setServer(server);
            }
        }
        return KubeConfigUtils.serializeKubeConfig(kubeconfig);
    }
}
