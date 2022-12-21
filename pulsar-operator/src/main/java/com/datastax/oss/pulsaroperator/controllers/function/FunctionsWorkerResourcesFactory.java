package com.datastax.oss.pulsaroperator.controllers.function;

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.SerializationUtil;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorkerSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class FunctionsWorkerResourcesFactory extends BaseResourcesFactory<FunctionsWorkerSpec> {

    private ConfigMap configMap;
    private ConfigMap extraConfigMap;

    public FunctionsWorkerResourcesFactory(KubernetesClient client, String namespace,
                                           FunctionsWorkerSpec spec, GlobalSpec global,
                                           OwnerReference ownerReference) {
        super(client, namespace, spec, global, ownerReference);
    }

    @Override
    protected String getComponentBaseName() {
        return global.getComponents().getFunctionsWorkerBaseName();
    }

    @Override
    protected boolean isComponentEnabled() {
        return spec.getReplicas() > 0;
    }

    public void patchService() {

        final FunctionsWorkerSpec.ServiceConfig serviceSpec = spec.getService();

        List<ServicePort> ports = new ArrayList<>();
        ports.add(new ServicePortBuilder()
                .withName("http")
                .withPort(6750)
                .build());
        ports.add(new ServicePortBuilder()
                .withName("https")
                .withPort(6751)
                .build());
        if (serviceSpec.getAdditionalPorts() != null) {
            ports.addAll(serviceSpec.getAdditionalPorts());
        }

        final Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels())
                .endMetadata()
                .withNewSpec()
                .withPorts(ports)
                .withClusterIP("None")
                .withType(serviceSpec.getType())
                .withSelector(getMatchLabels())
                .endSpec()
                .build();

        patchResource(service);
    }

    public void patchCaService() {

        final FunctionsWorkerSpec.ServiceConfig serviceSpec = spec.getService();

        Map<String, String> annotations = null;
        if (serviceSpec.getAnnotations() != null) {
            annotations = serviceSpec.getAnnotations();
        }
        List<ServicePort> ports = new ArrayList<>();
        ports.add(new ServicePortBuilder()
                .withName("http")
                .withPort(6750)
                .build());
        ports.add(new ServicePortBuilder()
                .withName("https")
                .withPort(6751)
                .build());
        if (serviceSpec.getAdditionalPorts() != null) {
            ports.addAll(serviceSpec.getAdditionalPorts());
        }

        final Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName("%s-ca".formatted(resourceName))
                .withNamespace(namespace)
                .withLabels(getLabels())
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withPorts(ports)
                .withSelector(getMatchLabels())
                .endSpec()
                .build();

        patchResource(service);
    }

    public void patchExtraConfigMap() {

        Map<String, String> data = new HashMap<>();
        data.put("PULSAR_MEM",
                "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -XX:+ExitOnOutOfMemoryError");
        data.put("PULSAR_GC", "-XX:+UseG1GC");
        data.put("PULSAR_LOG_LEVEL", "info");
        data.put("PULSAR_LOG_ROOT_LEVEL", "info");
        data.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");

        if (isAuthTokenEnabled()) {
            final AuthConfig.TokenConfig tokenConfig = global.getAuth().getToken();
            data.put("authorizationEnabled", "true");
            data.put("authenticationEnabled", "true");
            data.put("brokerClientAuthenticationPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
            data.put("brokerClientAuthenticationParameters", "file:///pulsar/token-superuser/superuser.jwt");
            data.put("superUserRoles", tokenConfig.superUserRolesAsString());
            data.put("tokenPublicKey", "file:///pulsar/token-public-key/%s".formatted(tokenConfig.getPublicKeyFile()));
            data.put("authenticationProviders", "org.apache.pulsar.broker.authentication.AuthenticationProviderToken,"
                    + "org.apache.pulsar.broker.authentication.AuthenticationProviderTls");
        }

        if (spec.getConfig() != null) {
            spec.getConfig().forEach((k, v) -> {
                // only keep properties passed to the bin/pulsar command
                // all the functions worker props will go in the main ConfigMap
                if (k.startsWith("PULSAR_") && v instanceof CharSequence) {
                    data.put(k, v.toString());
                }
            });
        }
        final ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName("%s-extra".formatted(resourceName))
                .withNamespace(namespace)
                .withLabels(getLabels()).endMetadata()
                .withData(data)
                .build();
        patchResource(configMap);
        this.extraConfigMap = configMap;

    }

    public void patchConfigMap() {

        Map<String, Object> data = new HashMap<>();
        final String zkServers = getZkServers();
        data.put("configurationStoreServers", zkServers);
        data.put("zookeeperServers", zkServers);
        data.put("zooKeeperSessionTimeoutMillis", "30000");
        data.put("pulsarFunctionsCluster", global.getName());
        data.put("workerId", resourceName);
        data.put("workerHostname", resourceName);
        data.put("workerPort", "6750");
        if (isTlsEnabledGlobally()) {
            data.put("tlsEnabled", "true");
            data.put("workerPortTls", "6751");
            data.put("tlsCertificateFilePath", "/pulsar/certs/tls.crt");
        }
        if (isTlsEnabledOnBookKeeper()) {
            data.put("bookkeeperTLSClientAuthentication", "true");
        }
        if (isAuthTokenEnabled()) {
            final AuthConfig.TokenConfig tokenConfig = global.getAuth().getToken();
            data.put("authenticationEnabled", "true");
            data.put("authorizationEnabled", "true");
            data.put("authorizationProvider", "org.apache.pulsar.broker.authorization.PulsarAuthorizationProvider");
            data.put("authenticationProviders", List.of("org.apache.pulsar.broker.authentication.AuthenticationProviderToken",
                    "org.apache.pulsar.broker.authentication.AuthenticationProviderTls"));
            data.put("clientAuthenticationPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
            data.put("clientAuthenticationParameters", "file:///pulsar/token-superuser/superuser.jwt");
            data.put("superUserRoles", new TreeSet<>(tokenConfig.getSuperUserRoles()));
            data.put("properties", Map.of(
                    "tokenPublicKey", "file:///pulsar/token-public-key/%s".formatted(tokenConfig.getPublicKeyFile())
            ));
        }
        final String brokerServiceUrl = getBrokerServiceUrl();
        final String brokerWebServiceUrl = getBrokerWebServiceUrl();

        data.put("pulsarServiceUrl", brokerServiceUrl);
        data.put("pulsarWebServiceUrl", brokerWebServiceUrl);
        data.put("downloadDirectory", "/tmp/pulsar_functions");
        data.put("pulsarFunctionsNamespace", "public/functions");
        data.put("functionMetadataTopicName", "metadata");
        data.put("clusterCoordinationTopicName", "coordinate");
        data.put("numHttpServerThreads", "16");
        data.put("schedulerClassName", "org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler");
        data.put("functionAssignmentTopicName", "assignments");
        data.put("failureCheckFreqMs", "30000");
        data.put("rescheduleTimeoutMs", "60000");
        data.put("initialBrokerReconnectMaxRetries", "60");
        data.put("assignmentWriteMaxRetries", "60");
        data.put("instanceLivenessCheckFreqMs", "30000");
        data.put("topicCompactionFrequencySec", "1800");
        data.put("includeStandardPrometheusMetrics", "true");
        data.put("connectorsDirectory", "./connectors");

        switch (spec.getRuntime()) {
            case "kubernetes":
                data.put("functionRuntimeFactoryClassName",
                        "org.apache.pulsar.functions.runtime.kubernetes.KubernetesRuntimeFactory");
                data.put("functionRuntimeFactoryConfigs", Map.of("jobNamespace", namespace,
                        "pulsarDockerImageName", spec.getImage(),
                        "pulsarRootDir", "/pulsar",
                        "submittingInsidePod", true,
                        "pulsarServiceUrl", brokerServiceUrl,
                        "pulsarAdminUrl", "https://%s.%s:6750/"
                                .formatted(resourceName, getServiceDnsSuffix()),
                        "percentMemoryPadding", 10)
                );
                break;
            case "process":
                data.put("functionRuntimeFactoryClassName",
                        "org.apache.pulsar.functions.runtime.process.ProcessRuntimeFactory");
                data.put("functionRuntimeFactoryConfigs", Map.of());
                break;
            default:
                throw new IllegalArgumentException("runtime not supported '" + spec.getRuntime() + "', "
                        + "only process and kubernetes");
        }
        if (spec.getConfig() != null) {
            data.putAll(spec.getConfig());
        }

        if (!data.containsKey("numFunctionPackageReplicas")) {
            data.put("numFunctionPackageReplicas", "2");
        }

        final ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels()).endMetadata()
                .withData(Map.of("functions_worker.yml", SerializationUtil.writeAsYaml(data)))
                .build();
        patchResource(configMap);
        this.configMap = configMap;

    }

    public void patchStatefulSet() {
        if (!isComponentEnabled()) {
            log.warn("Got replicas=0, deleting sts");
            deleteStatefulSet();
            return;
        }
        final StatefulSet statefulSet = generateStatefulSet();
        patchResource(statefulSet);
    }

    public StatefulSet generateStatefulSet() {
        if (!isComponentEnabled()) {
            return null;
        }

        Map<String, String> labels = getLabels();
        Map<String, String> allAnnotations = getDefaultAnnotations();
        Objects.requireNonNull(configMap, "ConfigMap should have been created at this point");
        addConfigMapChecksumAnnotation(configMap, allAnnotations);
        Objects.requireNonNull(extraConfigMap, "ConfigMap (extra) should have been created at this point");
        addConfigMapChecksumAnnotation(extraConfigMap, allAnnotations);
        if (spec.getAnnotations() != null) {
            allAnnotations.putAll(spec.getAnnotations());
        }

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        addTlsVolumesIfEnabled(volumeMounts, volumes, getTlsSecretNameForBroker());
        if (isAuthTokenEnabled()) {
            addSecretTokenVolume(volumeMounts, volumes, "superuser");
            addSecretTokenVolume(volumeMounts, volumes, "public-key");
        }
        volumes.add(new VolumeBuilder()
                .withName("config-volume")
                .withNewConfigMap()
                .withName(resourceName)
                .endConfigMap()
                .build());

        volumeMounts.add(
                new VolumeMountBuilder()
                        .withName("config-volume")
                        .withMountPath("/pulsar/funcconf/functions_worker.yml")
                        .withSubPath("functions_worker.yml")
                        .build()
        );

        final String logsVolumeName = "%s-%s".formatted(resourceName, spec.getLogsVolume().getName());
        volumeMounts.add(
                new VolumeMountBuilder()
                        .withName(logsVolumeName)
                        .withMountPath("/pulsar/logs")
                        .build()
        );

        List<Container> initContainers = new ArrayList<>();
        if (spec.getInitContainer() != null) {
            volumes.add(
                    new VolumeBuilder()
                            .withName("lib-data")
                            .withNewEmptyDir().endEmptyDir()
                            .build()
            );
            volumeMounts.add(
                    new VolumeMountBuilder()
                            .withName("lib-data")
                            .withMountPath(spec.getInitContainer().getEmptyDirPath())
                            .build()
            );
            initContainers.add(new ContainerBuilder()
                    .withName("add-libs")
                    .withImage(spec.getInitContainer().getImage())
                    .withImagePullPolicy(spec.getInitContainer().getImagePullPolicy())
                    .withCommand(spec.getInitContainer().getCommand())
                    .withArgs(spec.getInitContainer().getArgs())
                    .withVolumeMounts(new VolumeMountBuilder()
                            .withName("lib-data")
                            .withMountPath(spec.getInitContainer().getEmptyDirPath())
                            .build())
                    .build());
        }

        String mainArg = "";
        if (isAuthTokenEnabled()) {
            mainArg += "cat /pulsar/token-superuser/superuser.jwt | tr -d '\\n' > /pulsar/token-superuser-stripped"
                    + ".jwt && ";
        }
        if (isTlsEnabledGlobally()) {
            mainArg += "openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key "
                    + "-out /pulsar/tls-pk8.key -nocrypt && "
                    + ". /pulsar/tools/certconverter.sh && ";
        }
        mainArg += "bin/apply-config-from-env.py conf/broker.conf && "
                + "cp -f funcconf/functions_worker.yml conf/functions_worker.yml && "
                + "export PF_workerHostname=\"${workerHostname}.%s\" && ".formatted(resourceName)
                + "bin/gen-yml-from-env.py conf/functions_worker.yml && "
                + "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar functions-worker";

        List<ContainerPort> containerPorts = new ArrayList<>();
        containerPorts.add(new ContainerPortBuilder()
                .withName("http")
                .withContainerPort(6750)
                .build()
        );
        containerPorts.add(
                new ContainerPortBuilder()
                        .withName("https")
                        .withContainerPort(6751)
                        .build()
        );

        List<EnvVar> env = new ArrayList<>();
        env.add(new EnvVarBuilder()
                .withName("workerHostname")
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath("metadata.name")
                .endFieldRef()
                .endValueFrom()
                .build());
        env.add(new EnvVarBuilder()
                .withName("PF_workerId")
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath("metadata.name")
                .endFieldRef()
                .endValueFrom()
                .build());

        List<Container> containers = List.of(
                new ContainerBuilder()
                        .withName(resourceName)
                        .withImage(spec.getImage())
                        .withImagePullPolicy(spec.getImagePullPolicy())
                        .withLivenessProbe(createLivenessProbe())
                        .withReadinessProbe(createReadinessProbe())
                        .withResources(spec.getResources())
                        .withCommand("sh", "-c")
                        .withArgs(mainArg)
                        .withPorts(containerPorts)
                        .withEnv(env)
                        .withEnvFrom(new EnvFromSourceBuilder()
                                .withNewConfigMapRef()
                                .withName("%s-extra".formatted(resourceName))
                                .endConfigMapRef()
                                .build())
                        .withVolumeMounts(volumeMounts)
                        .build()
        );


        List<PersistentVolumeClaim> persistentVolumeClaims = new ArrayList<>();
        if (global.getPersistence()) {
            persistentVolumeClaims.add(
                    createPersistentVolumeClaim(logsVolumeName, spec.getLogsVolume())
            );
        } else {
            volumes.add(new VolumeBuilder()
                    .withName(logsVolumeName)
                    .withNewEmptyDir().endEmptyDir()
                    .build());
        }


        final StatefulSet statefulSet = new StatefulSetBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withServiceName(resourceName)
                .withReplicas(spec.getReplicas())
                .withNewSelector()
                .withMatchLabels(getMatchLabels())
                .endSelector()
                .withUpdateStrategy(spec.getUpdateStrategy())
                .withPodManagementPolicy(spec.getPodManagementPolicy())
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .withAnnotations(allAnnotations)
                .endMetadata()
                .withNewSpec()
                .withDnsConfig(global.getDnsConfig())
                .withImagePullSecrets(spec.getImagePullSecrets())
                .withServiceAccountName(resourceName)
                .withNodeSelector(spec.getNodeSelectors())
                .withTerminationGracePeriodSeconds(spec.getGracePeriod().longValue())
                .withInitContainers(initContainers)
                .withNewSecurityContext()
                .withFsGroup(0L)
                .endSecurityContext()
                .withContainers(containers)
                .withVolumes(volumes)
                .endSpec()
                .endTemplate()
                .withVolumeClaimTemplates(persistentVolumeClaims)
                .endSpec()
                .build();
        return statefulSet;
    }

    private Probe createReadinessProbe() {
        final ProbeConfig specProbe = spec.getProbe();
        if (specProbe == null) {
            return null;
        }
        return new ProbeBuilder()
                .withNewTcpSocket()
                .withNewPort().withValue(6750).endPort()
                .endTcpSocket()
                .withInitialDelaySeconds(specProbe.getInitial())
                .withPeriodSeconds(specProbe.getPeriod())
                .withTimeoutSeconds(specProbe.getTimeout())
                .build();
    }


    private Probe createLivenessProbe() {
        final ProbeConfig specProbe = spec.getProbe();
        if (specProbe == null) {
            return null;
        }
        final String authHeader = isAuthTokenEnabled()
                ? "-H \"Authorization: Bearer $(cat /pulsar/token-superuser/superuser.jwt | tr -d '\\r')\"" : "";
        return new ProbeBuilder()
                .withNewExec()
                .withCommand("sh", "-c", "curl -s --max-time %d --fail %s http://localhost:6750/metrics/ > /dev/null"
                        .formatted(specProbe.getTimeout(), authHeader))
                .endExec()
                .withInitialDelaySeconds(specProbe.getInitial())
                .withPeriodSeconds(specProbe.getPeriod())
                .withTimeoutSeconds(specProbe.getTimeout())
                .build();
    }

    public void patchPodDisruptionBudget() {
        createPodDisruptionBudgetIfEnabled(spec.getPdb());
    }

    public void patchStorageClass() {
        createStorageClassIfNeeded(spec.getLogsVolume());
    }

    public void patchRBAC() {
        if (!spec.getRbac().getCreate()) {
            return;
        }
        boolean namespaced = spec.getRbac().getNamespaced();
        List<PolicyRule> rules = new ArrayList<>();
        rules.add(
                new PolicyRuleBuilder()
                        .withApiGroups("")
                        .withResources("pods")
                        .withVerbs("list")
                        .build()
        );
        rules.add(
                new PolicyRuleBuilder()
                        .withApiGroups("")
                        .withResources("secrets")
                        .withVerbs("*")
                        .build()
        );
        rules.add(
                new PolicyRuleBuilder()
                        .withApiGroups("")
                        .withResources("services")
                        .withVerbs("get", "create", "delete")
                        .build()
        );
        rules.add(
                new PolicyRuleBuilder()
                        .withApiGroups("apps")
                        .withResources("statefulsets")
                        .withVerbs("get", "create", "delete")
                        .build()
        );

        final ServiceAccount serviceAccount = new ServiceAccountBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .endMetadata()
                .build();
        patchResource(serviceAccount);
        patchServiceAccountSingleRole(namespaced, rules, resourceName);
    }

}

