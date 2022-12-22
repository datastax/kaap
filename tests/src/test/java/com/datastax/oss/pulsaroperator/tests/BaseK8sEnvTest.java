package com.datastax.oss.pulsaroperator.tests;

import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.SerializationUtil;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.tests.env.ExistingK8sEnv;
import com.datastax.oss.pulsaroperator.tests.env.K8sEnv;
import com.datastax.oss.pulsaroperator.tests.env.LocalK3SContainer;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testcontainers.utility.MountableFile;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

@Slf4j
public abstract class BaseK8sEnvTest {

    public static final String OPERATOR_IMAGE = System.getProperty("pulsaroperator.tests.operator.image",
            "datastax/pulsar-operator:latest");

    public static final String PULSAR_IMAGE = System.getProperty("pulsaroperator.tests.pulsar.image",
            "datastax/lunastreaming-all:2.10_2.4");

    public static final boolean USE_EXISTING_ENV = Boolean.getBoolean("pulsaroperator.tests.env.existing");

    private static final boolean REUSE_ENV = Boolean
            .parseBoolean(System.getProperty("pulsaroperator.tests.env.reuse", "false"));
    protected static final int DEFAULT_AWAIT_SECONDS = 360;

    protected String namespace;
    protected K8sEnv env;
    protected KubernetesClient client;
    private Watch eventsWatch;
    private String operatorPodName;
    private String rbacManifest;

    @SneakyThrows
    private static List<Path> getYamlManifests() {
        return Files.list(
                        Path.of(MountableFile
                                .forClasspathResource("/manifests")
                                .getResolvedPath())
                ).filter(p -> p.toFile().getName().endsWith(".yml"))
                .collect(Collectors.toList());
    }

    private static void setDefaultAwaitilityTimings() {
        Awaitility.setDefaultPollInterval(Duration.ofSeconds(1));
        Awaitility.setDefaultTimeout(Duration.ofSeconds(DEFAULT_AWAIT_SECONDS));
    }

    @BeforeMethod(alwaysRun = true)
    public void before() throws Exception {
        setDefaultAwaitilityTimings();

        namespace = "pulsar-operator-test-" + UUID.randomUUID();

        if (USE_EXISTING_ENV) {
            env = new ExistingK8sEnv();
        } else {
            env = new LocalK3SContainer();
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
             }

            @Override
            public void onClose(WatcherException cause) {
            }
        });

        rbacManifest = """
                apiVersion: v1
                kind: ServiceAccount
                metadata:
                  name: pulsar-operator
                  namespace: %s
                ---
                apiVersion: rbac.authorization.k8s.io/v1
                kind: ClusterRole
                metadata:
                  name: pulsar-operator-cluster-role-admin
                  namespace: %s
                rules:
                  - apiGroups:
                      - "apiextensions.k8s.io"
                    resources:
                      - customresourcedefinitions
                    verbs:
                      - '*'
                ---
                apiVersion: rbac.authorization.k8s.io/v1
                kind: ClusterRoleBinding
                metadata:
                  name: pulsar-operator-cluster-role-admin-binding
                  namespace: %s
                roleRef:
                  kind: ClusterRole
                  apiGroup: rbac.authorization.k8s.io
                  name: pulsar-operator-cluster-role-admin
                subjects:
                  - kind: ServiceAccount
                    name: pulsar-operator
                    namespace: %s
                ---
                apiVersion: rbac.authorization.k8s.io/v1
                kind: Role
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
                      - serviceaccounts
                      - secrets
                    verbs:
                      - '*'
                  - apiGroups:
                      - "batch"
                    resources:
                      - jobs
                    verbs:
                      - '*'
                  - apiGroups:
                      - policy
                    resources:
                      - poddisruptionbudgets
                    verbs:
                      - "*"
                  - apiGroups:
                      - "apiextensions.k8s.io"
                    resources:
                        - customresourcedefinitions
                    verbs:
                        - '*'
                  - apiGroups:
                      - "rbac.authorization.k8s.io"
                    resources:
                        - roles
                        - rolebindings
                    verbs:
                        - '*'
                  - apiGroups:
                      - pulsar.oss.datastax.com
                    resources:
                        - pulsarclusters
                        - pulsarclusters/status
                        - zookeepers
                        - zookeepers/status
                        - bookkeepers
                        - bookkeepers/status
                        - brokers
                        - brokers/status
                        - proxies
                        - proxies/status
                        - autorecoveries
                        - autorecoveries/status
                        - bastions
                        - bastions/status
                        - functionsworkers
                        - functionsworkers/status
                    verbs:
                        - "*"
                ---
                apiVersion: rbac.authorization.k8s.io/v1
                kind: RoleBinding
                metadata:
                  name: pulsar-operator-role-admin-binding
                  namespace: %s
                roleRef:
                  kind: Role
                  apiGroup: rbac.authorization.k8s.io
                  name: pulsar-operator-role-admin
                subjects:
                  - kind: ServiceAccount
                    name: pulsar-operator
                    namespace: %s
                """.formatted(namespace, namespace, namespace, namespace, namespace, namespace, namespace);
    }

    @SneakyThrows
    protected void applyOperatorDeploymentAndCRDs() {
        for (Path yamlManifest : getYamlManifests()) {
            if (yamlManifest.toFile().getName().equals("kubernetes.yml")) {
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
                            log.info("Applied operator deployment" + SerializationUtil.writeAsJson(
                                    deployment.getSpec().getTemplate()));
                        });
            } else {
                applyManifestFromFile(yamlManifest);
                log.info("Applied {}", yamlManifest);

            }
        }
        awaitOperatorRunning();
    }


    @SneakyThrows
    protected void deleteOperatorDeploymentAndCRDs() {
        for (Path yamlManifest : getYamlManifests()) {

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
            } else {
                deleteManifestFromFile(yamlManifest);
                log.info("Deleted {}", yamlManifest);

            }
        }
    }


    protected void applyRBACManifests() {
        applyManifest(rbacManifest);
    }

    @SneakyThrows
    protected void deleteRBACManifests() {
        deleteManifest(rbacManifest);
    }


    @AfterClass(alwaysRun = true)
    public void afterClass() throws Exception {
        if (!REUSE_ENV && env != null) {
            env.close();
        }

    }

    @AfterMethod(alwaysRun = true)
    public void after() throws Exception {
        if ((REUSE_ENV || USE_EXISTING_ENV) && env != null) {
            if (client != null) {
                deleteRBACManifests();
                deleteOperatorDeploymentAndCRDs();
                deleteCRDsSync();
                client.namespaces().withName(namespace).delete();
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

    private void printOperatorPodLogs() {
        printPodLogs(operatorPodName);
    }

    protected void printAllPodsLogs() {
        client.pods().inNamespace(namespace).list().getItems()
                .forEach(pod -> printPodLogs(pod.getMetadata().getName()));
    }

    protected void printRunningPods() {
        final List<Pod> pods = client.pods()
                .inNamespace(namespace)
                .withLabel("app", "pulsar").list().getItems();
        log.info("found {} pods: {}", pods.size(), pods.stream().map(p -> p.getMetadata().getName()).collect(
                Collectors.toList()));
    }

    protected void printPodLogs(String podName) {
        if (podName != null) {
            try {
                client.pods().inNamespace(namespace)
                        .withName(podName)
                        .get().getSpec().getContainers().forEach(container -> {
                            final String sep = "=".repeat(300);
                            final String containerLog = client.pods().inNamespace(namespace)
                                    .withName(podName)
                                    .inContainer(container.getName())
                                    .tailingLines(300)
                                    .getLog();
                            log.info("{}\n{}\n{}/{} pod logs:\n{}\n{}\n{}", sep, sep, podName, container.getName(),
                                    containerLog, sep, sep);
                        });


            } catch (Throwable t) {
                log.error("failed to get operator pod logs: {}", t.getMessage());
            }
        }
    }

    @SneakyThrows
    protected void applyManifestFromFile(Path path) {
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

    protected void applyPulsarCluster(String content) {
        client.resources(PulsarCluster.class)
                .inNamespace(namespace)
                .load(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
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


    @SneakyThrows
    protected Path getHelmExampleFilePath(String name) {
        return Paths.get("..", "helm", "examples", name);
    }


    protected void awaitOperatorRunning() {
        Awaitility.await().untilAsserted(() -> {
            final List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .withLabel("app.kubernetes.io/name", "pulsar-operator").list().getItems();
            Assert.assertEquals(pods.size(), 1);
            Assert.assertEquals(pods.stream().filter(p -> p.getStatus().getPhase().equals("Running"))
                    .count(), 1);
        });
        operatorPodName = client.pods()
                .inNamespace(namespace)
                .withLabel("app.kubernetes.io/name", "pulsar-operator")
                .list()
                .getItems()
                .get(0)
                .getMetadata()
                .getName();
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
            final String outputCmd = future.get(30, TimeUnit.SECONDS);
            log.info("Output cmd: {}", outputCmd);
            if (exec.exitCode().get().intValue() != 0) {
                log.error("Cmd failed with code {}: {}", exec.exitCode().get().intValue(), outputCmd);
                throw new RuntimeException("Cmd '%s' failed with: %s".formatted(cmd, outputCmd));
            }
        }
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


    private void awaitJobCompleted(String name) {
        Awaitility.await()
                .atMost(2, TimeUnit.MINUTES)
                .untilAsserted(() -> {
                    final Job job = client.batch().v1().jobs()
                            .inNamespace(namespace)
                            .withName(name)
                            .get();
                    Assert.assertNotNull(job);
                    final JobStatus status = job.getStatus();
                    Assert.assertNotNull(status);
                    final Integer succeeded = status
                            .getSucceeded();
                    Assert.assertNotNull(succeeded);
                    Assert.assertEquals(succeeded.intValue(), 1);
                });

    }

}
