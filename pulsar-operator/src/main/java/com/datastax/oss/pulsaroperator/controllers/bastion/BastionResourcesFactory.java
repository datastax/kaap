package com.datastax.oss.pulsaroperator.controllers.bastion;

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bastion.BastionSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class BastionResourcesFactory extends BaseResourcesFactory<BastionSpec> {

    private ConfigMap configMap;

    public BastionResourcesFactory(KubernetesClient client, String namespace,
                                   BastionSpec spec, GlobalSpec global,
                                   OwnerReference ownerReference) {
        super(client, namespace, spec, global, ownerReference);
    }

    @Override
    protected String getComponentBaseName() {
        return global.getComponents().getBastionBaseName();
    }

    @Override
    protected String getResourceName() {
        return "%s-%s".formatted(global.getName(), getComponentBaseName());
    }

    public void patchConfigMap() {
        Map<String, String> data = new HashMap<>();
        boolean targetProxy = spec.getTargetProxy() != null && spec.getTargetProxy();
        final String brokerServiceUrl = targetProxy ? getProxyServiceUrl() : getBrokerServiceUrl();
        final String webServiceUrl = targetProxy ? getProxyWebServiceUrl() : getBrokerWebServiceUrl();
        data.put("brokerServiceUrl", brokerServiceUrl);
        data.put("webServiceUrl", webServiceUrl);
        data.put("PULSAR_MEM", "-XX:+ExitOnOutOfMemoryError");
        data.put("PULSAR_GC", "-XX:+UseG1GC");
        data.put("PULSAR_LOG_LEVEL", "info");
        data.put("PULSAR_LOG_ROOT_LEVEL", "info");
        data.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");

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


    public void patchDeployment() {
        final int replicas = spec.getReplicas();
        if (replicas == 0) {
            log.warn("Got replicas=0, deleting deployments");
            deleteDeployment();
            return;
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
        addTlsVolumesIfEnabled(volumeMounts, volumes, getTlsSecretNameForBroker());
        final String mainArg = "bin/apply-config-from-env.py conf/client.conf && "
                + "exec /bin/bash -c \"trap : TERM INT; sleep infinity & wait\"";

        List<Container> containers = new ArrayList<>();
        containers.add(
                new ContainerBuilder()
                        .withName(resourceName)
                        .withImage(spec.getImage())
                        .withImagePullPolicy(spec.getImagePullPolicy())
                        .withResources(spec.getResources())
                        .withCommand("sh", "-c")
                        .withArgs(mainArg)
                        .withEnvFrom(new EnvFromSourceBuilder()
                                .withNewConfigMapRef()
                                .withName(resourceName)
                                .endConfigMapRef()
                                .build())
                        .withVolumeMounts(volumeMounts)
                        .build()
        );
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withReplicas(spec.getReplicas())
                .withNewSelector()
                .withMatchLabels(getMatchLabels())
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .withAnnotations(allAnnotations)
                .endMetadata()
                .withNewSpec()
                .withDnsConfig(global.getDnsConfig())
                .withNodeSelector(spec.getNodeSelectors())
                .withTerminationGracePeriodSeconds(spec.getGracePeriod().longValue())
                .withContainers(containers)
                .withVolumes(volumes)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        patchResource(deployment);
    }

}

