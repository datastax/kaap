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
package com.datastax.oss.kaap.tests;

import com.datastax.oss.kaap.autoscaler.AutoscalerUtils;
import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.autorecovery.Autorecovery;
import com.datastax.oss.kaap.crds.bastion.Bastion;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeper;
import com.datastax.oss.kaap.crds.broker.Broker;
import com.datastax.oss.kaap.crds.cluster.PulsarCluster;
import com.datastax.oss.kaap.crds.function.FunctionsWorker;
import com.datastax.oss.kaap.crds.proxy.Proxy;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeper;
import com.datastax.oss.kaap.tests.env.ExistingK8sEnv;
import com.datastax.oss.kaap.tests.env.K3sEnv;
import com.datastax.oss.kaap.tests.env.K8sEnv;
import io.fabric8.certmanager.api.model.v1.Certificate;
import io.fabric8.certmanager.api.model.v1.CertificateRequest;
import io.fabric8.certmanager.api.model.v1.Issuer;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

@Slf4j
public abstract class BaseK8sEnvTest {

    public static final Path OPERATOR_CHART_PATH = Paths.get("..", "helm", "kaap");

    public static final String OPERATOR_IMAGE = System.getProperty("kaap.tests.operator.image",
            "datastax/kaap:latest-dev");

    public static final String PULSAR_IMAGE = System.getProperty("kaap.tests.pulsar.image",
            "docker.io/datastax/lunastreaming-all:2.10_5.1");

    public static final String PULSAR3_IMAGE = System.getProperty("kaap.tests.pulsar.image",
            "docker.io/datastax/lunastreaming-all:3.1_4.0");

    public static final boolean USE_EXISTING_ENV = Boolean.getBoolean("kaap.tests.env.existing");

    public static final Integer K3S_AGENTS = Integer.getInteger("kaap.tests.env.existing.k3s.agents");
    public static final File TEST_LOGS_DIR = new File("target", "operator-test-logs");

    private static final boolean REUSE_ENV = Boolean
            .parseBoolean(System.getProperty("kaap.tests.env.reuse", "true"));
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
    private static List<Path> getCRDsManifests() {
        return Files.list(
                        Paths.get(OPERATOR_CHART_PATH.toFile().getAbsolutePath(), "crds")
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

        namespace = "kaap-test-" + RandomStringUtils.randomAlphabetic(8).toLowerCase();
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

        eventsWatch = client.resources(Event.class).watch(new Watcher<>() {
            @Override
            public void eventReceived(Action action, Event resource) {
                final String ns = resource.getMetadata().getNamespace();

                if (log.isDebugEnabled()) {
                    log.debug("[{}][{}/{}]: {} -> {}", ns, resource.getRegarding().getName(),
                            resource.getRegarding().getKind(), resource.getReason(), resource.getNote());
                } else {
                    log.info("[{}][{}/{}]: {}", ns, resource.getRegarding().getKind(),
                            resource.getRegarding().getName(), resource.getReason());
                }
                if ("FailedMount".equals(resource.getReason()) || "FailedScheduling".equals(resource.getReason())) {
                    log.error("[{}] ERROR {} on {}: {}", ns, resource.getKind(), resource.getRegarding().getName(),
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
                Paths.get(OPERATOR_CHART_PATH.toFile().getAbsolutePath(), "templates", "rbac", "namespace-scoped.yaml")));
        allRbac.add("---");
        allRbac.addAll(Files.readAllLines(
                Paths.get(OPERATOR_CHART_PATH.toFile().getAbsolutePath(), "templates", "serviceaccount.yaml")));

        return simulateHelmRendering(allRbac);
    }

    private String getOperatorDeploymentManifest() throws IOException {
        List<String> manifests = new ArrayList<>();
        manifests.addAll(Files.readAllLines(
                Paths.get(OPERATOR_CHART_PATH.toFile().getAbsolutePath(), "templates", "operator.yaml")));
        manifests.add("---");
        manifests.addAll(Arrays.stream("""
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: {{ include "kaap.name" . }}
                  namespace: {{ .Release.Namespace }}
                  labels:
                      {{- include "kaap.labels" . | nindent 4 }}
                data:
                  QUARKUS_OPERATOR_SDK_NAMESPACES: "JOSDK_WATCH_CURRENT"
                  QUARKUS_LOG_CATEGORY__COM_DATASTAX_OSS_KAAP__LEVEL: debug
                """.split("\n")).toList());
        return simulateHelmRendering(manifests);
    }

    private String simulateHelmRendering(List<String> allRbac) {
        final Map<String, String> vars = new HashMap<>();

        vars.put(".Release.Namespace", namespace);
        vars.put(".Chart.Name", "kaap");
        vars.put(".Release.Name", "test-release");
        vars.put(".Chart.AppVersion | quote", "v1");
        vars.put(".Values.serviceAccount.name", "kaap");
        vars.put(".Values.operator.replicas", "1");
        vars.put(".Values.operator.image", OPERATOR_IMAGE);
        vars.put(".Values.operator.imagePullPolicy", "Never");
        vars.put(".Values.operator.livenessProbe.failureThreshold", "3");
        vars.put(".Values.operator.livenessProbe.initialDelaySeconds", "0");
        vars.put(".Values.operator.livenessProbe.periodSeconds", "30");
        vars.put(".Values.operator.livenessProbe.successThreshold", "1");
        vars.put(".Values.operator.livenessProbe.timeoutSeconds", "10");
        vars.put(".Values.operator.readinessProbe.failureThreshold", "3");
        vars.put(".Values.operator.readinessProbe.initialDelaySeconds", "0");
        vars.put(".Values.operator.readinessProbe.periodSeconds", "30");
        vars.put(".Values.operator.readinessProbe.successThreshold", "1");
        vars.put(".Values.operator.readinessProbe.timeoutSeconds", "10");
        vars.put(".Values.nodeSelector", "        {}");
        vars.put(".Values.operator.nodeSelector", "        {}");
        vars.put(".Values.operator.tolerations", "        []");
        vars.put("include \"kaap.name\" .", "kaap");
        vars.put("include \"kaap.roleName\" .", "kaap");
        vars.put("include \"kaap.roleBindingName\" .", "kaap-role-binding");
        vars.put("include \"kaap.serviceAccountName\" .", "kaap");
        vars.put("include \"kaap.labels\" . | nindent 4", "{app.kubernetes.io/name: kaap}");
        vars.put("include \"kaap.selectorLabels\" . | nindent 6", "{app.kubernetes.io/name: kaap}");
        vars.put("include \"kaap.selectorLabels\" . | nindent 8", "{app.kubernetes.io/name: kaap}");
        vars.put("include (print $.Template.BasePath \"/configmap.yaml\") . | sha256sum", "sha");
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
                l = l.replace("{{ toYaml %s | indent 8 }}".formatted(entry.getKey()), entry.getValue());
                l = l.replace("{{- %s }}".formatted(entry.getKey()), entry.getValue());
            }
            result += l + System.lineSeparator();
        }
        if (result.contains("{{")) {
            throw new RuntimeException("Failed to replace all go templating vars, template: " + result);
        }
        return result;
    }

    @SneakyThrows
    protected void applyOperatorDeploymentAndCRDs() {
        getCRDsManifests().forEach(this::applyManifestFromFile);
        final String operatorDeploymentManifest = getOperatorDeploymentManifest();
        applyManifest(operatorDeploymentManifest);
        awaitOperatorRunning();
    }


    @SneakyThrows
    protected void deleteOperatorDeploymentAndCRDs() {
        deleteManifest(getOperatorDeploymentManifest());
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

            final String prefix = "%s.%s".formatted(testResult.getTestClass().getRealClass().getSimpleName(),
                    testResult.getMethod().getMethodName());
            dumpAllPodsLogs(prefix, namespace);
            dumpAllPodsLogs(prefix, "kube-system");
            dumpEvents(prefix);
            dumpAllResources(prefix);
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
        client.persistentVolumes().list().getItems().forEach(pv -> {
            if (pv.getSpec().getClaimRef() == null || namespace.equals(pv.getSpec().getClaimRef().getNamespace())) {
                client.resource(pv).delete();
            }
        });
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
        final String sep = "=".repeat(100);
        withPodLogs(podName, namespace, tailingLines, (container, logs) -> {
            log.info("{}\n{}\n{}/{} pod logs (last {} lines}:\n{}\n{}\n{}", sep, sep, podName,
                    container,
                    tailingLines, logs, sep, sep);
        });
    }


    protected void withPodLogs(String podName, String namespace, int tailingLines, BiConsumer<String, String> consumer) {
        if (podName != null) {
            try {
                client.pods().inNamespace(namespace)
                        .withName(podName)
                        .get().getSpec().getContainers().forEach(container -> {
                            final ContainerResource containerResource = client.pods().inNamespace(namespace)
                                    .withName(podName)
                                    .inContainer(container.getName());
                            if (tailingLines > 0) {
                                containerResource.tailingLines(tailingLines);
                            }
                            final String containerLog = containerResource.getLog();
                            consumer.accept(container.getName(), containerLog);
                        });
            } catch (Throwable t) {
                log.error("failed to get pod {} logs: {}", podName, t.getMessage());
            }
        }
    }

    protected void dumpAllPodsLogs(String filePrefix, String namespace) {
        client.pods().inNamespace(namespace).list().getItems()
                .forEach(pod -> dumpPodLogs(pod.getMetadata().getName(), namespace, filePrefix));
    }

    protected void dumpPodLogs(String podName, String namespace, String filePrefix) {
        TEST_LOGS_DIR.mkdirs();
        withPodLogs(podName, namespace, -1, (container, logs) -> {
            final File outputFile = new File(TEST_LOGS_DIR, "%s.%s.%s.log".formatted(filePrefix, podName, container));
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(logs);
            } catch (IOException e) {
                log.error("failed to write pod {} logs to file {}", podName, outputFile, e);
            }
        });
    }

    protected void dumpAllResources(String filePrefix) {
        dumpResources(filePrefix, Pod.class);
        dumpResources(filePrefix, Deployment.class);
        dumpResources(filePrefix, StatefulSet.class);
        dumpResources(filePrefix, PersistentVolume.class);
        dumpResources(filePrefix, PersistentVolumeClaim.class);
        dumpResources(filePrefix, Node.class);
        dumpResources(filePrefix, PulsarCluster.class);
        dumpResources(filePrefix, ZooKeeper.class);
        dumpResources(filePrefix, BookKeeper.class);
        dumpResources(filePrefix, Broker.class);
        dumpResources(filePrefix, Proxy.class);
        dumpResources(filePrefix, FunctionsWorker.class);
        dumpResources(filePrefix, Bastion.class);
        dumpResources(filePrefix, Autorecovery.class);
        dumpResourcesAllNamespaces(filePrefix, CertificateRequest.class);
        dumpResourcesAllNamespaces(filePrefix, Certificate.class);
        dumpResourcesAllNamespaces(filePrefix, Issuer.class);
    }

    private void dumpResourcesAllNamespaces(String filePrefix, Class<? extends HasMetadata> clazz) {
        try {
            client.namespaces().list()
                    .getItems()
                    .forEach(ns -> dumpResources(filePrefix, clazz, ns.getMetadata().getName()));
        } catch (Throwable t) {
            log.error("failed to list namespaces for getting resource of class {}: {}", clazz, t.getMessage());
        }
    }

    private void dumpResources(String filePrefix, Class<? extends HasMetadata> clazz) {
        dumpResources(filePrefix, clazz, namespace);
    }

    private void dumpResources(String filePrefix, Class<? extends HasMetadata> clazz, String namespace) {
        try {
            client.resources(clazz)
                    .inNamespace(namespace)
                    .list()
                    .getItems()
                    .forEach(resource -> dumpResource(filePrefix, resource));
        } catch (Throwable t) {
            log.error("failed to dump resources of type {}: {}", clazz, t.getMessage());
        }
    }

    protected void dumpResource(String filePrefix, HasMetadata resource) {
        TEST_LOGS_DIR.mkdirs();
        final File outputFile = new File(TEST_LOGS_DIR,
                "%s-%s-%s.log".formatted(filePrefix, resource.getKind(), resource.getMetadata().getName()));
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(SerializationUtil.writeAsYaml(resource));
        } catch (Throwable e) {
            log.error("failed to write resource to file {}", outputFile, e);
        }
    }

    protected void dumpEvents(String filePrefix) {
        TEST_LOGS_DIR.mkdirs();
        final File outputFile = new File(TEST_LOGS_DIR, "%s-events.log".formatted(filePrefix));
        try (FileWriter writer = new FileWriter(outputFile)) {
            client.resources(Event.class).inAnyNamespace().list()
                    .getItems()
                    .forEach(event -> {
                        try {
                            writer.write("[%s] [%s/%s] %s: %s\n".formatted(event.getMetadata().getNamespace(),
                                    event.getRegarding().getKind(),
                                    event.getRegarding().getName(), event.getReason(), event.getNote()));
                        } catch (IOException e) {
                            log.error("failed to write event {} to file {}", event, outputFile, e);
                        }
                    });
        } catch (Throwable e) {
            log.error("failed to write events logs to file {}", outputFile, e);
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
                .withLabel("app.kubernetes.io/name", "kaap").list().getItems()
                .get(0)
                .getMetadata()
                .getName();
    }

    protected void awaitOperatorRunning() {
        Awaitility.await().atMost(15, TimeUnit.MINUTES).untilAsserted(() -> {
            final List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .withLabel("app.kubernetes.io/name", "kaap").list().getItems();
            Assert.assertTrue(pods.size() > 0);
            Assert.assertTrue(pods.stream().filter(p -> BaseResourcesFactory.isPodReady(p))
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
    protected String execInPod(String podName, String... cmd) {
        return execInPodContainer(podName, null, cmd);
    }

    @SneakyThrows
    protected String execInPodContainer(String podName, String containerName, String... cmds) {
        final String cmd = Arrays.stream(cmds).collect(Collectors.joining(" && "));
        int remainingAttempts = 3;
        RuntimeException lastEx = null;
        while (remainingAttempts-- > 0) {
            log.info("Executing in pod {}: {}", containerName == null ? podName : podName + "/" + containerName,
                    cmd);
            try {
                return AutoscalerUtils.execInPod(
                        client, namespace, podName, containerName, cmd
                ).get();
            } catch (ExecutionException e) {
                log.error("Cmd failed with code: {}", e.getCause());
                lastEx = new RuntimeException("Cmd '%s' failed with: %s".formatted(cmd, e.getCause()));
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

    protected String ensureNamespace(String namespaceName) {
        var ns = new Namespace();
        var meta = new ObjectMeta();

        meta.setName(namespaceName);
        ns.setMetadata(meta);

        client.namespaces()
                .resource(ns)
                .createOrReplace();
        return namespaceName;
    }
}
