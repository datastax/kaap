package com.datastax.oss.pulsaroperator.controllers.bookkeeper;

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
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
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.ObjectUtils;

@JBossLog
public class BookKeeperResourcesFactory extends BaseResourcesFactory<BookKeeperSpec> {

    private ConfigMap configMap;

    public BookKeeperResourcesFactory(KubernetesClient client, String namespace,
                                      BookKeeperSpec spec, GlobalSpec global,
                                      OwnerReference ownerReference) {
        super(client, namespace, spec, global, ownerReference);
    }

    @Override
    protected String getComponentBaseName() {
        return global.getComponents().getBookkeeperBaseName();
    }

    @Override
    protected String getResourceName() {
        return "%s-%s".formatted(global.getName(), getComponentBaseName());
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
        List<ServicePort> ports = new ArrayList<>();
        ports.add(new ServicePortBuilder()
                .withName("server")
                .withPort(3181)
                .build());
        if (spec.getService() != null && spec.getService().getAdditionalPorts() != null) {
            ports.addAll(spec.getService().getAdditionalPorts());
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
                .withPublishNotReadyAddresses(true)
                .withSelector(getMatchLabels())
                .endSpec()
                .build();

        patchResource(service);
    }


    public void patchConfigMap() {
        Map<String, String> data = new HashMap<>();
        // disable auto recovery on bookies since we will start AutoRecovery in separated pods
        data.put("autoRecoveryDaemonEnabled", "false");
        // In k8s always want to use hostname as bookie ID since IP addresses are ephemeral
        data.put("useHostNameAsBookieID", "true");
        // HTTP server used by health check
        data.put("httpServerEnabled", "true");
        //Pulsar's metadata store based rack awareness solution
        data.put("PULSAR_PREFIX_reppDnsResolverClass", "org.apache.pulsar.zookeeper.ZkBookieRackAffinityMapping");

        data.put("BOOKIE_MEM", "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled "
                + "-Dio.netty.recycler.linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        data.put("BOOKIE_GC", "-XX:+UseG1GC");
        data.put("PULSAR_LOG_LEVEL", "info");
        data.put("PULSAR_LOG_ROOT_LEVEL", "info");
        data.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        data.put("statsProviderClass", "org.apache.bookkeeper.stats.prometheus.PrometheusMetricsProvider");

        data.put("zkServers", getZkServers());
        // TODO: TLS config

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
        patchResource(configMap);
        this.configMap = configMap;
    }


    public void patchStatefulSet() {
        if (!isComponentEnabled()) {
            log.warn("Got replicas=0, deleting sts");
            deleteStatefulSet();
            return;
        }

        Map<String, String> labels = getLabels();
        Map<String, String> allAnnotations = getDefaultAnnotations();
        Objects.requireNonNull(configMap, "ConfigMap should have been created at this point");
        addConfigMapChecksumAnnotation(configMap, allAnnotations);
        if (spec.getAnnotations() != null) {
            allAnnotations.putAll(spec.getAnnotations());
        }


        List<Container> initContainers = new ArrayList<>();

        // initContainers.add(createWaitZooKeeperReadyContainer(spec.getImage(), spec.getImagePullPolicy()));
        initContainers.add(new ContainerBuilder()
                .withName("pulsar-bookkeeper-metaformat")
                .withImage(spec.getImage())
                .withImagePullPolicy(spec.getImagePullPolicy())
                .withCommand("sh", "-c")
                .withArgs("""
                        bin/apply-config-from-env.py conf/bookkeeper.conf && bin/bookkeeper shell metaformat --nonInteractive || true;
                        """)
                .withEnvFrom(new EnvFromSourceBuilder()
                        .withNewConfigMapRef()
                        .withName(resourceName)
                        .endConfigMapRef()
                        .build())
                .build()
        );

        final Probe probe = createProbe();
        String mainArg = "bin/apply-config-from-env.py conf/bookkeeper.conf && ";
        if (isTlsEnabledOnBookKeeper()) {
            mainArg +=
                    "openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key -out /pulsar/tls-pk8.key"
                            + " -nocrypt && ";
        }
        if (isTlsEnabledOnZooKeeper()) {
            mainArg += "/pulsar/tools/certconverter.sh && ";
        }

        mainArg += "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar bookie";


        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        addTlsVolumesIfEnabled(volumeMounts, volumes, getTlsSecretNameForBookkeeper());

        final String journalVolumeName = "%s%s-%s".formatted(
                ObjectUtils.firstNonNull(spec.getPvcPrefix(), ""),
                resourceName,
                spec.getVolumes().getJournal().getName());

        final String ledgersVolumeName = "%s%s-%s".formatted(
                ObjectUtils.firstNonNull(spec.getPvcPrefix(), ""),
                resourceName,
                spec.getVolumes().getLedgers().getName());

        volumeMounts.add(new VolumeMountBuilder()
                .withName(journalVolumeName)
                .withMountPath("/pulsar/data/bookkeeper/journal")
                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName(ledgersVolumeName)
                .withMountPath("/pulsar/data/bookkeeper/ledgers")
                .build());

        List<PersistentVolumeClaim> persistentVolumeClaims = new ArrayList<>();
        if (!global.getPersistence()) {
            volumes.add(
                    new VolumeBuilder()
                            .withName(journalVolumeName)
                            .withNewEmptyDir().endEmptyDir()
                            .build()
            );
            volumes.add(
                    new VolumeBuilder()
                            .withName(ledgersVolumeName)
                            .withNewEmptyDir().endEmptyDir()
                            .build()
            );
        } else {
            persistentVolumeClaims.add(createPersistentVolumeClaim(journalVolumeName, spec.getVolumes().getJournal()));
            persistentVolumeClaims.add(createPersistentVolumeClaim(ledgersVolumeName, spec.getVolumes().getLedgers()));
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
                        .withPorts(new ContainerPortBuilder()
                                .withName("client")
                                .withContainerPort(3181)
                                .build())
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
                .withDnsConfig(global.getDnsConfig())
                .withNodeSelector(spec.getNodeSelectors())
                .withTerminationGracePeriodSeconds(spec.getGracePeriod().longValue())
                .withNewSecurityContext().withFsGroup(0L).endSecurityContext()
                .withInitContainers(initContainers)
                .withContainers(containers)
                .withVolumes(volumes)
                .endSpec()
                .endTemplate()
                .withVolumeClaimTemplates(persistentVolumeClaims)
                .endSpec()
                .build();
        patchResource(statefulSet);
    }



    private Probe createProbe() {
        final ProbeConfig specProbe = spec.getProbe();
        if (specProbe == null) {
            return null;
        }
        return new ProbeBuilder()
                .withHttpGet(new HTTPGetActionBuilder()
                        .withPath("/api/v1/bookie/is_ready")
                        .withNewPort(8000)
                        .build()
                )
                .withInitialDelaySeconds(specProbe.getInitial())
                .withPeriodSeconds(specProbe.getPeriod())
                .withTimeoutSeconds(specProbe.getTimeout())
                .build();
    }

    public void patchStorageClasses() {
        createStorageClassIfNeeded(spec.getVolumes().getJournal());
        createStorageClassIfNeeded(spec.getVolumes().getLedgers());
    }

    public void patchPodDisruptionBudget() {
        createPodDisruptionBudgetIfEnabled(spec.getPdb());
    }

}

