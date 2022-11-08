package com.datastax.oss.pulsaroperator.controllers.zookeeper;

import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
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
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class ZooKeeperResourcesFactory {

    private final KubernetesClient client;
    private final String namespace;
    private final ZooKeeperSpec spec;
    private final GlobalSpec global;
    private final String resourceName;
    private final OwnerReference ownerReference;
    private VersionInfo version;

    public ZooKeeperResourcesFactory(KubernetesClient client, String namespace, ZooKeeperSpec spec,
                                     GlobalSpec global, OwnerReference ownerReference) {
        this.client = client;
        this.namespace = namespace;
        this.spec = spec;
        this.global = global;
        this.ownerReference = ownerReference;

        resourceName = String.format("%s-%s", global.getName(), spec.getComponent());
    }

    private void commonCreateOrReplace(HasMetadata resource) {
        resource.getMetadata().setOwnerReferences(List.of(ownerReference));
        client.resource(resource).inNamespace(namespace).createOrReplace();
        if (log.isDebugEnabled()) {
            try {
                log.debugf("Created %s: \n%s", resource.getFullResourceName(),
                        SerializationUtils.dumpAsYaml(resource));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }

    public void createService() {
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
                .withLabels(getLabels())
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withPorts(ports)
                .withClusterIP("None")
                .withPublishNotReadyAddresses(true)
                .withSelector(getMatchLabels())
                .endSpec()
                .build();

        commonCreateOrReplace(service);
    }

    private List<ServicePort> getServicePorts() {
        List<ServicePort> ports = new ArrayList<>();
        ports.add(new ServicePortBuilder()
                .withName("server")
                .withPort(2888)
                .build()
        );
        ports.add(new ServicePortBuilder()
                .withName("leader-election")
                .withPort(3888)
                .build()
        );
        ports.add(new ServicePortBuilder()
                .withName("client")
                .withPort(2181)
                .build()
        );
        if (isTlsEnabledOnZooKeeper()) {
            ports.add(
                    new ServicePortBuilder()
                            .withName("client-tls")
                            .withPort(2281)
                            .build()
            );
        }
        if (spec.getService() != null && spec.getService().getAdditionalPorts() != null) {
            ports.addAll(spec.getService().getAdditionalPorts());
        }
        return ports;
    }

    public void createCaService() {
        Map<String, String> annotations = new HashMap<>();
        if (spec.getService() != null && spec.getService().getAnnotations() != null) {
            annotations.putAll(spec.getService().getAnnotations());
        }
        List<ServicePort> ports = getServicePorts();

        final Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(resourceName + "-ca")
                .withNamespace(namespace)
                .withLabels(getLabels())
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withPorts(ports)
                .withSelector(getMatchLabels())
                .endSpec()
                .build();

        commonCreateOrReplace(service);
    }

    public void createConfigMap() {
        Map<String, String> data = new HashMap<>();
        data.put("PULSAR_MEM", "-Xms1g -Xmx1g -Dcom.sun.management.jmxremote -Djute.maxbuffer=10485760");
        data.put("PULSAR_GC", "-XX:+UseG1GC");
        data.put("PULSAR_LOG_LEVEL", "info");
        data.put("PULSAR_LOG_ROOT_LEVEL", "info");
        data.put("PULSAR_EXTRA_OPTS",
                "-Dzookeeper.tcpKeepAlive=true -Dzookeeper.clientTcpKeepAlive=true -Dpulsar.log.root.level=info");

        if (isTlsEnabledOnZooKeeper()) {
            data.put("PULSAR_PREFIX_serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");
            data.put("serverCnxnFactory", "org.apache.zookeeper.server.NettyServerCnxnFactory");
            data.put("secureClientPort", "2281");
            data.put("sslQuorum", "true");
            data.put("PULSAR_PREFIX_sslQuorum", "true");
        }
        if (spec.getConfig() != null) {
            data.putAll(spec.getConfig());
        }

        final ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels()).endMetadata()
                .withData(data)
                .build();
        commonCreateOrReplace(configMap);
    }

    public void createStorageClass() {

        final ZooKeeperSpec.VolumeConfig dataVolume = spec.getDataVolume();
        final String volumeFullName = resourceName + "-" + dataVolume.getName();
        final StorageClassConfig storageClass = dataVolume.getStorageClass();
        if (storageClass == null) {
            throw new IllegalStateException("StorageClass is not defined");
        }

        Map<String, String> parameters = new HashMap<>();
        if (storageClass.getType() != null) {
            parameters.put("type", storageClass.getType());
        }
        if (storageClass.getFsType() != null) {
            parameters.put("fsType", storageClass.getFsType());
        }
        if (storageClass.getExtraParams() != null) {
            parameters.putAll(storageClass.getExtraParams());
        }

        final StorageClass storageClassResource = new StorageClassBuilder()
                .withNewMetadata()
                .withName(volumeFullName)
                .withNamespace(namespace)
                .withLabels(getLabels())
                .endMetadata()
                .withAllowVolumeExpansion(true)
                .withVolumeBindingMode("WaitForFirstConsumer")
                .withReclaimPolicy(storageClass.getReclaimPolicy())
                .withProvisioner(storageClass.getProvisioner())
                .withParameters(parameters)
                .build();

        commonCreateOrReplace(storageClassResource);
    }

    public void createPodDisruptionBudget() {
        final PodDisruptionBudgetConfig pdb = spec.getPdb();
        if (!pdb.getEnabled()) {
            return;
        }
        final boolean pdbSupported = isPdbSupported();

        final PodDisruptionBudget pdbResource = new PodDisruptionBudgetBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels())
                .endMetadata()
                .withNewSpec()
                .withNewSelector()
                .withMatchLabels(getMatchLabels())
                .endSelector()
                .withNewMaxUnavailable(pdb.getMaxUnavailable())
                .endSpec()
                .build();

        if (!pdbSupported) {
            pdbResource.setApiVersion("policy/v1beta1");
        }

        commonCreateOrReplace(pdbResource);
    }

    private Map<String, String> getLabels() {
        return Map.of(
                CRDConstants.LABEL_APP, global.getName(),
                CRDConstants.LABEL_COMPONENT, spec.getComponent(),
                CRDConstants.LABEL_CLUSTER, global.getName()
        );
    }

    public void createStatefulSet() {
        final int replicas = spec.getReplicas();

        Map<String, String> labels = getLabels();
        Map<String, String> matchLabels = getMatchLabels();
        Map<String, String> allAnnotations = new HashMap<>();
        allAnnotations.put("prometheus.io/scrape", "true");
        allAnnotations.put("prometheus.io/port", "8080");
        if (spec.getAnnotations() != null) {
            allAnnotations.putAll(spec.getAnnotations());
        }
        PodDNSConfig dnsConfig = global.getDnsConfig();
        Map<String, String> nodeSelectors = spec.getNodeSelectors();

        long gracePeriod = spec.getGracePeriod();

        List<Container> containers = new ArrayList<>();
        final ResourceRequirements resources = spec.getResources();
        boolean enableTls = isTlsEnabledOnZooKeeper();

        List<String> zkServers = new ArrayList<>();
        for (int i = 0; i < spec.getReplicas(); i++) {
            zkServers.add(resourceName + "-" + i);
        }


        Probe probe = createProbe();
        final String dataStorageVolumeName = resourceName + "-" + spec.getDataVolume().getName();

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        volumeMounts.add(
                new VolumeMountBuilder()
                        .withName(dataStorageVolumeName)
                        .withMountPath("/pulsar/data")
                        .build()
        );
        if (enableTls) {
            addTlsVolumes(volumeMounts, volumes);
        }

        String command = "bin/apply-config-from-env.py conf/zookeeper.conf && ";
        if (enableTls) {
            command += "/pulsar/tools/certconverter.sh && ";
        }
        command += "bin/generate-zookeeper-config.sh conf/zookeeper.conf && ";

        command += "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar zookeeper";

        final String zkConnectString = zkServers.stream().collect(Collectors.joining(","));
        containers.add(
                new ContainerBuilder()
                        .withName(resourceName)
                        .withImage(spec.getImage())
                        .withImagePullPolicy(spec.getImagePullPolicy())
                        .withResources(resources)
                        .withCommand("sh", "-c")
                        .withArgs(command)
                        .withPorts(Arrays.asList(
                                new ContainerPortBuilder()
                                        .withName("client")
                                        .withContainerPort(2181)
                                        .build(),
                                new ContainerPortBuilder()
                                        .withName("server")
                                        .withContainerPort(2888)
                                        .build(),
                                new ContainerPortBuilder()
                                        .withName("leader-election")
                                        .withContainerPort(3888)
                                        .build()
                        ))
                        .withEnv(List.of(new EnvVarBuilder().withName("ZOOKEEPER_SERVERS").withValue(zkConnectString)
                                .build()))
                        .withEnvFrom(List.of(new EnvFromSourceBuilder().withNewConfigMapRef()
                                .withName(resourceName).endConfigMapRef().build()))
                        .withLivenessProbe(probe)
                        .withReadinessProbe(probe)
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
            String storageClassName = null;
            final ZooKeeperSpec.VolumeConfig dataVolume = spec.getDataVolume();
            if (dataVolume.getExistingStorageClassName() != null) {
                if (!dataVolume.getExistingStorageClassName().equals("default")) {
                    storageClassName = dataVolume.getExistingStorageClassName();
                }
            } else if (dataVolume.getStorageClass() != null) {
                storageClassName = dataStorageVolumeName;
            }

            persistentVolumeClaims.add(
                    new PersistentVolumeClaimBuilder()
                            .withNewMetadata().withName(dataStorageVolumeName).endMetadata()
                            .withNewSpec()
                            .withAccessModes(List.of("ReadWriteOnce"))
                            .withNewResources()
                            .withRequests(Map.of("storage", Quantity.parse(dataVolume.getSize())))
                            .endResources()
                            .withStorageClassName(storageClassName)
                            .endSpec()
                            .build()
            );
        }

        final StatefulSet statefulSet = new StatefulSetBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withServiceName(resourceName)
                .withReplicas(replicas)
                .withNewSelector()
                .withMatchLabels(matchLabels)
                .endSelector()
                .withUpdateStrategy(spec.getUpdateStrategy())
                .withPodManagementPolicy(spec.getPodManagementPolicy())
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .withAnnotations(allAnnotations)
                .endMetadata()
                .withNewSpec()
                .withDnsConfig(dnsConfig)
                .withNodeSelector(nodeSelectors)
                .withTerminationGracePeriodSeconds(gracePeriod)
                .withNewSecurityContext().withFsGroup(0L).endSecurityContext()
                .withContainers(containers)
                .withVolumes(volumes)
                .endSpec()
                .endTemplate()
                .withVolumeClaimTemplates(persistentVolumeClaims)
                .endSpec()
                .build();
        commonCreateOrReplace(statefulSet);
    }

    private void addTlsVolumes(List<VolumeMount> volumeMounts, List<Volume> volumes) {
        volumeMounts.add(
                new VolumeMountBuilder()
                        .withName("certs")
                        .withReadOnly(true)
                        .withMountPath("/pulsar/certs")
                        .build()
        );
        volumeMounts.add(
                new VolumeMountBuilder()
                        .withName("certconverter")
                        .withMountPath("/pulsar/tools")
                        .build()
        );
        volumes.add(
                new VolumeBuilder()
                        .withName("certs")
                        .withNewSecret().withSecretName(global.getTls().getZookeeper().getTlsSecretName())
                        .endSecret()
                        .build()
        );

        volumes.add(
                new VolumeBuilder()
                        .withName("certconverter")
                        .withNewConfigMap().withName(global.getName() + "-certconverter-configmap")
                        .withDefaultMode(0755).endConfigMap()
                        .build()
        );
    }

    public void createMetadataInitializationJob() {

        final ZooKeeperSpec.MetadataInitializationJobConfig jobConfig =
                spec.getMetadataInitializationJob();

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        final boolean tlsEnabled = isTlsEnabledOnZooKeeper();
        if (tlsEnabled) {
            addTlsVolumes(volumeMounts, volumes);
        }

        final String waitZKHostname =
                String.format("%s-%d.%s.%s", resourceName, spec.getReplicas() - 1, resourceName, namespace);

        final Container initContainer = new ContainerBuilder()
                .withName("wait-zookeeper-ready")
                .withImage(spec.getImage())
                .withImagePullPolicy(spec.getImagePullPolicy())
                .withCommand("sh", "-c")
                .withArgs("""
                        until [ "$(echo ruok | nc %s 2181)" = "imok" ]; do
                          echo Zookeeper not yet ready. Will try again after 3 seconds.
                          sleep 3;
                        done;
                        echo Zookeeper is ready.
                        """.formatted(waitZKHostname))
                .build();

        String mainArgs = "";
        if (tlsEnabled) {
            mainArgs += "/pulsar/tools/certconverter.sh &&";
        }

        final String clusterName = global.getName();
        final String serviceDnsSuffix = "%s.svc.%s".formatted(namespace, global.getKubernetesClusterDomain());
        final String zkParam = "%s-ca.%s:%d".formatted(resourceName, serviceDnsSuffix, tlsEnabled ? 2281 : 2181);
        final boolean tlsEnabledOnBroker = isTlsEnabledGlobally();

        final String webService = "%s://%s-%s.%s:%d/".formatted(
                tlsEnabledOnBroker ? "https" : "http",
                clusterName, "broker", serviceDnsSuffix, tlsEnabledOnBroker ? 8443 : 8080);

        final String brokerService = "%s://%s-%s.%s:%d/".formatted(
                tlsEnabledOnBroker ? "pulsar+ssl" : "pulsar",
                clusterName, "broker", serviceDnsSuffix, tlsEnabledOnBroker ? 6651 : 6650);

        mainArgs += """
                bin/pulsar initialize-cluster-metadata --cluster %s \\
                    --zookeeper %s \\
                    --configuration-store %s \\
                    %s %s \\
                    %s %s
                """.formatted(clusterName, zkParam, zkParam,
                tlsEnabledOnBroker ? "--web-service-url-tls" : "--web-service-url",
                webService,
                tlsEnabledOnBroker ? "--broker-service-url-tls" : "--broker-service-url",
                brokerService);

        final Container container = new ContainerBuilder()
                .withName(resourceName)
                .withImage(spec.getImage())
                .withImagePullPolicy(spec.getImagePullPolicy())
                .withVolumeMounts(volumeMounts)
                .withResources(jobConfig.getResources())
                .withCommand("timeout", jobConfig.getInitTimeout() + "", "sh", "-c")
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
                .withDnsConfig(global.getDnsConfig())
                .withNodeSelector(spec.getNodeSelectors())
                .withVolumes(volumes)
                .withInitContainers(List.of(initContainer))
                .withContainers(List.of(container))
                .withRestartPolicy("OnFailure")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        commonCreateOrReplace(job);
    }

    private boolean isTlsEnabledGlobally() {
        return global.getTls() != null && global.getTls().isEnabled();
    }

    private boolean isTlsEnabledOnZooKeeper() {
        return isTlsEnabledGlobally()
                && global.getTls().getZookeeper() != null
                && global.getTls().getZookeeper().isEnabled();
    }

    private Probe createProbe() {
        final ZooKeeperSpec.ProbeConfig specProbe = spec.getProbe();
        if (specProbe == null) {
            return null;
        }
        return new ProbeBuilder().withNewExec()
                .withCommand("timeout", specProbe.getTimeout() + "", "bin/pulsar-zookeeper-ruok.sh")
                .endExec()
                .withInitialDelaySeconds(specProbe.getInitial())
                .withPeriodSeconds(specProbe.getPeriod())
                .withTimeoutSeconds(specProbe.getTimeout())
                .build();
    }

    private Map<String, String> getMatchLabels() {
        Map<String, String> matchLabels = Map.of(
                "app", global.getName(),
                "component", spec.getComponent()
        );
        return matchLabels;
    }

    private boolean isPdbSupported() {
        final VersionInfo version = getVersion();
        if (version.getMajor().compareTo("1") >= 0
                && version.getMinor().compareTo("21") >= 0) {
            return true;
        }
        return false;
    }

    private VersionInfo getVersion() {
        if (version == null) {
            version = client.getKubernetesVersion();
        }
        return version;
    }

}
