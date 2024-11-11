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
package com.datastax.oss.kaap.controllers.function;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.configs.AuthConfig;
import com.datastax.oss.kaap.crds.configs.ProbesConfig;
import com.datastax.oss.kaap.crds.function.FunctionsWorkerSpec;
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
import org.apache.commons.lang3.BooleanUtils;

@JBossLog
public class FunctionsWorkerResourcesFactory extends BaseResourcesFactory<FunctionsWorkerSpec> {

    public static final int DEFAULT_HTTP_PORT = 6750;
    public static final int DEFAULT_HTTPS_PORT = 6751;
    public static final String ENV_WORKER_HOSTNAME = "workerHostname";
    public static final String ENV_WORKER_ID = "PF_workerId";
    public static final List<String> DEFAULT_ENV = List.of(ENV_WORKER_HOSTNAME, ENV_WORKER_ID);

    public static String getComponentBaseName(GlobalSpec globalSpec) {
        return globalSpec.getComponents().getFunctionsWorkerBaseName();
    }

    public static List<String> getContainerNames(String resourceName) {
        return List.of(getMainContainerName(resourceName));
    }

    private static String getMainContainerName(String resourceName) {
        return resourceName;
    }

    public static String getResourceName(String clusterName, String baseName) {
        return "%s-%s".formatted(clusterName, baseName);
    }

    public static String getResourceName(GlobalSpec globalSpec, String baseName) {
        return getResourceName(globalSpec.getName(), baseName);
    }

    private ConfigMap configMap;
    private ConfigMap extraConfigMap;

    public FunctionsWorkerResourcesFactory(KubernetesClient client, String namespace,
                                           FunctionsWorkerSpec spec, GlobalSpec global,
                                           OwnerReference ownerReference) {
        super(client, namespace, getResourceName(global, getComponentBaseName(global)), spec, global, ownerReference);
    }

    @Override
    protected String getComponentBaseName() {
        return getComponentBaseName(global);
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
                .withPort(DEFAULT_HTTP_PORT)
                .build());
        ports.add(new ServicePortBuilder()
                .withName("https")
                .withPort(DEFAULT_HTTPS_PORT)
                .build());
        if (serviceSpec.getAdditionalPorts() != null) {
            ports.addAll(serviceSpec.getAdditionalPorts());
        }

        final Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels(spec.getLabels()))
                .endMetadata()
                .withNewSpec()
                .withPorts(ports)
                .withClusterIP("None")
                .withType(serviceSpec.getType())
                .withSelector(getMatchLabels(spec.getMatchLabels()))
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
                .withPort(DEFAULT_HTTP_PORT)
                .build());
        ports.add(new ServicePortBuilder()
                .withName("https")
                .withPort(DEFAULT_HTTPS_PORT)
                .build());
        if (serviceSpec.getAdditionalPorts() != null) {
            ports.addAll(serviceSpec.getAdditionalPorts());
        }

        final Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName("%s-ca".formatted(resourceName))
                .withNamespace(namespace)
                .withLabels(getLabels(spec.getLabels()))
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withPorts(ports)
                .withSelector(getMatchLabels(spec.getMatchLabels()))
                .endSpec()
                .build();

        patchResource(service);
    }

    public void patchExtraConfigMap() {

        Map<String, String> data = new HashMap<>();
        data.put("PULSAR_MEM", "-XX:+ExitOnOutOfMemoryError");
        data.put("PULSAR_LOG_LEVEL", "info");
        data.put("PULSAR_LOG_ROOT_LEVEL", "info");
        data.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");

        if (isAuthTokenEnabled()) {
            final AuthConfig.TokenAuthenticationConfig tokenConfig = global.getAuth().getToken();
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
                .withLabels(getLabels(spec.getLabels()))
                .withAnnotations(getAnnotations(spec.getAnnotations()))
                .endMetadata()
                .withData(handleConfigPulsarPrefix(data))
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
        data.put("pulsarFunctionsCluster", global.getClusterName());
        data.put("workerId", resourceName);
        data.put(ENV_WORKER_HOSTNAME, resourceName);
        data.put("workerPort", "6750");
        if (isTlsEnabledOnBookKeeper()) {
            data.put("bookkeeperTLSClientAuthentication", "true");
        }
        if (isAuthTokenEnabled()) {
            final AuthConfig.TokenAuthenticationConfig tokenConfig = global.getAuth().getToken();
            data.put("authenticationEnabled", "true");
            data.put("authorizationEnabled", "true");
            data.put("authorizationProvider", "org.apache.pulsar.broker.authorization.PulsarAuthorizationProvider");
            data.put("authenticationProviders",
                    List.of("org.apache.pulsar.broker.authentication.AuthenticationProviderToken",
                            "org.apache.pulsar.broker.authentication.AuthenticationProviderTls"));
            data.put("clientAuthenticationPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
            data.put("clientAuthenticationParameters", "file:///pulsar/token-superuser/superuser.jwt");
            data.put("proxyRoles", tokenConfig.getProxyRoles());
            data.put("superUserRoles", new TreeSet<>(tokenConfig.getSuperUserRoles()));
            data.put("properties", Map.of(
                    "tokenPublicKey", "file:///pulsar/token-public-key/%s".formatted(tokenConfig.getPublicKeyFile())
            ));
        }
        if (isTlsEnabledOnFunctionsWorker()) {
            data.put("tlsEnabled", "true");
            data.put("workerPortTls", "6751");
            data.put("tlsCertificateFilePath", "/pulsar/certs/tls.crt");
            final String fullCaPath = getFullCaPath();
            data.put("tlsTrustCertsFilePath", fullCaPath);
            data.put("brokerClientTrustCertsFilePath", fullCaPath);
            data.put("tlsKeyFilePath", "/pulsar/tls-pk8.key");
        }
        final boolean enabledWithBroker = isTlsEnabledFromFunctionsWorkerToBroker();
        if (enabledWithBroker) {
            data.put("useTls", "true");
            data.put("tlsEnabledWithKeyStore", "true");
            data.put("tlsKeyStore", "/pulsar/tls.keystore.jks");
            data.put("tlsTrustStore", "/pulsar/tls.truststore.jks");
            data.put("tlsEnableHostnameVerification", "true");
        }
        if (isTlsEnabledOnBookKeeper()) {
            data.put("bookkeeperTLSClientAuthentication", "true");
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
                        "pulsarAdminUrl", getFunctionsWorkerServiceUrl(),
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
            spec.getConfig().forEach((k, v) -> {
                // keep PULSAR_PREFIX_ but skip PULSAR_MEM and family. They are handled by the extra config map
                if (k.startsWith(BaseResourcesFactory.CONFIG_PULSAR_PREFIX) || !k.startsWith("PULSAR_")) {
                    data.put(k, v);
                }
            });
        }
        if (!data.containsKey("numFunctionPackageReplicas")) {
            data.put("numFunctionPackageReplicas", "2");
        }

        final ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels(spec.getLabels()))
                .withAnnotations(getAnnotations(spec.getAnnotations()))
                .endMetadata()
                .withData(Map.of("functions_worker.yml", SerializationUtil.writeAsYaml(data)))
                .build();
        patchResource(configMap);
        this.configMap = configMap;

    }

    public void patchStatefulSet() {
        if (!isComponentEnabled()) {
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

        Map<String, String> labels = getLabels(spec.getLabels());
        boolean skipVolumeClaimLabels = BooleanUtils.isTrue(spec.getSkipVolumeClaimLabels());
        Map<String, String> podLabels = getPodLabels(spec.getPodLabels());
        Objects.requireNonNull(configMap, "ConfigMap should have been created at this point");
        Map<String, String> annotations = getAnnotations(spec.getAnnotations());
        Map<String, String> podAnnotations = getPodAnnotations(spec.getPodAnnotations(), configMap, 8080 + "");
        Objects.requireNonNull(extraConfigMap, "ConfigMap (extra) should have been created at this point");
        addConfigMapChecksumAnnotation(extraConfigMap, podAnnotations);


        final boolean tlsEnabledOnBroker = isTlsEnabledOnBroker();
        final boolean tlsEnabledOnFunctionsWorker = isTlsEnabledOnFunctionsWorker();
        final boolean enabledWithBroker = isTlsEnabledFromFunctionsWorkerToBroker();

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        addAdditionalVolumes(spec.getAdditionalVolumes(), volumeMounts, volumes);

        if (tlsEnabledOnFunctionsWorker || (tlsEnabledOnBroker && enabledWithBroker)) {
            addTlsVolumes(volumeMounts, volumes, getTlsSecretNameForFunctionsWorker());
        }
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

        String mainArg = "";
        if (isAuthTokenEnabled()) {
            mainArg += "cat /pulsar/token-superuser/superuser.jwt | tr -d '\\n' > /pulsar/token-superuser-stripped"
                    + ".jwt && ";
        }

        if (tlsEnabledOnBroker && enabledWithBroker) {
            mainArg += "openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key "
                    + "-out /pulsar/tls-pk8.key -nocrypt && "
                    + generateCertConverterScript() + " && ";
        }
        mainArg += "bin/apply-config-from-env.py conf/broker.conf && "
                + "cp -f funcconf/functions_worker.yml conf/functions_worker.yml && "
                + "export PF_workerHostname=\"${workerHostname}.%s\" && ".formatted(resourceName)
                + "bin/gen-yml-from-env.py conf/functions_worker.yml && "
                + "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar functions-worker";

        List<ContainerPort> containerPorts = new ArrayList<>();
        containerPorts.add(new ContainerPortBuilder()
                .withName("http")
                .withContainerPort(DEFAULT_HTTP_PORT)
                .build()
        );
        containerPorts.add(
                new ContainerPortBuilder()
                        .withName("https")
                        .withContainerPort(DEFAULT_HTTPS_PORT)
                        .build()
        );

        List<EnvVar> env = spec.getEnv() != null ? spec.getEnv() : new ArrayList<>();
        checkEnvListNotContains(env, DEFAULT_ENV);

        env.add(new EnvVarBuilder()
                .withName(ENV_WORKER_HOSTNAME)
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath("metadata.name")
                .endFieldRef()
                .endValueFrom()
                .build());
        env.add(new EnvVarBuilder()
                .withName(ENV_WORKER_ID)
                .withNewValueFrom()
                .withNewFieldRef()
                .withFieldPath("metadata.name")
                .endFieldRef()
                .endValueFrom()
                .build());

        final Container mainContainer = new ContainerBuilder()
                .withName(getMainContainerName(resourceName))
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
                .build();
        final List<Container> containers = getSidecars(spec.getSidecars());
        containers.add(mainContainer);


        List<PersistentVolumeClaim> persistentVolumeClaims = new ArrayList<>();
        if (global.getPersistence()) {
            persistentVolumeClaims.add(
                    createPersistentVolumeClaim(logsVolumeName, spec.getLogsVolume(),
                        labels, skipVolumeClaimLabels)
            );
        } else {
            volumes.add(new VolumeBuilder()
                    .withName(logsVolumeName)
                    .withNewEmptyDir().endEmptyDir()
                    .build());
        }


        final String serviceAccountName = spec.getServiceAccountName() != null ? spec.getServiceAccountName() : resourceName;
        final StatefulSet statefulSet = new StatefulSetBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(labels)
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withServiceName(resourceName)
                .withReplicas(spec.getReplicas())
                .withNewSelector()
                .withMatchLabels(getMatchLabels(spec.getMatchLabels()))
                .endSelector()
                .withUpdateStrategy(spec.getUpdateStrategy())
                .withPodManagementPolicy(spec.getPodManagementPolicy())
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(podLabels)
                .withAnnotations(podAnnotations)
                .endMetadata()
                .withNewSpec()
                .withTolerations(spec.getTolerations())
                .withDnsConfig(global.getDnsConfig())
                .withImagePullSecrets(spec.getImagePullSecrets())
                .withServiceAccountName(serviceAccountName)
                .withNodeSelector(spec.getNodeSelectors())
                .withAffinity(getAffinity(
                        spec.getNodeAffinity(),
                        spec.getAntiAffinity(),
                        spec.getMatchLabels()
                ))
                .withTerminationGracePeriodSeconds(spec.getGracePeriod().longValue())
                .withPriorityClassName(global.getPriorityClassName())
                .withInitContainers(getInitContainers(spec.getInitContainers()))
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
        final ProbesConfig.ProbeConfig specProbe = spec.getProbes().getReadiness();
        if (specProbe == null || !specProbe.getEnabled()) {
            return null;
        }
        return newProbeBuilder(specProbe)
                .withNewTcpSocket()
                .withNewPort().withValue(DEFAULT_HTTP_PORT).endPort()
                .endTcpSocket()
                .build();
    }


    private Probe createLivenessProbe() {
        final ProbesConfig.ProbeConfig specProbe = spec.getProbes().getLiveness();
        if (specProbe == null || !specProbe.getEnabled()) {
            return null;
        }
        final String authHeader = isAuthTokenEnabled()
                ? "-H \"Authorization: Bearer $(cat /pulsar/token-superuser/superuser.jwt | tr -d '\\r')\"" : "";
        return newProbeBuilder(specProbe)
                .withNewExec()
                .withCommand("sh", "-c", "curl -s --max-time %d --fail %s http://localhost:6750/metrics/ > /dev/null"
                        .formatted(specProbe.getTimeoutSeconds(), authHeader))
                .endExec()
                .build();
    }

    public void patchPodDisruptionBudget() {
        createPodDisruptionBudgetIfEnabled(spec.getPdb(), spec.getAnnotations(), spec.getLabels(),
                spec.getMatchLabels());
    }

    public void patchStorageClass() {
        createStorageClassIfNeeded(spec.getLogsVolume(), spec.getAnnotations(), spec.getLabels());
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
