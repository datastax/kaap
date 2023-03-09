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

import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.tests.env.ExistingK8sEnv;
import com.datastax.oss.pulsaroperator.tests.env.K3sEnv;
import com.datastax.oss.pulsaroperator.tests.env.K8sEnv;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.testcontainers.utility.MountableFile;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

@Slf4j
public abstract class BaseK8sEnvTest {

    public static final Path PULSAR_OPERATOR_CHART_PATH = Paths.get("..", "helm", "pulsar-operator");

    public static final String OPERATOR_IMAGE = System.getProperty("pulsaroperator.tests.operator.image",
            "datastax/lunastreaming-operator:latest-dev");

    public static final String PULSAR_IMAGE = System.getProperty("pulsaroperator.tests.pulsar.image",
            "docker.io/datastax/lunastreaming-all:2.10_3.4");

    public static final boolean USE_EXISTING_ENV = Boolean.getBoolean("pulsaroperator.tests.env.existing");

    public static final Integer K3S_AGENTS = Integer.getInteger("pulsaroperator.tests.env.existing.k3s.agents");

    private static final boolean REUSE_ENV = Boolean
            .parseBoolean(System.getProperty("pulsaroperator.tests.env.reuse", "true"));
    protected static final int DEFAULT_AWAIT_SECONDS = 360;

    private final Integer agents;
    protected String namespace;
    protected static K8sEnv env;
    protected KubernetesClient client;
    private Watch eventsWatch;
    private String rbacManifest;

    public BaseK8sEnvTest(Integer agents) {
        this.agents = agents;
    }
    public BaseK8sEnvTest() {
        this.agents = null;
    }

    @SneakyThrows
    private static List<Path> getOperatorYamlManifests() {
        return Files.list(
                        Path.of(MountableFile
                                .forClasspathResource("/manifests")
                                .getResolvedPath())
                ).filter(p -> p.toFile().getName().endsWith(".yml"))
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private static List<Path> getCRDsManifests() {
        return Files.list(
                        Paths.get(PULSAR_OPERATOR_CHART_PATH.toFile().getAbsolutePath(), "crds")
                ).filter(p -> p.toFile().getName().endsWith(".yml"))
                .collect(Collectors.toList());
    }

    private static void setDefaultAwaitilityTimings() {
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(1));
        Awaitility.setDefaultTimeout(Duration.ofSeconds(DEFAULT_AWAIT_SECONDS));
    }

    @BeforeMethod(alwaysRun = true)
    public void before(Method testMethod) throws Exception {
        setDefaultAwaitilityTimings();

        namespace = "pulsar-operator-test-" + RandomStringUtils.randomAlphabetic(8).toLowerCase();
        log.info("Starting test {}.{} using existing env: {}, reuse env: {}", testMethod.getDeclaringClass().getName(),
                testMethod.getName(), USE_EXISTING_ENV, REUSE_ENV);

        if (env == null || !REUSE_ENV) {
            if (USE_EXISTING_ENV) {
                env = new ExistingK8sEnv();
            } else {
                env = new K3sEnv(K3S_AGENTS == null ? agents : K3S_AGENTS);
            }
        }
        env.start();
        log.info("Using namespace: {} env {}", namespace, env.getConfig().getCurrentContext().getName());
        client = new KubernetesClientBuilder()
                .withConfig(env.getConfig())
                .build();

        client.resource(new NamespaceBuilder().withNewMetadata().withName(namespace)
                .endMetadata().build()).create();
        eventsWatch = client.resources(Event.class).inNamespace(namespace).watch(new Watcher<>() {
            @Override
            public void eventReceived(Action action, Event resource) {

                if (log.isDebugEnabled()) {
                    log.debug("[{}/{}]: {} -> {}", resource.getRegarding().getName(),
                            resource.getRegarding().getKind(), resource.getReason(), resource.getNote());
                } else {
                    log.info("[{}/{}]: {}", resource.getRegarding().getKind(),
                            resource.getRegarding().getName(), resource.getReason());
                }
                if ("FailedMount".equals(resource.getReason())) {
                    log.error("ERROR {} on {}: {}", resource.getKind(), resource.getRegarding().getName(),
                            resource.getNote());
                }
            }

            @Override
            public void onClose(WatcherException cause) {
            }
        });

        this.rbacManifest = getRbacManifest();
    }

    private String getRbacManifest() throws IOException {
        List<String> allRbac = new ArrayList<>();
        allRbac.addAll(Files.readAllLines(
                Paths.get(PULSAR_OPERATOR_CHART_PATH.toFile().getAbsolutePath(), "templates", "rbac.yaml")));
        allRbac.add("---");
        allRbac.addAll(Files.readAllLines(
                Paths.get(PULSAR_OPERATOR_CHART_PATH.toFile().getAbsolutePath(), "templates", "serviceaccount.yaml")));

        final Map<String, String> vars = Map.of(
                ".Release.Namespace", namespace,
                ".Chart.Name", "pulsar-operator",
                ".Release.Name", "test-release",
                ".Chart.AppVersion | quote", "v1",
                ".Values.serviceAccount.name", "pulsar-operator"
        );
        String result = "";
        // simulate go templating
        for (String l : allRbac) {
            if (l.contains("{{- if")) { // assume always true and no content other than the if
                continue;
            }
            if (l.contains("{{- end }}")) { // assume no content other than the end
                continue;
            }
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                l = l.replace("{{ %s }}".formatted(entry.getKey()), entry.getValue());
            }
            result += l + System.lineSeparator();
        }
        return result;
    }

    @SneakyThrows
    protected void applyOperatorDeploymentAndCRDs() {
        getCRDsManifests().forEach(this::applyManifestFromFile);
        for (Path yamlManifest : getOperatorYamlManifests()) {
            if ("kubernetes.yml".equals(yamlManifest.toFile().getName())) {
                final List<HasMetadata> resources =
                        client.load(new ByteArrayInputStream(Files.readAllBytes(yamlManifest))).get();
                resources
                        .stream()
                        .filter(hasMetadata -> hasMetadata instanceof Deployment)
                        .forEach(d -> {
                            Deployment deployment = (Deployment) d;
                            deployment.getSpec().getTemplate().getSpec()
                                    .getContainers()
                                    .get(0)
                                    .setImage(OPERATOR_IMAGE);
                            client.resource(deployment).inNamespace(namespace)
                                    .create();
                            log.info("Applied operator deployment {}", SerializationUtil.writeAsJson(
                                    deployment.getSpec().getTemplate()));
                        });
            } else {
                // skip generated yml, we only use the kubernetes.yml.
                // CRDs are applied separately from the helm chart directory.
                // In this way we ensure correctness of released crds and we can skip generating them in the CI
                log.debug("skipping {}", yamlManifest);
            }
        }
        awaitOperatorRunning();
    }


    @SneakyThrows
    protected void deleteOperatorDeploymentAndCRDs() {
        for (Path yamlManifest : getOperatorYamlManifests()) {

            if (yamlManifest.getFileName().equals("kubernetes.yml")) {
                client.load(new ByteArrayInputStream(Files.readAllBytes(yamlManifest))).get()
                        .stream()
                        .filter(hasMetadata -> hasMetadata instanceof Deployment)
                        .forEach(d -> {
                            Deployment deployment = (Deployment) d;
                            client.resource(deployment).inNamespace(namespace)
                                    .delete();
                            log.info("Deleted operator deployment");
                        });
            }
        }
        getCRDsManifests().forEach(this::deleteManifestFromFile);
    }


    protected void applyRBACManifests() {
        applyManifest(rbacManifest);
    }

    @SneakyThrows
    protected void deleteRBACManifests() {
        if (rbacManifest != null) {
            deleteManifest(rbacManifest);
        }
    }


    @AfterClass(alwaysRun = true)
    public void afterClass() throws Exception {
        if (!REUSE_ENV && env != null) {
            env.close();
        }

    }

    @AfterMethod(alwaysRun = true)
    public void after(ITestResult testResult) throws Exception {
        log.info("test {}: {}", testResult.getMethod().getMethodName(), testResult.isSuccess() ? "SUCCESS"
                : "FAILED");
        if (testResult.getThrowable() != null) {
            log.error("Test {} failed with: {}", testResult.getMethod().getMethodName(),
                    testResult.getThrowable().getMessage(), testResult.getThrowable());
        }
        if ((REUSE_ENV || USE_EXISTING_ENV) && env != null) {
            log.info("cleaning up namespace {}", namespace);
            if (client != null) {
                deleteRBACManifests();
                deleteOperatorDeploymentAndCRDs();
                deleteCRDsSync();
                deleteNamespaceSync();
            }
            env.cleanup();
        }
        if (eventsWatch != null) {
            eventsWatch.close();
        }
        if (client != null) {
            client.close();
            client = null;
        }
        if (!REUSE_ENV && env != null) {
            env.close();
        }
    }

    private void deleteNamespaceSync() {
        client.namespaces().withName(namespace).delete();
        client.pods().inNamespace(namespace).delete();
        Awaitility.await().untilAsserted(() -> {
            List<String> pods = client.pods()
                    .inNamespace(namespace)
                    .list()
                    .getItems()
                    .stream()
                    .map(Pod::getMetadata)
                    .map(ObjectMeta::getName)
                    .collect(Collectors.toList());
            log.info("pods in namespace {} after cleanup: {}", namespace, pods);
            Assert.assertEquals(pods.size(), 0);
        });
    }

    private void deleteCRDsSync() {
        client.resources(CustomResourceDefinition.class).list()
                .getItems()
                .stream()
                .filter(crd -> crd.getSpec().getGroup().equals(CRDConstants.GROUP))
                .forEach(crd -> client.resources(CustomResourceDefinition.class)
                        .withName(crd.getMetadata().getName())
                        .delete()
                );

        // await for actual deletion to not pollute k8s resources
        Awaitility.await().untilAsserted(() ->
                Assert.assertEquals(client.resources(CustomResourceDefinition.class).list()
                        .getItems()
                        .stream()
                        .filter(crd -> crd.getSpec().getGroup().equals(CRDConstants.GROUP))
                        .count(), 0L)
        );
    }

    protected void printAllPodsLogs() {
        try {
            client.pods().inNamespace(namespace).list().getItems()
                    .forEach(pod -> printPodLogs(pod.getMetadata().getName()));
        } catch (Throwable t) {
            log.error("failed to list pods", t.getMessage());
        }
    }

    protected void printRunningPods() {
        try {
            final List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .withLabel("app", "pulsar").list().getItems();
            log.info("found {} pods: {}", pods.size(), pods.stream().map(p -> p.getMetadata().getName()).collect(
                    Collectors.toList()));
        } catch (Throwable t) {
            log.error("failed to print running pods", t.getMessage());
        }
    }

    protected void printPodLogs(String podName) {
        printPodLogs(podName, 300);
    }

    protected void printPodLogs(String podName, int tailingLines) {
        if (podName != null) {
            try {
                client.pods().inNamespace(namespace)
                        .withName(podName)
                        .get().getSpec().getContainers().forEach(container -> {
                            final String sep = "=".repeat(100);
                            final String containerLog = client.pods().inNamespace(namespace)
                                    .withName(podName)
                                    .inContainer(container.getName())
                                    .tailingLines(tailingLines)
                                    .getLog();
                            log.info("{}\n{}\n{}/{} pod logs (last {} lines}:\n{}\n{}\n{}", sep, sep, podName,
                                    container.getName(),
                                    tailingLines, containerLog, sep, sep);
                        });


            } catch (Throwable t) {
                log.error("failed to get pod {} logs: {}", podName, t.getMessage());
            }
        }
    }

    @SneakyThrows
    protected void applyManifestFromFile(Path path) {
        log.info("applying manifest from file {}", path);
        applyManifest(Files.readAllBytes(path));
    }

    protected void applyManifest(String manifest) {
        applyManifest(manifest.getBytes(StandardCharsets.UTF_8));
    }

    protected void applyManifest(byte[] manifest) {
        client.load(new ByteArrayInputStream(manifest))
                .inNamespace(namespace)
                .createOrReplace();
    }

    @SneakyThrows
    protected void deleteManifestFromFile(Path path) {
        deleteManifest(Files.readAllBytes(path));
    }

    protected void deleteManifest(String manifest) {
        deleteManifest(manifest.getBytes(StandardCharsets.UTF_8));
    }

    protected void deleteManifest(byte[] manifest) {
        final List<HasMetadata> resources =
                client.load(new ByteArrayInputStream(manifest)).get();
        client.resourceList(resources).inNamespace(namespace).delete();
    }

    protected String getOperatorPodName() {
        return client.pods()
                .inNamespace(namespace)
                .withLabel("app.kubernetes.io/name", "pulsar-operator").list().getItems()
                .get(0)
                .getMetadata()
                .getName();
    }

    protected void awaitOperatorRunning() {
        Awaitility.await().untilAsserted(() -> {
            final List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .withLabel("app.kubernetes.io/name", "pulsar-operator").list().getItems();
            Assert.assertTrue(pods.size() > 0);
            Assert.assertTrue(pods.stream().filter(p -> "Running".equals(p.getStatus().getPhase()))
                    .count() > 0);
        });
    }


    protected void execInBastionPod(String... cmd) {
        final String bastion = client.pods().inNamespace(namespace)
                .withLabel("component", "bastion")
                .list().getItems().get(0).getMetadata().getName();
        execInPod(bastion, cmd);
    }

    @SneakyThrows
    protected void execInPod(String podName, String... cmd) {
        execInPodContainer(podName, null, cmd);
    }

    @SneakyThrows
    protected void execInPodContainer(String podName, String containerName, String... cmds) {
        final String cmd = Arrays.stream(cmds).collect(Collectors.joining(" && "));
        int remainingAttempts = 3;
        RuntimeException lastEx = null;
        while (remainingAttempts-- > 0) {
            log.info("Executing in pod {}: {}", containerName == null ? podName : podName + "/" + containerName, cmd);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CompletableFuture<String> future = new CompletableFuture<>();
            try (final ExecWatch exec = client
                    .pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .inContainer(containerName)
                    .writingOutput(baos)
                    .writingError(baos)
                    .usingListener(new SimpleListener(future, baos))
                    .exec("bash", "-c", cmd);) {
                final String outputCmd = future.get(5, TimeUnit.MINUTES);
                log.info("Output cmd: {}", outputCmd);
                if (exec.exitCode().get().intValue() != 0) {
                    log.error("Cmd failed with code {}: {}", exec.exitCode().get().intValue(), outputCmd);
                    lastEx = new RuntimeException("Cmd '%s' failed with: %s".formatted(cmd, outputCmd));
                } else {
                    return;
                }
            }
        }
        throw lastEx;
    }

    static class SimpleListener implements ExecListener {

        private CompletableFuture<String> data;
        private ByteArrayOutputStream baos;

        public SimpleListener(CompletableFuture<String> data, ByteArrayOutputStream baos) {
            this.data = data;
            this.baos = baos;
        }

        @Override
        public void onOpen() {
        }

        @Override
        public void onFailure(Throwable t, Response failureResponse) {
            System.err.println(t.getMessage());
            data.completeExceptionally(t);
        }

        @Override
        public void onClose(int code, String reason) {
            data.complete(baos.toString());
        }
    }

    protected String getPodNameByComponent(String component) {
        return getPodNamesByComponent(component).get(0);
    }

    protected List<String> getPodNamesByComponent(String component) {
        return client.pods()
                .inNamespace(namespace)
                .withLabel("component", component)
                .list()
                .getItems()
                .stream()
                .map(p -> p.getMetadata().getName())
                .collect(Collectors.toList());
    }

    protected String getPodNameByLabels(Map<String, String> labels) {
        return client.pods()
                .inNamespace(namespace)
                .withLabels(labels)
                .list()
                .getItems()
                .get(0)
                .getMetadata()
                .getName();
    }

    protected List<String> getPodNamesByResourceSet(String rs) {
        return client.pods()
                .inNamespace(namespace)
                .withLabel(CRDConstants.LABEL_RESOURCESET, rs)
                .list()
                .getItems()
                .stream()
                .map(p -> p.getMetadata().getName())
                .collect(Collectors.toList());
    }
}
