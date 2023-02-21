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
package com.datastax.oss.pulsaroperator.controllers.bookkeeper;

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.configs.ProbesConfig;
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
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.ObjectUtils;

@JBossLog
public class BookKeeperResourcesFactory extends BaseResourcesFactory<BookKeeperSpec> {

    public static final int DEFAULT_BK_PORT = 3181;
    public static final int DEFAULT_HTTP_PORT = 8000;

    public static String getBookKeeperContainerName(GlobalSpec globalSpec) {
        return getResourceName(globalSpec.getName(), globalSpec.getComponents().getBookkeeperBaseName());
    }

    public static String getComponentBaseName(GlobalSpec globalSpec) {
        return globalSpec.getComponents().getBookkeeperBaseName();
    }


    private ConfigMap configMap;

    public BookKeeperResourcesFactory(KubernetesClient client, String namespace,
                                      BookKeeperSpec spec, GlobalSpec global,
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
            log.warn("Got replicas=0, deleting sts");
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
        Map<String, String> podAnnotations = getPodAnnotations(spec.getPodAnnotations(), configMap);
        final Map<String, String> annotations = getAnnotations(spec.getAnnotations());

        List<Container> initContainers = new ArrayList<>();
        String metaformatArg = "";
        final boolean tlsEnabledOnZooKeeper = isTlsEnabledOnZooKeeper();
        List<VolumeMount> initContainerVolumeMounts = new ArrayList<>();
        if (tlsEnabledOnZooKeeper) {
            initContainerVolumeMounts.add(createTlsCertsVolumeMount());
            metaformatArg += generateCertConverterScript() + " && ";
        }
        metaformatArg += "bin/apply-config-from-env.py conf/bookkeeper.conf "
                + "&& bin/bookkeeper shell metaformat --nonInteractive || true;";

        initContainers.add(new ContainerBuilder()
                .withName("pulsar-bookkeeper-metaformat")
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
            addTlsVolumesIfEnabled(volumeMounts, volumes, getTlsSecretNameForBookkeeper());
        }

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
                        .build()
        );
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
                .withNodeSelector(spec.getNodeSelectors())
                .withAffinity(getAffinity(
                        spec.getNodeAffinity(),
                        spec.getAntiAffinity(),
                        spec.getMatchLabels()
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

}
