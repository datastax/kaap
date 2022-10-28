package com.datastax.oss.pulsaroperator.tests;

import com.dajudge.kindcontainer.K3sContainer;
import com.dajudge.kindcontainer.K3sContainerVersion;
import com.dajudge.kindcontainer.KubernetesImageSpec;
import com.dajudge.kindcontainer.exception.ExecutionException;
import com.dajudge.kindcontainer.kubectl.KubectlContainer;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.PullResponseItem;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseK8sEnvironment {

    private static final String OPERATOR_IMAGE = System.getProperty("pulsar.operator.tests.operator.image",
            "datastax/pulsar-operator:latest");

    protected static final String PULSAR_IMAGE = System.getProperty("pulsar.operator.tests.pulsar.image",
            "us-central1-docker.pkg.dev/datastax-gcp-pulsar/pulsar/lunastreaming-core:latest-210");

    private static final boolean DEBUG_LOG_CONTAINER = Boolean
            .getBoolean("pulsar.operator.tests.container.log.debug");

    private static final boolean DEBUG_CONTAINER_KEEP = Boolean
            .parseBoolean(System.getProperty("pulsar.operator.tests.container.keep", "true"));


    protected static final String NAMESPACE = "ns";
    private static final DockerClient hostDockerClient = DockerClientFactory.lazyClient();
    protected ReusableK3sContainer container;
    protected KubernetesClient client;

    private static class ReusableK3sContainer<SELF extends ReusableK3sContainer<SELF>> extends K3sContainer<SELF> {

        Boolean reused;

        public ReusableK3sContainer(KubernetesImageSpec<K3sContainerVersion> imageSpec) {
            super(imageSpec);
            withReuse(true);
        }

        @Override
        protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
            this.reused = reused;
            super.containerIsStarting(containerInfo, reused);
        }
    }

    @SneakyThrows
    private static List<String> getYamlManifests() {
        return Files.list(
                        Path.of(MountableFile
                                .forClasspathResource("/manifests")
                                .getResolvedPath())
                ).filter(p -> p.toFile().getName().endsWith(".yml"))
                .map(p -> "/manifests/" + p.getFileName().toFile().getName())
                .collect(Collectors.toList());
    }

    @BeforeMethod(alwaysRun = true)
    public void before() throws Exception {
        boolean containerWasNull = container == null;
        if (containerWasNull) {
            final KubernetesImageSpec<K3sContainerVersion> k3sImage = new KubernetesImageSpec<>(K3sContainerVersion.VERSION_1_25_0)
                    .withImage("rancher/k3s:v1.25.3-k3s1");
            container = new ReusableK3sContainer(k3sImage);
            createAndMountImageDigest(OPERATOR_IMAGE);
            createAndMountImageDigest(PULSAR_IMAGE);
        }



        container.start();
        final KubectlContainer kubectl = container.kubectl();
        kubectl.followOutput((Consumer<OutputFrame>) outputFrame -> {
            if (DEBUG_LOG_CONTAINER) {
                System.out.println("kubectl > " + outputFrame.getUtf8String());
            }
        });

        if (!container.reused && containerWasNull) {
            restoreDockerImageInK3s(OPERATOR_IMAGE);
            restoreDockerImageInK3s(PULSAR_IMAGE);
            kubectl.create.namespace.run(NAMESPACE);
        } else {
            kubectl.delete.namespace(NAMESPACE).force().ignoreNotFound().run("all", "--all");
        }

        if (containerWasNull) {
            container.followOutput((Consumer<OutputFrame>) outputFrame -> {
                if (DEBUG_LOG_CONTAINER) {
                    System.out.println("k3s > " + outputFrame.getUtf8String());
                }
            });

        }
        applyRBACManifests();
        applyOperatorManifests(kubectl);

        printDebugInfo();
        client = new DefaultKubernetesClient(Config.fromKubeconfig(container.getKubeconfig()));
    }

    private void printDebugInfo() throws IOException {
        File tmpKubeConfig = File.createTempFile( "test-kubeconfig", ".yaml");
        tmpKubeConfig.deleteOnExit();
        Files.write(tmpKubeConfig.toPath(), container.getKubeconfig().getBytes(StandardCharsets.UTF_8));
        System.out.println("export KUBECONFIG="+tmpKubeConfig.getAbsolutePath());
    }

    private void applyOperatorManifests(KubectlContainer kubectl) throws IOException, ExecutionException, InterruptedException {
        for (String yamlManifest : getYamlManifests()) {
            kubectl.copyFileToContainer(MountableFile.forClasspathResource(yamlManifest),
                    yamlManifest);
            kubectl.apply.from(yamlManifest).namespace(NAMESPACE).run();
            log.info("Applied {}", yamlManifest);
        }
    }

    private void applyRBACManifests() {
        kubectlApply("""
                apiVersion: v1
                kind: ServiceAccount
                metadata:
                  name: pulsar-operator
                  namespace: %s
                ---
                apiVersion: rbac.authorization.k8s.io/v1
                kind: ClusterRole
                metadata:
                  name: pulsar-operator-role-admin
                  namespace: %s
                rules:
                  - apiGroups:
                      - apps
                    resources:
                      - deployments
                      - daemonsets
                      - replicasets
                      - statefulsets
                    verbs:
                      - "*"
                  - apiGroups:
                    - ""
                    resources:
                    - pods
                    - configmaps
                    - services
                    verbs:
                    - '*'
                ---
                apiVersion: rbac.authorization.k8s.io/v1
                kind: ClusterRoleBinding
                metadata:
                  name: pulsar-operator-role-admin-binding
                  namespace: %s
                roleRef:
                  kind: ClusterRole
                  apiGroup: rbac.authorization.k8s.io
                  name: pulsar-operator-role-admin
                subjects:
                  - kind: ServiceAccount
                    name: pulsar-operator
                    namespace: %s
                """.formatted(NAMESPACE, NAMESPACE, NAMESPACE, NAMESPACE));
    }

    @SneakyThrows
    private void createAndMountImageDigest(String image) {

        String imageFilename;
        try {
            imageFilename = getMountedImageFilename(hostDockerClient, image);
        } catch (com.github.dockerjava.api.exception.NotFoundException notFoundException) {
            System.out.println("image not found locally, pulling..");
            final String[] split = image.split(":");
            hostDockerClient.pullImageCmd(split[0])
                    .withTag(split[1])
                    .start().awaitCompletion();
            imageFilename = getMountedImageFilename(hostDockerClient, image);
        }

        final Path operatorImageBinPath = Paths.get("target", imageFilename);
        if (operatorImageBinPath.toFile().exists()) {
            System.out.println("Local operator digest already exists, reusing it");

        } else {
            long start = System.currentTimeMillis();
            final InputStream saved = hostDockerClient.saveImageCmd(image).exec();
            Files.copy(saved, operatorImageBinPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Saved local operator docker image in " + (System.currentTimeMillis() - start) + " ms");
        }
        container.withFileSystemBind(operatorImageBinPath.toFile().getAbsolutePath(),"/" + imageFilename);
        // O COPY ? container.copyFileToContainer(Transferable.of(bytes), "/pulsar-bin.bin");*/
    }

    private String getMountedImageFilename(DockerClient dockerClient, String image) {
        final String dockerImageId = dockerClient.inspectImageCmd(image).exec()
                .getId()
                .replace("sha256:", "");

        return "docker-digest-" + dockerImageId + ".bin";
    }

    private void restoreDockerImageInK3s(String imageName) throws IOException, InterruptedException {
        System.out.println("Restoring docker image in k3s {}" + imageName);
        long start = System.currentTimeMillis();
        final String mountedImageFilename = getMountedImageFilename(hostDockerClient, imageName);

        final Container.ExecResult execResult = container.execInContainer("ctr", "-a", "/run/k3s/containerd/containerd.sock",
                "image", "import", mountedImageFilename);
        if (execResult.getExitCode() != 0) {
            throw new RuntimeException("ctr images import failed: " + execResult.getStderr());
        }
        System.out.println("Restored in " + (System.currentTimeMillis() - start));
    }

    @AfterMethod(alwaysRun = true)
    public void after() throws Exception {
        if (!DEBUG_CONTAINER_KEEP && container != null) {
            container.close();
        }
        if (client != null) {
            client.close();
        }
    }

    @SneakyThrows
    protected void kubectlApply(String manifest) {
        final String outputFilename = "/tmp/manifest" + System.nanoTime() + ".yml";
        container.kubectl().copyFileToContainer(Transferable.of(manifest),
                outputFilename);
        container.kubectl().apply.from(outputFilename).namespace(NAMESPACE).run();
    }

}
