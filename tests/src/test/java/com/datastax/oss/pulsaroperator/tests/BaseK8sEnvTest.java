package com.datastax.oss.pulsaroperator.tests;

import com.dajudge.kindcontainer.helm.Helm3Container;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.tests.env.ExistingK8sEnv;
import com.datastax.oss.pulsaroperator.tests.env.K8sEnv;
import com.datastax.oss.pulsaroperator.tests.env.LocalK3SContainer;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.events.v1.Event;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.testcontainers.containers.Container;
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

    @BeforeMethod(alwaysRun = true)
    public void before() throws Exception {

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
                log.info("event [{} - {}]: {} - {}", resource.getRegarding().getName(),
                        resource.getRegarding().getKind(),
                        resource.getNote(), resource.getReason());
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
                      - com.datastax.oss
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
    protected void applyOperatorManifests() {
        for (Path yamlManifest : getYamlManifests()) {

            if (yamlManifest.getFileName().equals("kubernetes.yml")) {
                client.load(new ByteArrayInputStream(Files.readAllBytes(yamlManifest))).get()
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
                            log.info("Applied operator deployment");
                        });
            } else {
                applyManifestFromFile(yamlManifest);
                log.info("Applied {}", yamlManifest);

            }
        }
        awaitOperatorRunning();
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
        if (env != null) {
            env.close();
        }

    }

    @AfterMethod(alwaysRun = true)
    public void after() throws Exception {
        printOperatorPodLogs();
        if (env != null) {
            deleteRBACManifests();
            env.cleanup();
        }
        if (eventsWatch != null) {
            eventsWatch.close();
        }
        if (client != null) {
            client.namespaces().withName(namespace).delete();
            client.close();
            client = null;
        }
    }

    private void printOperatorPodLogs() {
        printPodLogs(operatorPodName);
    }

    protected void printAllPodsLogs() {
        client.pods().inNamespace(namespace).list().getItems()
                .forEach(pod -> printPodLogs(pod.getMetadata().getName()));
    }

    protected void printPodLogs(String podName) {
        if (podName != null) {
            try {
                final String podLog = client.pods().inNamespace(namespace)
                        .withName(podName)
                        .tailingLines(300)
                        .getLog();
                final String sep = "-".repeat(100);
                log.info("{}\n{} pod logs:\n{}\n{}", sep, podName, podLog, sep);
            } catch (Throwable t) {
                log.error("failed to get operator pod logs: {}", t.getMessage(), t);
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
        log.info("Applying {}", new String(manifest, StandardCharsets.UTF_8));
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

    protected void applyPulsarCluster(Path path) {
        client.resources(PulsarCluster.class)
                .inNamespace(namespace)
                .load(path.toFile())
                .createOrReplace();
    }

    protected void deleteManifest(String manifest) {
        final List<HasMetadata> resources =
                client.load(new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8))).get();
        client.resourceList(resources).inNamespace(namespace).delete();
    }


    @SneakyThrows
    protected void helmInstall() {
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
    protected void helmUninstall() {
        final Helm3Container helm3Container = env.helmContainer();
        final Container.ExecResult exec =
                helm3Container.execInContainer("helm", "delete", "test", "-n", namespace);
        if (exec.getExitCode() != 0) {
            throw new RuntimeException("Helm uninstallation failed: " + exec.getStderr());
        }
    }

    @SneakyThrows
    protected Path getHelmExampleFilePath(String name) {
        return Paths.get("..", "helm", "examples", name);
    }


    protected void awaitOperatorRunning() {
        Awaitility.await().pollInterval(1, TimeUnit.SECONDS).untilAsserted(() -> {
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

}
