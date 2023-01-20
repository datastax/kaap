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
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
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
public class BrokerResourcesFactory extends BaseResourcesFactory<BrokerSpec> {

    public static String getComponentBaseName(GlobalSpec globalSpec) {
        return globalSpec.getComponents().getBrokerBaseName();
    }

    private ConfigMap configMap;

    public BrokerResourcesFactory(KubernetesClient client, String namespace,
                                  BrokerSpec spec, GlobalSpec global,
                                  OwnerReference ownerReference) {
        super(client, namespace, spec, global, ownerReference);
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

        final BrokerSpec.ServiceConfig serviceSpec = spec.getService();

        Map<String, String> annotations = null;
        if (serviceSpec.getAnnotations() != null) {
            annotations = serviceSpec.getAnnotations();
        }
        List<ServicePort> ports = new ArrayList<>();
        ports.add(new ServicePortBuilder()
                .withName("http")
                .withPort(8080)
                .build());
        ports.add(new ServicePortBuilder()
                .withName("pulsar")
                .withPort(6650)
                .build());
        if (isTlsEnabledOnBroker()) {
            ports.add(new ServicePortBuilder()
                    .withName("https")
                    .withPort(8443)
                    .build());
            ports.add(new ServicePortBuilder()
                    .withName("pulsarssl")
                    .withPort(6651)
                    .build());
        }
        if (serviceSpec.getAdditionalPorts() != null) {
            ports.addAll(serviceSpec.getAdditionalPorts());
        }

        final Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels())
                .withAnnotations(annotations)
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

        if (isTlsEnabledOnBroker()) {
            data.put("tlsEnabled", "true");
            data.put("tlsCertificateFilePath", "/pulsar/certs/tls.crt");
            data.put("tlsKeyFilePath", " /pulsar/tls-pk8.key");
            data.put("tlsTrustCertsFilePath", "/pulsar/certs/ca.crt");
            data.put("brokerServicePortTls", "6651");
            data.put("brokerClientTlsEnabled", "true");
            data.put("webServicePortTls", "8443");
            data.put("brokerClientTrustCertsFilePath", "/pulsar/certs/ca.crt");
            data.put("brokerClient_tlsHostnameVerificationEnable", "true");
            // TODO: tls vs bookkeeper
        }
        if (spec.getFunctionsWorkerEnabled()) {
            data.put("functionsWorkerEnabled", "true");
            data.put("pulsarFunctionsCluster", global.getName());

            // Since function worker connects on localhost, we can always non-TLS ports
            // when running with the broker
            data.put("pulsarServiceUrl", "pulsar://localhost:6650");
            data.put("pulsarWebServiceUrl", "http://localhost:8080");
        }
        if (spec.getTransactions() != null && spec.getTransactions().getEnabled()) {
            data.put("transactionCoordinatorEnabled", "true");
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

        if (spec.getConfig() != null) {
            data.putAll(spec.getConfig());
        }

        final ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels()).endMetadata()
                .withData(handleConfigPulsarPrefix(data))
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
        if (spec.getAnnotations() != null) {
            allAnnotations.putAll(spec.getAnnotations());
        }

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        addAdditionalVolumes(spec.getAdditionalVolumes(), volumeMounts, volumes);

        addTlsVolumesIfEnabled(volumeMounts, volumes, getTlsSecretNameForBroker());
        if (isAuthTokenEnabled()) {
            addSecretTokenVolume(volumeMounts, volumes, "public-key");
            addSecretTokenVolume(volumeMounts, volumes, "superuser");
        }

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

        final Probe probe = createProbe();
        String mainArg = "";
        final boolean tlsEnabledOnBroker = isTlsEnabledOnBroker();
        if (tlsEnabledOnBroker) {
            mainArg += "openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key "
                    + "-out /pulsar/tls-pk8.key -nocrypt && "
                    + generateCertConverterScript() + " && ";
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
                .withContainerPort(8080)
                .build()
        );
        containerPorts.add(
                new ContainerPortBuilder()
                        .withName("pulsar")
                        .withContainerPort(6650)
                        .build()
        );
        if (tlsEnabledOnBroker) {
            containerPorts.add(new ContainerPortBuilder()
                    .withName("https")
                    .withContainerPort(8843)
                    .build()
            );
            containerPorts.add(
                    new ContainerPortBuilder()
                            .withName("pulsarssl")
                            .withContainerPort(6651)
                            .build()
            );
        }


        List<Container> containers = List.of(
                new ContainerBuilder()
                        .withName(resourceName)
                        .withImage(spec.getImage())
                        .withImagePullPolicy(spec.getImagePullPolicy())
                        .withLivenessProbe(probe)
                        .withReadinessProbe(probe)
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
                        .build()
        );
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
                .withTolerations(spec.getTolerations())
                .withDnsConfig(global.getDnsConfig())
                .withServiceAccountName(spec.getServiceAccountName())
                .withNodeSelector(spec.getNodeSelectors())
                .withTerminationGracePeriodSeconds(spec.getGracePeriod().longValue())
                .withInitContainers(initContainers)
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
        final BrokerSpec.TransactionCoordinatorConfig transactions = spec.getTransactions();
        if (transactions == null || !transactions.getEnabled()) {
            return;
        }

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        final boolean tlsEnabled = isTlsEnabledOnBroker();
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
                .withLabels(getLabels())
                .endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .withTolerations(spec.getTolerations())
                .withDnsConfig(global.getDnsConfig())
                .withNodeSelector(spec.getNodeSelectors())
                .withVolumes(volumes)
                .withContainers(List.of(container))
                .withRestartPolicy("OnFailure")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        patchResource(job);
    }


    private Probe createProbe() {
        final ProbeConfig specProbe = spec.getProbe();
        if (specProbe == null || !specProbe.getEnabled()) {
            return null;
        }
        final String authHeader = isAuthTokenEnabled()
                ? "-H \"Authorization: Bearer $(cat /pulsar/token-superuser/superuser.jwt | tr -d '\\r')\"" : "";
        return new ProbeBuilder()
                .withNewExec()
                .withCommand("sh", "-c", "curl -s --max-time %d --fail %s http://localhost:8080/admin/v2/brokers/health > /dev/null"
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

}

