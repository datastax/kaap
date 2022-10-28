package com.datastax.oss.pulsaroperator.reconcilier;

import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeperSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorRequirementBuilder;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.PodAntiAffinityBuilder;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-zk-app")
public class ZooKeeperReconcilier implements Reconciler<ZooKeeper> {

    private final KubernetesClient client;

    @SneakyThrows
    public ZooKeeperReconcilier(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<ZooKeeper> reconcile(ZooKeeper resource, Context context) {
        System.out.println("ZK reconcile called");
        final String namespace = resource.getMetadata().getNamespace();
        final ZooKeeperSpec spec = resource.getSpec();
        System.out.println("ZK patch with config " + spec);
        System.out.println("ZK patch config was " + resource.getStatus().getCurrentSpec());
        System.out.println("Status: " + resource.getStatus().getCurrentSpec());
        // TODO: handle immutable fields with proper methods (replicas for example)
        if (resource.getStatus().getCurrentSpec() != null) {
            client.apps().statefulSets().withName(spec.getClusterSpec().getFullname() + "-" + spec.getComponent())
                    .delete();
            System.out.println("Old stateful set deleted");
        }
        createConfigMap(namespace, spec);
        createStatefulSet(namespace, spec);
        createService(namespace, spec);
        createCaService(namespace, spec);

        resource.getStatus().setCurrentSpec(spec);
        // statefulSet
        // service
        // storageClass
        // pdb
        // metadata init job
        // config-map

        return UpdateControl.updateStatus(resource);
    }

    private void createService(String namespace, ZooKeeperSpec spec) {
        Map<String, String> annotations = new HashMap<>();
        annotations.put("service.alpha.kubernetes.io/tolerate-unready-endpoints", "true");
        if (spec.getService().getAnnotations() != null) {
            annotations.putAll(spec.getService().getAnnotations());
        }
        List<ServicePort> ports = new ArrayList<>();
        if (spec.getClusterSpec().isEnableTls() && spec.getClusterSpec().getTls().getZookeeper().isEnabled()) {
            ports.add(
                    new ServicePortBuilder()
                            .withName("client-tls")
                            .withPort(2281)
                            .build()
            );
        }
        if (spec.getService().getPorts() != null) {
            ports.addAll(spec.getService().getPorts());
        }

        final Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(spec.getClusterSpec().getFullname() + "-" + spec.getComponent())
                .withNamespace(namespace)
                .withLabels(getLabels(spec))
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withPorts(ports)
                .withClusterIP("None")
                .withPublishNotReadyAddresses(true)
                .withSelector(getMatchLabels(spec))
                .endSpec()
                .build();

        client.resource(service).inNamespace(namespace).createOrReplace();
    }

    private void createCaService(String namespace, ZooKeeperSpec spec) {
        Map<String, String> annotations = new HashMap<>();
        if (spec.getService().getAnnotations() != null) {
            annotations.putAll(spec.getService().getAnnotations());
        }
        List<ServicePort> ports = new ArrayList<>();
        if (spec.getClusterSpec().isEnableTls() && spec.getClusterSpec().getTls().getZookeeper().isEnabled()) {
            ports.add(
                    new ServicePortBuilder()
                            .withName("client-tls")
                            .withPort(2281)
                            .build()
            );
        }
        if (spec.getService().getPorts() != null) {
            ports.addAll(spec.getService().getPorts());
        }

        final Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(spec.getClusterSpec().getFullname() + "-" + spec.getComponent() + "-ca")
                .withNamespace(namespace)
                .withLabels(getLabels(spec))
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withPorts(ports)
                .withSelector(getMatchLabels(spec))
                .endSpec()
                .build();

        client.resource(service).inNamespace(namespace).createOrReplace();
    }

    private void createConfigMap(String namespace, ZooKeeperSpec spec) {
        Map<String, String> data = new HashMap<>();
        if (spec.getClusterSpec().isEnableTls() && spec.getClusterSpec().getTls().getZookeeper().isEnabled()) {
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
                .withName(spec.getClusterSpec().getFullname() + "-" + spec.getComponent())
                .withNamespace(namespace)
                .withLabels(getLabels(spec)).endMetadata()
                .withData(data)
                .build();
        client.resource(configMap).inNamespace(namespace).createOrReplace();
    }

    private Map<String, String> getLabels(ZooKeeperSpec spec) {
        return Map.of(
                "app", spec.getClusterSpec().getName(),
                "component", spec.getComponent(),
                "cluster", spec.getClusterSpec().getFullname()
        );
    }

    private void createStatefulSet(String namespace, ZooKeeperSpec spec) {
        final String pulsarFullName = spec.getClusterSpec().getFullname();
        final String zkComponent = spec.getComponent();
        final int replicas = spec.getReplicas();
        final String podManagementPolicy = spec.getPodManagementPolicy();


        Map<String, String> labels = getLabels(spec);
        Map<String, String> matchLabels = getMatchLabels(spec);
        Map<String, String> allAnnotations = new HashMap<>();
        if (spec.getAnnotations() != null) {
            allAnnotations.putAll(spec.getAnnotations());
        }
        // TODO:  checksum/config (restartOnConfigMapChange)


        PodDNSConfig dnsConfig = spec.getClusterSpec().getDnsConfig();
        Map<String, String> nodeSelectors;
        if (spec.getNodeSelectors() != null) {
            nodeSelectors = spec.getNodeSelectors();
        } else if (spec.getClusterSpec().getGlobalNodeSelectors() != null) {
            nodeSelectors = spec.getClusterSpec().getGlobalNodeSelectors();
        } else {
            nodeSelectors = Collections.emptyMap();
        }
        List<Toleration> tolerations = spec.getTolerations();

        NodeAffinity nodeAffinity = spec.getNodeAffinity();
        PodAntiAffinity podAntiAffinity = getPodAntiAffinity(spec, zkComponent);
        long gracePeriod = spec.getGracePeriod();

        List<Container> containers = new ArrayList<>();
        final ResourceRequirements resources = spec.getResources();
        boolean enableTls = spec.getClusterSpec().isEnableTls() && spec.getClusterSpec().getTls().getZookeeper().isEnabled();
        boolean zookeepernp = spec.isZookeepernp();

        List<String> zkServers = new ArrayList<>();
        for (int i = 0; i < spec.getReplicas(); i++) {
            zkServers.add(spec.getClusterSpec().getFullname() + "-" + spec.getComponent() + "-" + i);
        }

        final String zkConnectString = zkServers.stream().collect(Collectors.joining(","));
        boolean probeEnabled = spec.getProbe().isEnabled();
        int probeTimeout = spec.getProbe().getTimeout();
        int probeInitialDelaySeconds = spec.getProbe().getInitial();
        int probePeriodSeconds = spec.getProbe().getPeriod();

        Probe readinessProbe = probeEnabled ? new ProbeBuilder().withNewExec()
                .withCommand("timeout", probeTimeout + "", "bin/pulsar-zookeeper-ruok.sh")
                .endExec()
                .withInitialDelaySeconds(probeInitialDelaySeconds)
                .withPeriodSeconds(probePeriodSeconds)
                .withTimeoutSeconds(probeTimeout)
                .build() : null;
        Probe livenessProbe = probeEnabled ? new ProbeBuilder().withNewExec()
                .withCommand("timeout", probeTimeout + "", "bin/pulsar-zookeeper-ruok.sh")
                .endExec()
                .withInitialDelaySeconds(probeInitialDelaySeconds)
                .withPeriodSeconds(probePeriodSeconds)
                .withTimeoutSeconds(probeTimeout)
                .build() : null;


        final String volumeDataName = spec.getDataVolume().getName();
        final String storageVolumeName = pulsarFullName + "-" + zkComponent + "-" + volumeDataName;
        final String storageClassName = spec.getDataVolume().getExistingStorageClassName() != null ?
                spec.getDataVolume().getExistingStorageClassName() :
                storageVolumeName;

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        if (enableTls) {
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
                            .withNewSecret().withSecretName(spec.getClusterSpec().getTls().getZookeeper().getTlsSecretName()).endSecret()
                            .build()
            );

            volumes.add(
                    new VolumeBuilder()
                            .withName("certconverter")
                            .withNewConfigMap().withName(pulsarFullName + "-certconverter-configmap").withDefaultMode(0755).endConfigMap()
                            .build()
            );
        }

        boolean persistence = spec.getClusterSpec().isPersistence();

        if (!persistence) {
            volumeMounts.add(
                    new VolumeMountBuilder()
                            .withName(storageVolumeName)
                            .withMountPath("/pulsar/data")
                            .build()
            );
            volumes.add(
                    new VolumeBuilder()
                            .withName(storageVolumeName)
                            .withNewEmptyDir().endEmptyDir()
                            .build()
            );
        }
        if (zookeepernp) {
            volumeMounts.add(
                    new VolumeMountBuilder()
                            .withName("zookeeper-config")
                            .withMountPath("/pulsar/zookeeper-config")
                            .build()
            );
            volumes.add(
                    new VolumeBuilder()
                            .withName("zookeeper-config")
                            .withNewConfigMap().withName(pulsarFullName + "-zookeeper-config").withDefaultMode(0755).endConfigMap()
                            .build()
            );
        }


        String command = "bin/apply-config-from-env.py conf/zookeeper.conf && ";
        if (enableTls) {
            command += "/pulsar/tools/certconverter.sh && ";
        }
        if (zookeepernp) {
            command += "/pulsar/zookeeper-config/generate-zookeeper-config-mixed.sh conf/zookeeper.conf && ";
        } else {
            command += "bin/generate-zookeeper-config.sh conf/zookeeper.conf && ";
        }
        command += "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar zookeeper";

        containers.add(
                new ContainerBuilder()
                        .withName(pulsarFullName + "-" + zkComponent)
                        .withImage(spec.getContainerImage())
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
                        .withEnv(List.of(new EnvVarBuilder().withName("ZOOKEEPER_SERVERS").withValue(zkConnectString).build()))
                        .withEnvFrom(List.of(new EnvFromSourceBuilder().withNewConfigMapRef().withName(pulsarFullName + "-" + zkComponent).endConfigMapRef().build()))
                        .withLivenessProbe(livenessProbe)
                        .withReadinessProbe(readinessProbe)
                        .withVolumeMounts(volumeMounts)
                        .build()
        );

        List<PersistentVolumeClaim> persistentVolumeClaims = new ArrayList<>();
        if (persistence) {
            persistentVolumeClaims.add(
                    new PersistentVolumeClaimBuilder()
                            .withNewMetadata().withName(storageVolumeName).endMetadata()
                            .withNewSpec()
                            .withAccessModes(List.of("ReadWriteOnce"))
                            .withNewResources()
                            .withRequests(Map.of("storage", Quantity.parse(spec.getDataVolume().getSize())))
                            .endResources()
                            .withStorageClassName(storageClassName)
                            .endSpec()
                            .build()
            );
        }

        final StatefulSet statefulSet = new StatefulSetBuilder()
                .withNewMetadata()
                .withName(pulsarFullName + "-" + zkComponent)
                .withNamespace(namespace)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withServiceName(pulsarFullName + "-" + zkComponent)
                .withReplicas(replicas)
                .withNewSelector()
                .withMatchLabels(matchLabels)
                .endSelector()
                .withUpdateStrategy(spec.getUpdateStrategy())
                .withPodManagementPolicy(podManagementPolicy)
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .withAnnotations(allAnnotations)
                .endMetadata()
                .withNewSpec()
                .withDnsConfig(dnsConfig)
                .withPriorityClassName(spec.getClusterSpec().isPriorityClass() ? "pulsar-priority" : null)
                .withNodeSelector(nodeSelectors)
                .withTolerations(tolerations)
                .withNewAffinity()
                .withNodeAffinity(nodeAffinity)
                .withPodAntiAffinity(podAntiAffinity)
                .endAffinity()
                .withTerminationGracePeriodSeconds(gracePeriod)
                .withNewSecurityContext().withFsGroup(0L).endSecurityContext()
                .withContainers(containers)
                .withVolumes()
                .endSpec()
                .endTemplate()
                .withVolumeClaimTemplates(persistentVolumeClaims)
                .endSpec()
                .build();
        try {
            System.out.println("Created statefulset:\n" + SerializationUtils.dumpAsYaml(statefulSet));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        client.resource(statefulSet).inNamespace(namespace).createOrReplace();
    }

    private Map<String, String> getMatchLabels(ZooKeeperSpec spec) {
        Map<String, String> matchLabels = Map.of(
                "app", spec.getClusterSpec().getName(),
                "component", spec.getComponent()
        );
        return matchLabels;
    }

    private PodAntiAffinity getPodAntiAffinity(ZooKeeperSpec spec, String zkComponent) {
        if (spec.getClusterSpec().isEnableAntiAffinity()) {
            if (spec.getPodAntiAffinity() != null) {
                return spec.getPodAntiAffinity();
            } else {
                PodAntiAffinityBuilder builder = new PodAntiAffinityBuilder();
                if (spec.getClusterSpec().getAntiAffinity() != null && spec.getClusterSpec().getAntiAffinity().getHost().isEnabled()) {
                    builder = builder.withRequiredDuringSchedulingIgnoredDuringExecution(
                                    new PodAffinityTermBuilder()
                                            .withTopologyKey("kubernetes.io/hostname")
                                            .withLabelSelector(new LabelSelectorBuilder()
                                                    .withMatchExpressions(
                                                            new LabelSelectorRequirementBuilder()
                                                                    .withKey("app")
                                                                    .withOperator("In")
                                                                    .withValues(List.of(spec.getClusterSpec().getFullname()))
                                                                    .build(),
                                                            new LabelSelectorRequirementBuilder()
                                                                    .withKey("component")
                                                                    .withOperator("In")
                                                                    .withValues(List.of(zkComponent))
                                                                    .build()

                                                    ).build())
                                            .build()
                        );
                }
                if (spec.getClusterSpec().getAntiAffinity() != null && spec.getClusterSpec().getAntiAffinity().getZone().isEnabled()) {
                    builder = builder.withPreferredDuringSchedulingIgnoredDuringExecution(
                            new WeightedPodAffinityTermBuilder()
                                    .withWeight(100)
                                    .withNewPodAffinityTerm()
                                    .withTopologyKey("failure-domain.beta.kubernetes.io/zone")
                                    .withLabelSelector(new LabelSelectorBuilder()
                                            .withMatchExpressions(
                                                    new LabelSelectorRequirementBuilder()
                                                            .withKey("app")
                                                            .withOperator("In")
                                                            .withValues(List.of(spec.getClusterSpec().getFullname()))
                                                            .build(),
                                                    new LabelSelectorRequirementBuilder()
                                                            .withKey("component")
                                                            .withOperator("In")
                                                            .withValues(List.of(zkComponent))
                                                            .build()

                                            ).build()).endPodAffinityTerm()
                                    .build()
                    );
                }
                return builder.build();
            }
        }
        return null;
    }
}

