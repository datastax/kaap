package com.datastax.oss.pulsaroperator.tests;

import com.dajudge.kindcontainer.helm.Helm3Container;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.Container;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "misc")
public class HelmTest extends BasePulsarClusterTest {

    @Test
    public void testInstallWithHelm() throws Exception {
        try {
            helmInstall();
            awaitOperatorRunning();

            final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
            applyPulsarCluster(specsToYaml(specs));
            awaitInstalled();
            helmUninstall();
            client.resources(PulsarCluster.class).inNamespace(namespace)
                    .withName("pulsar-cluster")
                    .delete();
            awaitUninstalled();
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            printAllPodsLogs();
            throw new RuntimeException(t);
        }
    }



    @SneakyThrows
    private void helmInstall() {
        final Path helmHome = Paths.get("..", "helm", "pulsar-operator");


        final Helm3Container helm3Container = env.withHelmContainer((Consumer<Helm3Container>) helm -> {
            helm.withFileSystemBind(helmHome.toFile().getAbsolutePath(), "/helm-pulsar-operator");
        });

        helm3Container.execInContainer("helm", "delete", "test", "-n", namespace);
        final String cmd = "helm install test -n " + namespace + " /helm-pulsar-operator";
        final Container.ExecResult exec = helm3Container.execInContainer(cmd.split(" "));
        if (exec.getExitCode() != 0) {
            throw new RuntimeException("Helm installation failed: " + exec.getStderr());
        }
    }

    @SneakyThrows
    private void helmUninstall() {
        final Helm3Container helm3Container = env.helmContainer();
        final Container.ExecResult exec =
                helm3Container.execInContainer("helm", "delete", "test", "-n", namespace);
        if (exec.getExitCode() != 0) {
            throw new RuntimeException("Helm uninstallation failed: " + exec.getStderr());
        }
    }
}
