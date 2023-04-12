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
package com.datastax.oss.pulsaroperator.controllers.broker;

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbesConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ResourceSetConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class BrokerResourcesFactory extends BaseResourcesFactory<BrokerSetSpec> {

    public static final String BROKER_DEFAULT_SET = "broker";

    public static final int DEFAULT_HTTP_PORT = 8080;
    public static final int DEFAULT_PULSAR_PORT = 6650;
    public static final int DEFAULT_HTTPS_PORT = 8443;
    public static final int DEFAULT_PULSARSSL_PORT = 6651;
    public static final int KAFKA_SSL_PORT = 9093;
    public static final int KAFKA_PORT = 9092;
    public static final int KAFKA_SCHEMA_REGISTRY_PORT = 8081;

    public static String getComponentBaseName(GlobalSpec globalSpec) {
        return globalSpec.getComponents().getBrokerBaseName();
    }


    public static List<String> getContainerNames(String resourceName) {
        return List.of(getMainContainerName(resourceName));
    }

    public static String getMainContainerName(String resourceName) {
        return resourceName;
    }

    private ConfigMap configMap;
    private final String brokerSet;

    public BrokerResourcesFactory(KubernetesClient client, String namespace,
                                  String brokerSetName, BrokerSetSpec spec, GlobalSpec global,
                                  OwnerReference ownerReference) {
        super(client, namespace, getResourceName(global.getName(),
                        getComponentBaseName(global), Objects.requireNonNull(brokerSetName),
                        spec.getOverrideResourceName()),
                spec, global, ownerReference);
        brokerSet = brokerSetName;
    }

    @Override
    protected String getComponentBaseName() {
        return getComponentBaseName(global);
    }

    public static String getResourceName(String clusterName, String baseName, String brokerSetName,
                                         String overrideResourceName) {
        Objects.requireNonNull(brokerSetName);
        if (overrideResourceName != null) {
            return overrideResourceName;
        }
        if (BROKER_DEFAULT_SET.equals(brokerSetName)) {
            return "%s-%s".formatted(clusterName, baseName);
        }
        return "%s-%s-%s".formatted(clusterName, baseName, brokerSetName);
    }

    @Override
    protected boolean isComponentEnabled() {
        return spec.getReplicas() > 0;
    }

    public void patchService() {

        final BrokerSetSpec.ServiceConfig serviceSpec = spec.getService();

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
                .withName("pulsar")
                .withPort(DEFAULT_PULSAR_PORT)
                .build());
        final boolean tlsEnabledOnBrokerSet = isTlsEnabledOnBrokerSet(brokerSet);
        if (tlsEnabledOnBrokerSet) {
            ports.add(new ServicePortBuilder()
                    .withName("https")
                    .withPort(DEFAULT_HTTPS_PORT)
                    .build());
            ports.add(new ServicePortBuilder()
                    .withName("pulsarssl")
                    .withPort(DEFAULT_PULSARSSL_PORT)
                    .build());
        }
        if (spec.getKafka() != null && spec.getKafka().getEnabled() && spec.getKafka().getExposePorts()) {
            if (tlsEnabledOnBrokerSet) {
                ports.add(new ServicePortBuilder()
                        .withName("kafkassl")
                        .withPort(KAFKA_SSL_PORT)
                        .build());
            } else {
                ports.add(new ServicePortBuilder()
                        .withName("kafkaplaintext")
                        .withPort(KAFKA_PORT)
                        .build());
            }
            ports.add(new ServicePortBuilder()
                    .withName("kafkaschemaregistry")
                    .withPort(KAFKA_SCHEMA_REGISTRY_PORT)
                    .build());

        }
        if (serviceSpec.getAdditionalPorts() != null) {
            ports.addAll(serviceSpec.getAdditionalPorts());
        }

        final Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels(spec.getLabels()))
                .withAnnotations(annotations)
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


    public void patchConfigMap() {
        Map<String, String> data = new HashMap<>();
        final String zkServers = getZkServers();
        data.put("zookeeperServers", zkServers);
        data.put("configurationStoreServers", zkServers);
        data.put("clusterName", global.getName());

        if (isAuthTokenEnabled()) {
            data.put("authParams", "file:///pulsar/token-superuser-stripped.jwt");
            data.put("authPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
            data.put("authorizationEnabled", "true");
            data.put("authenticationEnabled", "true");
            data.put("authenticationProviders", "org.apache.pulsar.broker.authentication.AuthenticationProviderToken");
            final AuthConfig.TokenAuthenticationConfig tokenConfig = global.getAuth().getToken();
            data.put("proxyRoles", tokenConfig.proxyRolesAsString());
            data.put("superUserRoles", tokenConfig.superUserRolesAsString());
            data.put("tokenPublicKey", "file:///pulsar/token-public-key/%s".formatted(tokenConfig.getPublicKeyFile()));
            data.put("brokerClientAuthenticationPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
            data.put("brokerClientAuthenticationParameters", "file:///pulsar/token-superuser/superuser.jwt");
        }

        final boolean tlsEnabledOnBrokerSet = isTlsEnabledOnBrokerSet(brokerSet);
        if (tlsEnabledOnBrokerSet) {
            data.put("tlsEnabled", "true");
            data.put("tlsCertificateFilePath", "/pulsar/certs/tls.crt");
            data.put("tlsKeyFilePath", " /pulsar/tls-pk8.key");
            final String fullCaPath = getFullCaPath();
            data.put("tlsTrustCertsFilePath", fullCaPath);
            data.put("brokerServicePortTls", DEFAULT_PULSARSSL_PORT + "");
            data.put("brokerClientTlsEnabled", "true");
            data.put("webServicePortTls", DEFAULT_HTTPS_PORT + "");
            data.put("brokerClientTrustCertsFilePath", fullCaPath);
            data.put("brokerClient_tlsHostnameVerificationEnable", "true");
        }
        if (isTlsEnabledOnBookKeeper()) {
            data.put("bookkeeperTLSClientAuthentication", "true");
            data.put("bookkeeperTLSKeyFileType", "PEM");
            data.put("bookkeeperTLSKeyFilePath", "/pulsar/tls-pk8.key");
            data.put("bookkeeperTLSCertificateFilePath", "/pulsar/certs/tls.crt");
            data.put("bookkeeperTLSTrustCertsFilePath", getFullCaPath());
            data.put("bookkeeperTLSTrustCertTypes", "PEM");
            // See this BookKeeper PR for why this is necessary: https://github.com/apache/bookkeeper/pull/2300
            data.put("bookkeeperUseV2WireProtocol", "false");
        }
        if (spec.getFunctionsWorkerEnabled()) {
            data.put("functionsWorkerEnabled", "true");
            data.put("pulsarFunctionsCluster", global.getName());

            // Since function worker connects on localhost, we can always non-TLS ports
            // when running with the broker
            data.put("pulsarServiceUrl", "pulsar://localhost:%d".formatted(DEFAULT_PULSAR_PORT));
            data.put("pulsarWebServiceUrl", "http://localhost:%d".formatted(DEFAULT_HTTP_PORT));
        }
        if (spec.getTransactions() != null && spec.getTransactions().getEnabled()) {
            data.put("transactionCoordinatorEnabled", "true");
        }
        if (spec.getKafka() != null && spec.getKafka().getEnabled()) {
            data.put("protocolHandlerDirectory", "./protocols");
            data.put("messagingProtocols", "kafka");
            data.put("kopSchemaRegistryEnable", "true");
            data.put("kopSchemaRegistryPort", KAFKA_SCHEMA_REGISTRY_PORT + "");
            data.put("kafkaTransactionCoordinatorEnabled", "true");
            data.put("kafkaNamespace", "kafka");
            data.put("kafkaListeners", "PLAINTEXT://0.0.0.0:%d".formatted(KAFKA_PORT));
            data.put("kafkaAdvertisedListeners", "PLAINTEXT://advertisedAddress:%d".formatted(KAFKA_PORT));
            data.put("brokerEntryMetadataInterceptors",
                    "org.apache.pulsar.common.intercept.AppendIndexMetadataInterceptor,org.apache.pulsar.common"
                            + ".intercept.AppendBrokerTimestampMetadataInterceptor");
            data.put("brokerDeleteInactiveTopicsEnabled", "false");

            if (tlsEnabledOnBrokerSet) {
                data.put("kopSchemaRegistryEnableTls", "true");
                data.put("kafkaListeners", "SASL_SSL://0.0.0.0:%d".formatted(KAFKA_SSL_PORT));
                data.put("kafkaAdvertisedListeners", "SASL_SSL://advertisedAddress:%d".formatted(KAFKA_SSL_PORT));
                data.put("kopSslTruststoreLocation", "/pulsar/tls.truststore.jks");
            }
        }

        data.put("allowAutoTopicCreationType", "non-partitioned");
        data.put("PULSAR_MEM",
                "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler"
                        + ".linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        data.put("PULSAR_GC", "-XX:+UseG1GC");
        data.put("PULSAR_LOG_LEVEL", "info");
        data.put("PULSAR_LOG_ROOT_LEVEL", "info");
        data.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        data.put("brokerDeduplicationEnabled", "false");
        data.put("exposeTopicLevelMetricsInPrometheus", "true");
        data.put("exposeConsumerLevelMetricsInPrometheus", "false");
        data.put("backlogQuotaDefaultRetentionPolicy", "producer_exception");
        data.put("bookkeeperClientRegionawarePolicyEnabled", "true");

        if (spec.getProbes() != null
                && (!spec.getProbes().getUseHealthCheckForLiveness()
                || !spec.getProbes().getUseHealthCheckForReadiness())) {
            data.put("statusFilePath", "/pulsar/status");
        }

        appendConfigData(data, spec.getConfig());

        if (!data.containsKey("bookkeeperClientMinNumRacksPerWriteQuorum")
                && data.containsKey("managedLedgerDefaultWriteQuorum")) {
            data.put("bookkeeperClientMinNumRacksPerWriteQuorum", data.get("managedLedgerDefaultWriteQuorum"));
        }

        final ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels(spec.getLabels()))
                .withAnnotations(getAnnotations(spec.getAnnotations()))
                .endMetadata()
                .withData(handleConfigPulsarPrefix(data))
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
        Map<String, String> podLabels = getPodLabels(spec.getPodLabels());
        Objects.requireNonNull(configMap, "ConfigMap should have been created at this point");
        Map<String, String> podAnnotations = getPodAnnotations(spec.getPodAnnotations(), configMap);
        Map<String, String> annotations = getAnnotations(spec.getAnnotations());

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        addAdditionalVolumes(spec.getAdditionalVolumes(), volumeMounts, volumes);

        final boolean tlsEnabledOnBroker = isTlsEnabledOnBrokerSet(brokerSet);
        final boolean tlsEnabledOnZooKeeper = isTlsEnabledOnZooKeeper();

        if (tlsEnabledOnBroker || tlsEnabledOnZooKeeper) {
            addTlsVolumesIfEnabled(volumeMounts, volumes, getTlsSecretNameForBroker());
        }
        if (isAuthTokenEnabled()) {
            addSecretTokenVolume(volumeMounts, volumes, "public-key");
            addSecretTokenVolume(volumeMounts, volumes, "superuser");
        }
        String mainArg = "";

        if (tlsEnabledOnBroker) {
            mainArg += "openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key "
                    + "-out /pulsar/tls-pk8.key -nocrypt && ";
        }

        if (tlsEnabledOnBroker || tlsEnabledOnZooKeeper) {
            mainArg += generateCertConverterScript() + " && ";
        }
        mainArg += "bin/apply-config-from-env.py conf/broker.conf && "
                + "bin/apply-config-from-env.py conf/client.conf && "
                + "bin/gen-yml-from-env.py conf/functions_worker.yml && ";

        if (isAuthTokenEnabled()) {
            mainArg += "cat /pulsar/token-superuser/superuser.jwt | tr -d '\\n' > /pulsar/token-superuser-stripped"
                    + ".jwt && ";

        }

        mainArg += "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar broker";


        List<ContainerPort> containerPorts = new ArrayList<>();
        containerPorts.add(new ContainerPortBuilder()
                .withName("http")
                .withContainerPort(DEFAULT_HTTP_PORT)
                .build()
        );
        containerPorts.add(
                new ContainerPortBuilder()
                        .withName("pulsar")
                        .withContainerPort(DEFAULT_PULSAR_PORT)
                        .build()
        );
        if (tlsEnabledOnBroker) {
            containerPorts.add(new ContainerPortBuilder()
                    .withName("https")
                    .withContainerPort(DEFAULT_HTTPS_PORT)
                    .build()
            );
            containerPorts.add(
                    new ContainerPortBuilder()
                            .withName("pulsarssl")
                            .withContainerPort(DEFAULT_PULSARSSL_PORT)
                            .build()
            );
        }


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
                .withEnvFrom(new EnvFromSourceBuilder()
                        .withNewConfigMapRef()
                        .withName(resourceName)
                        .endConfigMapRef()
                        .build())
                .withVolumeMounts(volumeMounts)
                .withEnv(spec.getEnv())
                .build();
        final List<Container> containers = getSidecars(spec.getSidecars());
        containers.add(mainContainer);
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
                .withServiceAccountName(spec.getServiceAccountName())
                .withNodeSelector(spec.getNodeSelectors())
                .withAffinity(getAffinity(
                        spec.getNodeAffinity(),
                        spec.getAntiAffinity(),
                        spec.getMatchLabels(),
                        getRack()
                ))
                .withTerminationGracePeriodSeconds(spec.getGracePeriod().longValue())
                .withPriorityClassName(global.getPriorityClassName())
                .withInitContainers(getInitContainers(spec.getInitContainers()))
                .withContainers(containers)
                .withVolumes(volumes)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        return statefulSet;
    }

    public void createTransactionsInitJobIfNeeded() {
        if (isJobCompleted(resourceName)) {
            return;
        }
        final BrokerSetSpec.TransactionCoordinatorConfig transactions = spec.getTransactions();
        if (transactions == null || !transactions.getEnabled()) {
            return;
        }

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        final boolean tlsEnabled = isTlsEnabledOnBrokerSet(brokerSet);
        if (tlsEnabled) {
            addTlsVolumesIfEnabled(volumeMounts, volumes, getTlsSecretNameForBroker());
        }

        String mainArgs = "";
        if (tlsEnabled) {
            mainArgs += generateCertConverterScript() + " && ";
        }

        final String clusterName = global.getName();
        final String zkServers = getZkServers();

        mainArgs += """
                bin/pulsar initialize-transaction-coordinator-metadata --cluster %s \\
                    --configuration-store %s \\
                    --initial-num-transaction-coordinators %d
                """.formatted(clusterName, zkServers, transactions.getPartitions());

        final Container container = new ContainerBuilder()
                .withName(resourceName)
                .withImage(spec.getImage())
                .withImagePullPolicy(spec.getImagePullPolicy())
                .withVolumeMounts(volumeMounts)
                .withResources(transactions.getInitJob() == null ? null : transactions.getInitJob().getResources())
                .withCommand("sh", "-c")
                .withArgs(mainArgs)
                .build();


        final Job job = new JobBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels(spec.getLabels()))
                .withAnnotations(getAnnotations(spec.getAnnotations()))
                .endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewMetadata()
                .withAnnotations(getPodAnnotations(spec.getPodAnnotations(), null))
                .withLabels(getPodLabels(spec.getPodLabels()))
                .endMetadata()
                .withNewSpec()
                .withTolerations(spec.getTolerations())
                .withDnsConfig(global.getDnsConfig())
                .withImagePullSecrets(spec.getImagePullSecrets())
                .withNodeSelector(spec.getNodeSelectors())
                .withPriorityClassName(global.getPriorityClassName())
                .withVolumes(volumes)
                .withContainers(List.of(container))
                .withRestartPolicy("OnFailure")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        patchResource(job);
    }

    private Probe createLivenessProbe() {
        return createProbe(spec.getProbes().getLiveness(), true);
    }

    private Probe createReadinessProbe() {
        return createProbe(spec.getProbes().getReadiness(), false);
    }

    private Probe createProbe(ProbesConfig.ProbeConfig specProbe, boolean liveness) {
        if (specProbe == null || !specProbe.getEnabled()) {
            return null;
        }
        final String authHeader = computeCurlAuthHeader(global);
        final String uri = liveness ? (spec.getProbes().getUseHealthCheckForLiveness()
                ? "admin/v2/brokers/health" : "status.html") :
                (spec.getProbes().getUseHealthCheckForReadiness() ? "admin/v2/brokers/health" : "metrics/");

        return newProbeBuilder(specProbe)
                .withNewExec()
                .withCommand("sh", "-c",
                        "curl -s --max-time %d --fail %s http://localhost:8080/%s > /dev/null"
                                .formatted(specProbe.getTimeoutSeconds(), authHeader, uri))
                .endExec()
                .build();
    }

    public static String computeCurlAuthHeader(GlobalSpec globalSpec) {
        return isAuthTokenEnabled(globalSpec)
                ? "-H \"Authorization: Bearer $(cat /pulsar/token-superuser/superuser.jwt | tr -d '\\r')\"" : "";
    }

    public void patchPodDisruptionBudget() {
        createPodDisruptionBudgetIfEnabled(
                spec.getPdb(),
                spec.getAnnotations(),
                spec.getLabels(),
                spec.getMatchLabels());
    }

    @Override
    protected Map<String, String> getLabels(Map<String, String> customLabels) {
        final Map<String, String> labels = super.getLabels(customLabels);
        labels.put(CRDConstants.LABEL_RESOURCESET, brokerSet);
        setRackLabel(labels);
        return labels;
    }

    @Override
    protected Map<String, String> getPodLabels(Map<String, String> customLabels) {
        final Map<String, String> labels = super.getPodLabels(customLabels);
        labels.put(CRDConstants.LABEL_RESOURCESET, brokerSet);
        setRackLabel(labels);
        return labels;
    }

    @Override
    protected Map<String, String> getMatchLabels(Map<String, String> customMatchLabels) {
        final Map<String, String> matchLabels = super.getMatchLabels(customMatchLabels);
        if (!brokerSet.equals(BROKER_DEFAULT_SET)) {
            matchLabels.put(CRDConstants.LABEL_RESOURCESET, brokerSet);
        }
        setRackLabel(matchLabels);
        return matchLabels;
    }

    private void setRackLabel(Map<String, String> labels) {
        final String rack = getRack();
        if (rack != null) {
            labels.put(CRDConstants.LABEL_RACK, rack);
        }
    }

    private String getRack() {
        if (global.getResourceSets() != null) {
            final ResourceSetConfig resourceSet = global.getResourceSets().get(brokerSet);
            if (resourceSet != null) {
                return resourceSet.getRack();
            }
        }
        return null;
    }
}
