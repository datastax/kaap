package com.datastax.oss.pulsaroperator.tests.helm;

import com.dajudge.kindcontainer.helm.Helm3Container;
import com.datastax.oss.pulsaroperator.tests.BasePulsarClusterTest;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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

    private static final String HELM_RELEASE_PREFIX = "pothr-";
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
    }


    @AfterMethod(alwaysRun = true)
    public void afterBaseHelmTest() throws Exception {
        try {
            helmUninstall();
        } catch (Exception e) {
            log.warn("Failed to cleanup helm release", e);
        }
    }

    @SneakyThrows
    protected void helmInstall(Chart chart, String values) {
        final Path helmHome;
        if (chart == Chart.OPERATOR) {
            helmHome = Paths.get("..", "helm", "pulsar-operator");
        } else {
            helmHome = Paths.get("..", "helm", "pulsar-stack");
        }
        final Helm3Container helm3Container = env.withHelmContainer((Consumer<Helm3Container>) helm -> {
            helm.withFileSystemBind(helmHome.toFile().getAbsolutePath(), "/helm-pulsar-operator");
        });
        helm3Container.copyFileToContainer(Transferable.of(values), "/test-values.yaml");

        helm3Container.execInContainer("helm", "delete", helmReleaseName, "-n", namespace);
        final String cmd =
                "helm install --debug --timeout 360s %s -n %s /helm-pulsar-operator --values /test-values.yaml".formatted(
                        helmReleaseName, namespace);
        final Container.ExecResult exec = helm3Container.execInContainer(cmd.split(" "));
        if (exec.getExitCode() != 0) {
            throw new RuntimeException("Helm installation failed: " + exec.getStderr());
        }
    }

    @SneakyThrows
    protected void helmUpgrade(String values) {
        env.helmContainer().copyFileToContainer(Transferable.of(values), "/test-values.yaml");
        final String cmd =
                "helm upgrade %s -n %s /helm-pulsar-operator --values /test-values.yaml"
                        .formatted(helmReleaseName, namespace);
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
                .withLabel("app.kubernetes.io/name", "pulsar-operator").list().getItems();
        return pods;
    }
}
