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
package com.datastax.oss.kaap.controllers.zookeeper;

import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.configs.ProbesConfig;
import com.datastax.oss.kaap.crds.configs.VolumeConfig;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeperSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.BooleanUtils;

@JBossLog
public class ZooKeeperResourcesFactory extends BaseResourcesFactory<ZooKeeperSpec> {

    public static final int DEFAULT_SERVER_PORT = 2888;
    public static final int DEFAULT_LEADER_ELECTION_PORT = 3888;
    public static final int DEFAULT_CLIENT_PORT = 2181;
    public static final int DEFAULT_CLIENT_TLS_PORT = 2281;
    public static final String ENV_ZOOKEEPER_SERVERS = "ZOOKEEPER_SERVERS";
    public static final List<String> DEFAULT_ENV = List.of("ZOOKEEPER_SERVERS");

    public static String getComponentBaseName(GlobalSpec globalSpec) {
        return globalSpec.getComponents().getZookeeperBaseName();
    }

    public static List<String> getContainerNames(String resourceName) {
        return List.of(getMainContainerName(resourceName));
    }


    public static String getResourceName(String clusterName, String baseName) {
        return "%s-%s".formatted(clusterName, baseName);
    }

    public static String getResourceName(GlobalSpec globalSpec, String baseName) {
        return getResourceName(globalSpec.getName(), baseName);
    }

    private static String getMainContainerName(String resourceName) {
        return resourceName;
    }


    private ConfigMap configMap;

    public ZooKeeperResourcesFactory(KubernetesClient client, String namespace,
                                     ZooKeeperSpec spec, GlobalSpec global,
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
        Map<String, String> annotations = new HashMap<>();
        annotations.put("service.alpha.kubernetes.io/tolerate-unready-endpoints", "true");
        if (spec.getService() != null && spec.getService().getAnnotations() != null) {
            annotations.putAll(spec.getService().getAnnotations());
        }
        List<ServicePort> ports = getServicePorts();

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
                .withPublishNotReadyAddresses(true)
                .withSelector(getMatchLabels(spec.getMatchLabels()))
                .endSpec()
                .build();

        patchResource(service);
    }

    private List<ServicePort> getServicePorts() {
        List<ServicePort> ports = new ArrayList<>();
        ports.add(new ServicePortBuilder()
                .withName("server")
                .withPort(DEFAULT_SERVER_PORT)
                .build()
        );
        ports.add(new ServicePortBuilder()
                .withName("leader-election")
                .withPort(DEFAULT_LEADER_ELECTION_PORT)
                .build()
        );
        ports.add(new ServicePortBuilder()
                .withName("client")
                .withPort(DEFAULT_CLIENT_PORT)
                .build()
        );
        if (isTlsEnabledOnZooKeeper()) {
            ports.add(
                    new ServicePortBuilder()
                            .withName("client-tls")
                            .withPort(DEFAULT_CLIENT_TLS_PORT)
                            .build()
            );
        }
        if (spec.getService() != null && spec.getService().getAdditionalPorts() != null) {
            ports.addAll(spec.getService().getAdditionalPorts());
        }
        return ports;
    }

    public void patchCaService() {
        Map<String, String> annotations = new HashMap<>();
        if (spec.getService() != null && spec.getService().getAnnotations() != null) {
            annotations.putAll(spec.getService().getAnnotations());
        }
        List<ServicePort> ports = getServicePorts();

        final Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(resourceName + "-ca")
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

    public void patchConfigMap() {
        Map<String, String> data = new HashMap<>();
        data.put("PULSAR_MEM", "-Dcom.sun.management.jmxremote -Djute.maxbuffer=10485760");
        data.put("PULSAR_LOG_LEVEL", "info");
        data.put("PULSAR_LOG_ROOT_LEVEL", "info");
        data.put("PULSAR_EXTRA_OPTS",
                "-Dzookeeper.tcpKeepAlive=true -Dzookeeper.clientTcpKeepAlive=true -Dpulsar.log.root.level=info");

        if (isTlsEnabledOnZooKeeper()) {
            data.put("serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");
            data.put("secureClientPort", "2281");
            data.put("sslQuorum", "true");
            // TLSv1.2 is backward compatible with ZK < 3.9.2
            data.put("ssl.protocol", "TLSv1.2");
            data.put("ssl.quorum.protocol", "TLSv1.2");
        }
        appendConfigData(data, spec.getConfig());

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

    public void patchStorageClass() {
        createStorageClassIfNeeded(spec.getDataVolume(), spec.getAnnotations(), spec.getLabels());
    }

    public void patchPodDisruptionBudget() {
        createPodDisruptionBudgetIfEnabled(
                spec.getPdb(),
                spec.getAnnotations(),
                spec.getLabels(),
                spec.getMatchLabels());
    }


    public void patchStatefulSet() {
        if (!isComponentEnabled()) {
            deleteStatefulSet();
            return;
        }

        Map<String, String> labels = getLabels(spec.getLabels());
        boolean skipVolumeClaimLabels = BooleanUtils.isTrue(spec.getSkipVolumeClaimLabels());
        Map<String, String> podLabels = getPodLabels(spec.getPodLabels());
        Map<String, String> matchLabels = getMatchLabels(spec.getMatchLabels());
        Map<String, String> annotations = getAnnotations(spec.getAnnotations());
        Objects.requireNonNull(configMap, "ConfigMap should have been created at this point");
        Map<String, String> podAnnotations = getPodAnnotations(spec.getPodAnnotations(), configMap, 8000 + "");

        PodDNSConfig dnsConfig = global.getDnsConfig();
        Map<String, String> nodeSelectors = spec.getNodeSelectors();

        long gracePeriod = spec.getGracePeriod();


        final ResourceRequirements resources = spec.getResources();
        boolean enableTls = isTlsEnabledOnZooKeeper();

        List<String> zkServers = new ArrayList<>();
        for (int i = 0; i < spec.getReplicas(); i++) {
            zkServers.add(resourceName + "-" + i);
        }


        final String dataStorageVolumeName = resourceName + "-" + spec.getDataVolume().getName();

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        addAdditionalVolumes(spec.getAdditionalVolumes(), volumeMounts, volumes);
        volumeMounts.add(
                new VolumeMountBuilder()
                        .withName(dataStorageVolumeName)
                        .withMountPath("/pulsar/data")
                        .build()
        );
        if (enableTls) {
            addTlsVolumes(volumeMounts, volumes, getTlsSecretNameForZookeeper());
        }

        String command = "bin/apply-config-from-env.py conf/zookeeper.conf && ";
        if (enableTls) {
            command += generateCertConverterScript() + " && ";
        }
        command += "bin/generate-zookeeper-config.sh conf/zookeeper.conf && ";

        command += "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar zookeeper";

        final String zkConnectString = zkServers.stream().collect(Collectors.joining(","));
        final List<EnvVar> env  = spec.getEnv() == null ? new ArrayList<>() : spec.getEnv();
        checkEnvListNotContains(env, DEFAULT_ENV);
        env.add(new EnvVarBuilder()
                .withName(ENV_ZOOKEEPER_SERVERS)
                .withValue(zkConnectString)
                .build()
        );
        List<Container> containers = getSidecars(spec.getSidecars());
        containers.add(
                new ContainerBuilder()
                        .withName(getMainContainerName(resourceName))
                        .withImage(spec.getImage())
                        .withImagePullPolicy(spec.getImagePullPolicy())
                        .withResources(resources)
                        .withCommand("sh", "-c")
                        .withArgs(command)
                        .withPorts(Arrays.asList(
                                new ContainerPortBuilder()
                                        .withName("client")
                                        .withContainerPort(DEFAULT_CLIENT_PORT)
                                        .build(),
                                new ContainerPortBuilder()
                                        .withName("server")
                                        .withContainerPort(DEFAULT_SERVER_PORT)
                                        .build(),
                                new ContainerPortBuilder()
                                        .withName("leader-election")
                                        .withContainerPort(DEFAULT_LEADER_ELECTION_PORT)
                                        .build()
                        ))
                        .withEnv(env)
                        .withEnvFrom(List.of(new EnvFromSourceBuilder().withNewConfigMapRef()
                                .withName(resourceName).endConfigMapRef().build()))
                        .withLivenessProbe(createProbe(spec.getProbes().getLiveness()))
                        .withReadinessProbe(createProbe(spec.getProbes().getReadiness()))
                        .withVolumeMounts(volumeMounts)
                        .build()
        );

        List<PersistentVolumeClaim> persistentVolumeClaims = new ArrayList<>();
        if (!global.getPersistence()) {
            volumes.add(
                    new VolumeBuilder()
                            .withName(dataStorageVolumeName)
                            .withNewEmptyDir().endEmptyDir()
                            .build()
            );
        } else {
            final VolumeConfig dataVolume = spec.getDataVolume();
            persistentVolumeClaims.add(
                createPersistentVolumeClaim(dataStorageVolumeName, dataVolume,
                    labels, skipVolumeClaimLabels)
            );
        }

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
                .withMatchLabels(matchLabels)
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
                .withDnsConfig(dnsConfig)
                .withImagePullSecrets(spec.getImagePullSecrets())
                .withServiceAccountName(spec.getServiceAccountName())
                .withNodeSelector(nodeSelectors)
                .withAffinity(getAffinity(
                        spec.getNodeAffinity(),
                        spec.getAntiAffinity(),
                        spec.getMatchLabels()
                ))
                .withTerminationGracePeriodSeconds(gracePeriod)
                .withPriorityClassName(global.getPriorityClassName())
                .withNewSecurityContext().withFsGroup(0L).endSecurityContext()
                .withContainers(containers)
                .withInitContainers(getInitContainers(spec.getInitContainers()))
                .withVolumes(volumes)
                .endSpec()
                .endTemplate()
                .withVolumeClaimTemplates(persistentVolumeClaims)
                .endSpec()
                .build();
        patchResource(statefulSet);
    }

    public Job getMetadataInitializationJob() {
        return getJob(jobName());
    }


    public void createMetadataInitializationJobIfNeeded() {
        final String jobName = jobName();

        if (isJobCompleted(jobName)) {
            return;
        }
        final ZooKeeperSpec.MetadataInitializationJobConfig jobConfig =
                spec.getMetadataInitializationJob();

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        final boolean tlsEnabled = isTlsEnabledOnZooKeeper();
        if (tlsEnabled) {
            addTlsVolumes(volumeMounts, volumes, getTlsSecretNameForZookeeper());
        }

        String mainArgs = "";
        if (tlsEnabled) {
            mainArgs += generateCertConverterScript() + " && ";
        }

        final String zkServers = getZkServers();
        final boolean tlsEnabledOnBroker = isTlsEnabledOnBroker();

        mainArgs +=
                "bin/pulsar initialize-cluster-metadata --cluster %s --zookeeper %s --configuration-store %s"
                        .formatted(global.getClusterName(), zkServers, zkServers);

        mainArgs += " --web-service-url %s --broker-service-url %s"
                .formatted(getBrokerWebServiceUrlPlain(), getBrokerServiceUrlPlain());

        if (tlsEnabledOnBroker) {
            mainArgs += " --web-service-url-tls %s --broker-service-url-tls %s"
                    .formatted(getBrokerWebServiceUrlTls(), getBrokerServiceUrlTls());
        }

        final Container container = new ContainerBuilder()
                .withName(jobName)
                .withImage(spec.getImage())
                .withImagePullPolicy(spec.getImagePullPolicy())
                .withVolumeMounts(volumeMounts)
                .withResources(jobConfig.getResources())
                .withCommand("timeout", jobConfig.getTimeout() + "", "sh", "-c")
                .withArgs(mainArgs)
                .build();


        final Job job = new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .withNamespace(namespace)
                .withLabels(getLabels(spec.getLabels()))
                .endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .withTolerations(spec.getTolerations())
                .withDnsConfig(global.getDnsConfig())
                .withImagePullSecrets(spec.getImagePullSecrets())
                .withServiceAccountName(spec.getServiceAccountName())
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

    private String jobName() {
        return "%s-metadata".formatted(resourceName);
    }


    private Probe createProbe(ProbesConfig.ProbeConfig probe) {
        if (probe == null || !probe.getEnabled()) {
            return null;
        }
        return newProbeBuilder(probe)
                .withNewExec()
                .withCommand("timeout", probe.getTimeoutSeconds() + "", "bin/pulsar-zookeeper-ruok.sh")
                .endExec()
                .build();
    }

}
