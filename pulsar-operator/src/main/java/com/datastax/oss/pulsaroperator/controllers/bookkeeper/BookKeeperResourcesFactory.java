package com.datastax.oss.pulsaroperator.controllers.bookkeeper;

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
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
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.ObjectUtils;

@JBossLog
public class BookKeeperResourcesFactory extends BaseResourcesFactory<BookKeeperSpec> {

    public BookKeeperResourcesFactory(KubernetesClient client, String namespace,
                                      BookKeeperSpec spec, GlobalSpec global,
                                      OwnerReference ownerReference) {
        super(client, namespace, spec, global, ownerReference);
    }

    @Override
    protected String getComponentBaseName() {
        return global.getComponents().getBookkeeperBaseName();
    }

    public void createConfigMap() {
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
        commonCreateOrReplace(configMap);
    }


    public void createStatefulSet() {
        final int replicas = spec.getReplicas();

        Map<String, String> labels = getLabels();
        Map<String, String> allAnnotations = new HashMap<>();
        allAnnotations.put("prometheus.io/scrape", "true");
        allAnnotations.put("prometheus.io/port", "8080");
        if (spec.getAnnotations() != null) {
            allAnnotations.putAll(spec.getAnnotations());
        }


        List<Container> initContainers = new ArrayList<>();

        initContainers.add(new ContainerBuilder()
                .withName("wait-zookeeper-ready")
                .withImage(spec.getImage())
                .withImagePullPolicy(spec.getImagePullPolicy())
                .withCommand("sh", "-c")
                .withArgs("""
                        until bin/pulsar zookeeper-shell -server %s-%s ls /admin/clusters | grep "^\\[.*%s.*\\]"; do
                            sleep 3;
                        done;
                        """.formatted(global.getName(),
                        global.getComponents().getZookeeperBaseName(), global.getName()))
                .build()
        );
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
            mainArg += "openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key -out /pulsar/tls-pk8.key -nocrypt && ";
        }
        if (isTlsEnabledOnZooKeeper()) {
            mainArg += "/pulsar/tools/certconverter.sh && ";
        }

        mainArg += "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar bookie";




        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        addTlsVolumesIfEnabled(volumeMounts, volumes);

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
                .withReplicas(replicas)
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
        commonCreateOrReplace(statefulSet);
    }

    private PersistentVolumeClaim createPersistentVolumeClaim(String name,
                                                              VolumeConfig volumeConfig) {
        String storageClassName = null;
        if (volumeConfig.getExistingStorageClassName() != null) {
            if (!volumeConfig.getExistingStorageClassName().equals("default")) {
                storageClassName = volumeConfig.getExistingStorageClassName();
            }
        } else if (volumeConfig.getStorageClass() != null) {
            storageClassName = name;
        }

        return new PersistentVolumeClaimBuilder()
                        .withNewMetadata().withName(name).endMetadata()
                        .withNewSpec()
                        .withAccessModes(List.of("ReadWriteOnce"))
                        .withNewResources()
                        .withRequests(Map.of("storage", Quantity.parse(volumeConfig.getSize())))
                        .endResources()
                        .withStorageClassName(storageClassName)
                        .endSpec()
                        .build();
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


}

