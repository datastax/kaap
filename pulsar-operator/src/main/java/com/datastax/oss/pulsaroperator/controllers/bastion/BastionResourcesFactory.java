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
    protected boolean isComponentEnabled() {
        return spec.getReplicas() > 0;
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

        if (isAuthTokenEnabled()) {
            data.put("authParams", "file:///pulsar/token-superuser-stripped.jwt");
            data.put("authPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
        }
        boolean targetTlsEnabled = targetProxy ? isTlsEnabledOnProxy() : isTlsEnabledOnBroker();
        if (targetTlsEnabled) {
            data.put("tlsEnableHostnameVerification", "true");
            data.put("tlsTrustCertsFilePath", "/pulsar/certs/ca.crt");
        }

        if (spec.getConfig() != null) {
            data.putAll(spec.getConfig());
        }

        final ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(getLabels()).endMetadata()
                .withData(handleConfigPulsarPrefix(data))
                .build();
        patchResource(configMap);
        this.configMap = configMap;
    }


    public void patchDeployment() {
        if (!isComponentEnabled()) {
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
        addTlsVolumesIfEnabled(volumeMounts, volumes, getTlsSsCaSecretName());
        String mainArg = "";

        if (isAuthTokenEnabled()) {
            addSecretTokenVolume(volumeMounts, volumes, "private-key");
            addSecretTokenVolume(volumeMounts, volumes, "public-key");
            addSecretTokenVolume(volumeMounts, volumes, "admin");
            addSecretTokenVolume(volumeMounts, volumes, "superuser");
            mainArg += "cat /pulsar/token-superuser/superuser.jwt | tr -d '\\n' > /pulsar/token-superuser-stripped"
                    + ".jwt && ";
        }


        mainArg += "bin/apply-config-from-env.py conf/client.conf && "
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

