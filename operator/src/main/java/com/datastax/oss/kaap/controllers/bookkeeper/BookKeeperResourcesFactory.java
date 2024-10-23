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
package com.datastax.oss.kaap.controllers.bookkeeper;

import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.kaap.crds.configs.ProbesConfig;
import com.datastax.oss.kaap.crds.configs.ResourceSetConfig;
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
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.ObjectUtils;

@JBossLog
public class BookKeeperResourcesFactory extends BaseResourcesFactory<BookKeeperSetSpec> {

    public static final String BOOKKEEPER_DEFAULT_SET = "bookkeeper";

    public static final int DEFAULT_BK_PORT = 3181;
    public static final int DEFAULT_HTTP_PORT = 8000;

    public static List<String> getInitContainerNames(String clusterName, String baseName) {
        return List.of(getMainContainerName(clusterName, baseName));
    }

    private static String getMetadataFormatInitContainerName(GlobalSpec global) {
        return "%s-metadata-format".formatted(getBookKeeperContainerName(global));
    }

    public static List<String> getContainerNames(String clusterName, String baseName) {
        return List.of(getMainContainerName(clusterName, baseName));
    }

    private static String getMainContainerName(String clusterName, String baseName) {
        return "%s-%s".formatted(clusterName, baseName);
    }

    public static String getBookKeeperContainerName(GlobalSpec globalSpec) {
        return getMainContainerName(globalSpec.getName(), getComponentBaseName(globalSpec));
    }

    public static String getComponentBaseName(GlobalSpec globalSpec) {
        return globalSpec.getComponents().getBookkeeperBaseName();
    }

    public static String getResourceName(String clusterName, String baseName, String bkSetName,
                                         String overrideResourceName) {
        Objects.requireNonNull(bkSetName);
        if (overrideResourceName != null) {
            return overrideResourceName;
        }
        if (BOOKKEEPER_DEFAULT_SET.equals(bkSetName)) {
            return "%s-%s".formatted(clusterName, baseName);
        }
        return "%s-%s-%s".formatted(clusterName, baseName, bkSetName);
    }


    private ConfigMap configMap;
    private final String bookkeeperSet;

    public BookKeeperResourcesFactory(KubernetesClient client, String namespace,
                                      String bookkeeperSetName,
                                      BookKeeperSetSpec spec, GlobalSpec global,
                                      OwnerReference ownerReference) {
        super(client, namespace, getResourceName(global.getName(),
                        getComponentBaseName(global), bookkeeperSetName, spec.getOverrideResourceName()), spec, global,
                ownerReference);
        this.bookkeeperSet = bookkeeperSetName;
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
        List<ServicePort> ports = new ArrayList<>();
        ports.add(new ServicePortBuilder()
                .withName("server")
                .withPort(DEFAULT_BK_PORT)
                .build());
        if (spec.getService() != null && spec.getService().getAdditionalPorts() != null) {
            ports.addAll(spec.getService().getAdditionalPorts());
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
                .withPublishNotReadyAddresses(true)
                .withSelector(getMatchLabels(spec.getMatchLabels()))
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
        data.put("reppDnsResolverClass", "org.apache.pulsar.zookeeper.ZkBookieRackAffinityMapping");


        data.put("BOOKIE_MEM", "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled "
                + "-Dio.netty.recycler.linkCapacity=1024 -XX:+ExitOnOutOfMemoryError");
        data.put("BOOKIE_GC", "-XX:+UseG1GC");
        data.put("PULSAR_LOG_LEVEL", "info");
        data.put("PULSAR_LOG_ROOT_LEVEL", "info");
        data.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        data.put("statsProviderClass", "org.apache.bookkeeper.stats.prometheus.PrometheusMetricsProvider");

        data.put("zkServers", getZkServers());
        if (isTlsEnabledOnBookKeeper()) {
            data.put("tlsProvider", "OpenSSL");
            data.put("tlsProviderFactoryClass", "org.apache.bookkeeper.tls.TLSContextFactory");
            data.put("tlsCertificatePath", "/pulsar/certs/tls.crt");
            data.put("tlsKeyStoreType", "PEM");
            data.put("tlsKeyStore", "/pulsar/tls-pk8.key");
            data.put("tlsTrustStoreType", "PEM");
            data.put("tlsHostnameVerificationEnabled", "true");
            data.put("bookkeeperTLSClientAuthentication", "true");
            data.put("bookkeeperTLSTrustCertsFilePath", getFullCaPath());
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

    public void patchStatefulSet() {
        if (!isComponentEnabled()) {
            deleteStatefulSet();
            return;
        }
        final StatefulSet statefulSet = generateStatefulSet();
        patchResource(statefulSet);
    }

    public StatefulSet generateStatefulSet() {
        Map<String, String> labels = getLabels(spec.getLabels());
        Map<String, String> podLabels = getPodLabels(spec.getPodLabels());
        Objects.requireNonNull(configMap, "ConfigMap should have been created at this point");
        Map<String, String> podAnnotations = getPodAnnotations(spec.getPodAnnotations(), configMap, DEFAULT_HTTP_PORT + "");
        final Map<String, String> annotations = getAnnotations(spec.getAnnotations());


        String metaformatArg = "";
        final boolean tlsEnabledOnZooKeeper = isTlsEnabledOnZooKeeper();
        List<VolumeMount> initContainerVolumeMounts = new ArrayList<>();
        if (tlsEnabledOnZooKeeper) {
            initContainerVolumeMounts.add(createTlsCertsVolumeMount());
            metaformatArg += generateCertConverterScript() + " && ";
        }
        metaformatArg += "bin/apply-config-from-env.py conf/bookkeeper.conf "
                + "&& bin/bookkeeper shell metaformat --nonInteractive || true;";

        List<Container> initContainers = getInitContainers(spec.getInitContainers());
        initContainers.add(new ContainerBuilder()
                .withName(getMetadataFormatInitContainerName(global))
                .withImage(spec.getImage())
                .withImagePullPolicy(spec.getImagePullPolicy())
                .withCommand("sh", "-c")
                .withArgs(metaformatArg)
                .withEnvFrom(new EnvFromSourceBuilder()
                        .withNewConfigMapRef()
                        .withName(resourceName)
                        .endConfigMapRef()
                        .build())
                .withVolumeMounts(initContainerVolumeMounts)
                .build()
        );

        String mainArg = "bin/apply-config-from-env.py conf/bookkeeper.conf && ";
        final boolean tlsEnabledOnBookKeeper = isTlsEnabledOnBookKeeper();
        if (tlsEnabledOnBookKeeper) {
            mainArg +=
                    "openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key -out /pulsar/tls-pk8.key"
                            + " -nocrypt && ";
        }
        if (tlsEnabledOnZooKeeper) {
            mainArg += generateCertConverterScript() + " && ";
        }

        mainArg += "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar bookie";


        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        addAdditionalVolumes(spec.getAdditionalVolumes(), volumeMounts, volumes);
        if (tlsEnabledOnBookKeeper || tlsEnabledOnZooKeeper) {
            addTlsVolumes(volumeMounts, volumes, getTlsSecretNameForBookkeeper());
        }

        final String journalVolumeName = getJournalPvPrefix(spec, resourceName);
        final String ledgersVolumeName = getLedgersPvPrefix(spec, resourceName);

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
            persistentVolumeClaims.add(createPersistentVolumeClaim(journalVolumeName, spec.getVolumes().getJournal(),
                    Map.of()));
            persistentVolumeClaims.add(createPersistentVolumeClaim(ledgersVolumeName, spec.getVolumes().getLedgers(),
                    Map.of()));
        }


        final Container mainContainer = new ContainerBuilder()
                .withName(getBookKeeperContainerName(global))
                .withImage(spec.getImage())
                .withImagePullPolicy(spec.getImagePullPolicy())
                .withLivenessProbe(createProbe(spec.getProbes().getLiveness()))
                .withReadinessProbe(createProbe(spec.getProbes().getReadiness()))
                .withResources(spec.getResources())
                .withCommand("sh", "-c")
                .withArgs(mainArg)
                .withPorts(new ContainerPortBuilder()
                                .withName("client")
                                .withContainerPort(DEFAULT_BK_PORT)
                                .build(),
                        new ContainerPortBuilder()
                                .withName("http")
                                .withContainerPort(DEFAULT_HTTP_PORT)
                                .build())
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
                .withAnnotations(annotations)
                .withLabels(labels)
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
                .withNewSecurityContext().withFsGroup(0L).endSecurityContext()
                .withInitContainers(initContainers)
                .withContainers(containers)
                .withVolumes(volumes)
                .endSpec()
                .endTemplate()
                .withVolumeClaimTemplates(persistentVolumeClaims)
                .endSpec()
                .build();
        return statefulSet;
    }

    public static String getJournalPvPrefix(BookKeeperSetSpec spec, String resourceName) {
        return "%s%s-%s".formatted(
                ObjectUtils.firstNonNull(spec.getPvcPrefix(), ""),
                resourceName,
                spec.getVolumes().getJournal().getName());
    }

    public static String getLedgersPvPrefix(BookKeeperSetSpec spec, String resourceName) {
        return "%s%s-%s".formatted(
                ObjectUtils.firstNonNull(spec.getPvcPrefix(), ""),
                resourceName,
                spec.getVolumes().getLedgers().getName());
    }

    private Probe createProbe(ProbesConfig.ProbeConfig specProbe) {
        if (specProbe == null || !specProbe.getEnabled()) {
            return null;
        }
        return newProbeBuilder(specProbe)
                .withHttpGet(new HTTPGetActionBuilder()
                        .withPath("/api/v1/bookie/is_ready")
                        .withNewPort("http")
                        .build()
                )
                .build();
    }

    public void patchStorageClasses() {
        createStorageClassIfNeeded(spec.getVolumes().getJournal(), spec.getAnnotations(), spec.getLabels());
        createStorageClassIfNeeded(spec.getVolumes().getLedgers(), spec.getAnnotations(), spec.getLabels());
    }

    public void patchPodDisruptionBudget() {
        createPodDisruptionBudgetIfEnabled(spec.getPdb(),
                spec.getAnnotations(),
                spec.getLabels(),
                spec.getMatchLabels()
        );
    }

    @Override
    protected Map<String, String> getLabels(Map<String, String> customLabels) {
        final Map<String, String> labels = super.getLabels(customLabels);
        labels.put(CRDConstants.LABEL_RESOURCESET, bookkeeperSet);
        setRackLabel(labels);
        return labels;
    }

    @Override
    protected Map<String, String> getPodLabels(Map<String, String> customLabels) {
        final Map<String, String> labels = super.getPodLabels(customLabels);
        labels.put(CRDConstants.LABEL_RESOURCESET, bookkeeperSet);
        setRackLabel(labels);
        return labels;
    }

    @Override
    protected Map<String, String> getMatchLabels(Map<String, String> customMatchLabels) {
        final Map<String, String> matchLabels = super.getMatchLabels(customMatchLabels);
        if (!bookkeeperSet.equals(BOOKKEEPER_DEFAULT_SET)) {
            matchLabels.put(CRDConstants.LABEL_RESOURCESET, bookkeeperSet);
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
        return getRack(global, bookkeeperSet);
    }

    public static String getRack(GlobalSpec global, String bookkeeperSet) {
        if (global.getResourceSets() != null) {
            final ResourceSetConfig resourceSet = global.getResourceSets().get(bookkeeperSet);
            if (resourceSet != null) {
                return resourceSet.getRack();
            }
        }
        return null;
    }

    public int cleanupOrphanPVCs() {
        if (spec.getCleanUpPvcs() == null || !spec.getCleanUpPvcs()) {
            return 0;
        }
        log.infof("Cleaning up orphan PVCs for bookie-set '%s', replicas=%d", resourceName, spec.getReplicas());
        final String journalPvPrefix = getJournalPvPrefix(spec, resourceName);
        final String ledgersPvPrefix = getLedgersPvPrefix(spec, resourceName);
        final AtomicInteger pvcCount = new AtomicInteger(0);
        client.persistentVolumeClaims()
                .inNamespace(namespace)
                .withLabels(getLabels(spec.getLabels()))
                .list().getItems().forEach(pvc -> {
                    String name = pvc.getMetadata().getName();
                    if (name.startsWith(journalPvPrefix)
                            || name.startsWith(ledgersPvPrefix)) {
                        int idx = Integer.parseInt(name.substring(name.lastIndexOf('-') + 1));
                        if (idx >= spec.getReplicas()) {
                            log.infof("Force deletion of bookie pvc: %s", name);
                            client.resource(pvc).delete();
                            pvcCount.incrementAndGet();
                        }
                    }
                });
        return pvcCount.get();
    }
}
