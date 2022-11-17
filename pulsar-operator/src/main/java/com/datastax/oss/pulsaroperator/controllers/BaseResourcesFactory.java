package com.datastax.oss.pulsaroperator.controllers;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.SerializationUtil;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ObjectUtils;

public abstract class BaseResourcesFactory<T extends BaseComponentSpec<T>> {

    protected final KubernetesClient client;
    protected final String namespace;
    protected final T spec;
    protected final GlobalSpec global;
    protected final String resourceName;
    protected final OwnerReference ownerReference;
    private VersionInfo version;

    public BaseResourcesFactory(KubernetesClient client, String namespace, T spec,
                                GlobalSpec global, OwnerReference ownerReference) {
        this.client = client;
        this.namespace = namespace;
        this.spec = spec;
        this.global = global;
        this.resourceName = getResourceName();
        this.ownerReference = ownerReference;
    }

    protected abstract String getResourceName();

    protected abstract String getComponentBaseName();

    protected <R extends HasMetadata> void patchResource(R resource) {
        resource.getMetadata().setOwnerReferences(List.of(ownerReference));
        final R current = (R) client.resources(resource.getClass())
                .inNamespace(namespace)
                .withName(resource.getMetadata().getName())
                .get();
        if (current == null) {
            client.resource(resource)
                    .inNamespace(namespace)
                    .create();
        } else {
            client
                    .resource(current)
                    .inNamespace(namespace)
                    .patch(resource);
        }
    }

    protected void deleteStatefulSet() {
        client.apps().statefulSets()
                .inNamespace(namespace)
                .withName(resourceName)
                .delete();
    }

    protected void deleteDeployment() {
        client.apps().deployments()
                .inNamespace(namespace)
                .withName(resourceName)
                .delete();
    }

    protected Map<String, String> getLabels() {
        return Map.of(
                CRDConstants.LABEL_APP, global.getName(),
                CRDConstants.LABEL_COMPONENT, getComponentBaseName(),
                CRDConstants.LABEL_CLUSTER, global.getName()
        );
    }

    protected Map<String, String> getMatchLabels() {
        Map<String, String> matchLabels = Map.of(
                "app", global.getName(),
                "component", getComponentBaseName()
        );
        return matchLabels;
    }

    protected boolean isPdbSupported() {
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

    protected boolean isTlsEnabledGlobally() {
        return global.getTls() != null && global.getTls().getEnabled();
    }

    protected boolean isTlsEnabledOnZooKeeper() {
        return isTlsEnabledGlobally()
                && global.getTls().getZookeeper() != null
                && global.getTls().getZookeeper().isEnabled();
    }

    protected boolean isTlsEnabledOnBookKeeper() {
        return isTlsEnabledGlobally()
                && global.getTls().getBookkeeper() != null
                && global.getTls().getBookkeeper().isEnabled();
    }

    protected boolean isTlsEnabledOnBroker() {
        // TODO: update when implementing brokers
        return isTlsEnabledGlobally();
    }

    protected String getServiceDnsSuffix() {
        return "%s.svc.%s".formatted(namespace, global.getKubernetesClusterDomain());
    }

    protected String getZkServers() {
        return "%s-%s-ca.%s:%d".formatted(global.getName(),
                global.getComponents().getZookeeperBaseName(),
                getServiceDnsSuffix(),
                isTlsEnabledOnZooKeeper() ? 2281 : 2181);
    }

    private String getBrokerWebServiceUrl(boolean tls) {
        return "%s://%s-%s.%s:%d/".formatted(
                tls ? "https" : "http",
                global.getName(),
                global.getComponents().getBrokerBaseName(),
                getServiceDnsSuffix(), tls ? 8443 : 8080);
    }

    protected String getBrokerWebServiceUrl() {
        final boolean tls = isTlsEnabledOnBroker();
        return getBrokerWebServiceUrl(tls);
    }

    protected String getBrokerWebServiceUrlTls() {
        return getBrokerWebServiceUrl(true);
    }

    protected String getBrokerWebServiceUrlPlain() {
        return getBrokerWebServiceUrl(false);
    }

    private String getBrokerServiceUrl(boolean tls) {
        return "%s://%s-%s.%s:%d/".formatted(
                tls ? "pulsar+ssl" : "pulsar",
                global.getName(),
                global.getComponents().getBrokerBaseName(),
                getServiceDnsSuffix(), tls ? 6651 : 6650);
    }

    protected String getBrokerServiceUrl() {
        final boolean tls = isTlsEnabledOnBroker();
        return getBrokerServiceUrl(tls);
    }

    protected String getBrokerServiceUrlTls() {
        return getBrokerServiceUrl(true);
    }

    protected String getBrokerServiceUrlPlain() {
        return getBrokerServiceUrl(false);
    }


    protected String getTlsSecretNameForZookeeper() {
        final String name = global.getTls().getZookeeper() == null
                ? null : global.getTls().getZookeeper().getTlsSecretName();
        return ObjectUtils.firstNonNull(
                name,
                global.getTls().getDefaultSecretName());
    }

    protected String getTlsSecretNameForBookkeeper() {
        final String name = global.getTls().getBookkeeper() == null
                ? null : global.getTls().getBookkeeper().getTlsSecretName();
        return ObjectUtils.firstNonNull(
                name,
                global.getTls().getDefaultSecretName());
    }

    protected String getTlsSecretNameForBroker() {
        final String name = global.getTls().getBroker() == null
                ? null : global.getTls().getBroker().getTlsSecretName();
        return ObjectUtils.firstNonNull(
                name,
                global.getTls().getDefaultSecretName());
    }

    protected void addTlsVolumesIfEnabled(List<VolumeMount> volumeMounts, List<Volume> volumes,
                                          String secretName) {
        if (!isTlsEnabledGlobally()) {
            return;
        }
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
                        .withNewSecret().withSecretName(secretName)
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

    protected boolean createStorageClassIfNeeded(VolumeConfig volumeConfig) {
        if (!global.getPersistence()) {
            return false;
        }
        if (volumeConfig.getExistingStorageClassName() != null) {
            return false;
        }
        if (volumeConfig.getStorageClass() == null) {
            return false;
        }
        final String volumeFullName = resourceName + "-" + volumeConfig.getName();
        final StorageClassConfig storageClass = volumeConfig.getStorageClass();
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

        final StorageClass storage = new StorageClassBuilder()
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
        patchResource(storage);
        return true;
    }

    protected void createPodDisruptionBudgetIfEnabled(PodDisruptionBudgetConfig pdb) {
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

        patchResource(pdbResource);
    }

    protected Container createWaitBKReadyContainer() {
        final String bkBaseName = global.getComponents().getBookkeeperBaseName();
        final String bkHostname = "%s-%s-%d.%s-%s.%s"
                .formatted(global.getName(), bkBaseName, 0, global.getName(), bkBaseName, namespace);
        return new ContainerBuilder()
                .withName("wait-bookkeeper-ready")
                .withImage(spec.getImage())
                .withImagePullPolicy(spec.getImagePullPolicy())
                .withCommand("sh", "-c")
                .withArgs("""
                        until nslookup %s; do
                            sleep 3;
                        done;
                        """.formatted(bkHostname))
                .build();
    }

    protected Map<String, String> getDefaultAnnotations() {
        Map<String, String> annotations = new HashMap<>();
        annotations.put("prometheus.io/scrape", "true");
        annotations.put("prometheus.io/port", "8080");
        return annotations;
    }

    protected void addConfigMapChecksumAnnotation(ConfigMap configMap,
                                                  Map<String, String> annotations) {
        if (!global.getRestartOnConfigMapChange()) {
            return;
        }
        String checksum = DigestUtils.sha256Hex(SerializationUtil.writeAsJsonBytes(configMap.getData()));
        annotations.put(
                "%s/configmap-%s".formatted(CRDConstants.GROUP, configMap.getMetadata().getName()),
                checksum
        );
    }
}
