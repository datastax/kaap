package com.datastax.oss.pulsaroperator.controllers;

import com.datastax.oss.pulsaroperator.crds.BaseComponentSpec;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import java.util.List;
import java.util.Map;

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
        resourceName = String.format("%s-%s", global.getName(), getComponentBaseName());
        this.ownerReference = ownerReference;
    }

    protected abstract String getComponentBaseName();

    protected void commonCreateOrReplace(HasMetadata resource) {
        resource.getMetadata().setOwnerReferences(List.of(ownerReference));
        client.resource(resource).inNamespace(namespace).createOrReplace();
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
        return global.getTls() != null && global.getTls().isEnabled();
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

    protected void addTlsVolumesIfEnabled(List<VolumeMount> volumeMounts, List<Volume> volumes) {
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

}
