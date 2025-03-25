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
package com.datastax.oss.kaap.tests.helm;

import com.dajudge.kindcontainer.helm.Helm3Container;
import com.datastax.oss.kaap.tests.BasePulsarClusterTest;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.testcontainers.containers.Container;
import org.testcontainers.images.builder.Transferable;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

@Slf4j
public class BaseHelmTest extends BasePulsarClusterTest {

    public static final String CERT_MANAGER_CRDS =
            "https://github.com/cert-manager/cert-manager/releases/download/v1.11.0/cert-manager.crds.yaml";

    private static final String HELM_RELEASE_PREFIX = "pothr-";
    public static final String HELM_BIND_OPERATOR = "/helm-kaap";
    public static final String HELM_BIND_DEPENDENCY = "/kaap";
    public static final String HELM_BIND_STACK = "/helm-kaap-stack";

    protected String helmReleaseName;

    public enum Chart {
        OPERATOR,
        STACK;
    }

    @BeforeMethod(alwaysRun = true)
    public void beforeBaseHelmTest() throws Exception {
        helmReleaseName = HELM_RELEASE_PREFIX
                + namespace + "-r-" + RandomStringUtils.randomAlphabetic(8).toLowerCase();
        cleanupNonNamespaceableResources();
        env.withHelmContainer(helm -> {
            final Path operatorPath = OPERATOR_CHART_PATH;
            final Path stackPath = Paths.get("..", "helm", "kaap-stack");
            helm.withFileSystemBind(operatorPath.toFile().getAbsolutePath(), HELM_BIND_OPERATOR);
            helm.withFileSystemBind(stackPath.toFile().getAbsolutePath(), HELM_BIND_STACK);
            helm.withFileSystemBind(operatorPath.toFile().getAbsolutePath(), HELM_BIND_DEPENDENCY);
            log.info("loaded helm charts from {} and {}", operatorPath, stackPath);
        });
    }


    @AfterMethod(alwaysRun = true)
    public void afterBaseHelmTest() throws Exception {
        try {
            helmUninstall();
        } catch (Exception e) {
            log.warn("Failed to cleanup helm release", e);
        }
        try (final InputStream in = new URL(CERT_MANAGER_CRDS)
                .openStream();) {
            deleteManifest(in.readAllBytes());
        } catch (Exception e) {
            log.warn("Failed to remove cert-manager crds", e);
        }
    }

    @SneakyThrows
    protected void helmInstall(Chart chart, String values) {

        final Helm3Container helm3Container = env.helmContainer();
        helm3Container.copyFileToContainer(Transferable.of(values), "/test-values.yaml");

        helm3Container.execInContainer("helm", "delete", helmReleaseName, "-n", namespace);

        // Fix Permissions for kubeconfig issues, no need for check as it can fail or succeed.
        helm3Container.execInContainer("chmod go-r /tmp/kindcontainer.kubeconfig".split(" "));

        final String buildChart =
            "helm dependency update %s".formatted(getChartPath(chart));
        final Container.ExecResult setup = helm3Container.execInContainer(buildChart.split(" "));
        if (setup.getExitCode() != 0) {
            throw new RuntimeException("Helm Dependency build failed: " + setup.getStderr());
        }
        final String cmd =
                "helm install --debug --timeout 900s %s -n %s %s --values /test-values.yaml".formatted(
                        helmReleaseName, namespace, getChartPath(chart));
        final Container.ExecResult exec = helm3Container.execInContainer(cmd.split(" "));
        if (exec.getExitCode() != 0) {
            throw new RuntimeException("Helm installation failed: " + exec.getStderr());
        }
    }

    private static String getChartPath(Chart chart) {
        if (chart == Chart.OPERATOR) {
            return HELM_BIND_OPERATOR;
        } else {
            return HELM_BIND_STACK;
        }
    }

    @SneakyThrows
    protected void helmUpgrade(Chart chart, String values) {
        env.helmContainer().copyFileToContainer(Transferable.of(values), "/test-values.yaml");
        final String cmd =
                "helm upgrade --timeout 900s %s -n %s %s --values /test-values.yaml"
                        .formatted(helmReleaseName, namespace, getChartPath(chart));
        final Container.ExecResult exec = env.helmContainer().execInContainer(cmd.split(" "));
        if (exec.getExitCode() != 0) {
            throw new RuntimeException("Helm upgrade failed: " + exec.getStderr());
        }

    }

    @SneakyThrows
    protected void helmUninstall() {
        final Helm3Container helm3Container = env.helmContainer();
        try {
            final Container.ExecResult exec =
                    helm3Container.execInContainer("helm", "delete", helmReleaseName, "-n", namespace);
            if (exec.getExitCode() != 0) {
                throw new RuntimeException("Helm uninstallation failed: " + exec.getStderr());
            }
        } finally {
            cleanupNonNamespaceableResources();
        }

    }

    private void cleanupNonNamespaceableResources() {
        final Map<String, String> labels = Map.of(
                "app.kubernetes.io/name", "Helm"
        );
        cleanupResource(client.rbac()
                .clusterRoleBindings()
                .withLabels(labels)
                .list().getItems());
        cleanupResource(client.rbac()
                .clusterRoles()
                .withLabels(labels).list()
                .getItems());

        cleanupResource(client.admissionRegistration()
                .v1()
                .mutatingWebhookConfigurations()
                .withLabels(labels)
                .list().getItems());

        cleanupResource(client.admissionRegistration()
                .v1()
                .validatingWebhookConfigurations()
                .withLabels(labels).list().getItems());
    }

    private void cleanupResource(List<? extends HasMetadata> deletable) {
        for (HasMetadata hasMetadata : deletable.stream()
                .filter(resource ->
                        resource.getMetadata().getLabels()
                                .get("app.kubernetes.io/instance").startsWith(HELM_RELEASE_PREFIX))
                .collect(Collectors.toList())) {
            try {
                client.resource(hasMetadata).delete();
            } catch (Exception e) {
                log.warn("Failed to cleanup resource", e);
            }
        }
    }

    protected List<Pod> getOperatorPods() {
        final List<Pod> pods = client.pods()
                .inNamespace(namespace)
                .withLabel("app.kubernetes.io/name", "kaap").list().getItems();
        return pods;
    }


    protected void applyCertManagerCRDs() throws IOException {
        try (final InputStream in = new URL(CERT_MANAGER_CRDS)
                .openStream();) {
            applyManifest(in.readAllBytes());
        }
    }
}
