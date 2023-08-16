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
package com.datastax.oss.kaap.controllers.autorecovery;

import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.autorecovery.AutorecoverySpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
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
public class AutorecoveryResourcesFactory extends BaseResourcesFactory<AutorecoverySpec> {

    public static String getComponentBaseName(GlobalSpec globalSpec) {
        return globalSpec.getComponents().getAutorecoveryBaseName();
    }

    public static List<String> getContainerNames(String resourceName) {
        return List.of(getMainContainerName(resourceName));
    }

    private static String getMainContainerName(String resourceName) {
        return resourceName;
    }

    public static String getResourceName(String clusterName, String baseName) {
        return "%s-%s".formatted(clusterName, baseName);
    }

    public static String getResourceName(GlobalSpec globalSpec, String baseName) {
        return getResourceName(globalSpec.getName(), baseName);
    }

    private ConfigMap configMap;

    public AutorecoveryResourcesFactory(KubernetesClient client, String namespace,
                                        AutorecoverySpec spec, GlobalSpec global,
                                        OwnerReference ownerReference) {
        super(client, namespace, getResourceName(global, getComponentBaseName(global)), spec, global, ownerReference);
    }

    @Override
    protected String getComponentBaseName() {
        return getComponentBaseName(global);
    }

    @Override
    protected boolean isComponentEnabled() {
        return spec.getReplicas() > 0;
    }

    public void patchConfigMap() {
        Map<String, String> data = new HashMap<>();
        final String zkServers = getZkServers();
        data.put("reppDnsResolverClass", "org.apache.pulsar.zookeeper.ZkBookieRackAffinityMapping");
        data.put("zkServers", zkServers);
        data.put("BOOKIE_MEM", "-Xms512m -Xmx512m -XX:+ExitOnOutOfMemoryError");
        data.put("BOOKIE_GC", "-XX:+UseG1GC");
        data.put("PULSAR_LOG_LEVEL", "info");
        data.put("PULSAR_LOG_ROOT_LEVEL", "info");
        data.put("PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info");
        data.put("ensemblePlacementPolicy", "org.apache.bookkeeper.client.RegionAwareEnsemblePlacementPolicy");

        if (isTlsEnabledOnBookKeeper()) {
            data.put("tlsHostnameVerificationEnabled", "true");
            data.put("tlsProvider", "OpenSSL");
            data.put("tlsProviderFactoryClass", "org.apache.bookkeeper.tls.TLSContextFactory");
            data.put("tlsCertificatePath", "/pulsar/certs/tls.crt");
            data.put("tlsKeyStoreType", "PEM");
            data.put("tlsKeyStore", "/pulsar/tls-pk8.key");
            data.put("tlsTrustStoreType", "PEM");
            data.put("tlsClientAuthentication", "true");
            data.put("tlsTrustStore", getFullCaPath());
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


    public void patchDeployment() {
        if (!isComponentEnabled()) {
            deleteDeployment();
            return;
        }
        Map<String, String> labels = getLabels(spec.getLabels());
        Map<String, String> podLabels = getPodLabels(spec.getPodLabels());
        Objects.requireNonNull(configMap, "ConfigMap should have been created at this point");
        Map<String, String> podAnnotations = getPodAnnotations(spec.getPodAnnotations(), configMap,
                BookKeeperResourcesFactory.DEFAULT_HTTP_PORT + "");
        final Map<String, String> annotations = getAnnotations(spec.getAnnotations());

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        final boolean tlsEnabledOnZooKeeper = isTlsEnabledOnZooKeeper();
        final boolean tlsEnabledOnBookKeeper = isTlsEnabledOnBookKeeper();
        if (tlsEnabledOnZooKeeper || tlsEnabledOnBookKeeper) {
            addTlsVolumesIfEnabled(volumeMounts, volumes, getTlsSecretNameForAutorecovery());
        }
        String mainArg = "bin/apply-config-from-env.py conf/bookkeeper.conf && ";
        if (tlsEnabledOnBookKeeper) {
            mainArg += "openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key "
                    + "-out /pulsar/tls-pk8.key -nocrypt && ";
        }
        if (tlsEnabledOnZooKeeper) {
            mainArg += generateCertConverterScript() + " && ";
        }
        mainArg += "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/bookkeeper autorecovery";


        List<ContainerPort> containerPorts = new ArrayList<>();
        containerPorts.add(new ContainerPortBuilder()
                .withName("http")
                .withContainerPort(8000)
                .build()
        );
        List<Container> containers = getSidecars(spec.getSidecars());
        containers.add(
                new ContainerBuilder()
                        .withName(getMainContainerName(resourceName))
                        .withImage(spec.getImage())
                        .withImagePullPolicy(spec.getImagePullPolicy())
                        .withResources(spec.getResources())
                        .withCommand("sh", "-c")
                        .withArgs(mainArg)
                        .withPorts(containerPorts)
                        .withEnvFrom(new EnvFromSourceBuilder()
                                .withNewConfigMapRef()
                                .withName(resourceName)
                                .endConfigMapRef()
                                .build())
                        .withEnv(spec.getEnv())
                        .withVolumeMounts(volumeMounts)
                        .build()
        );
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(resourceName)
                .withNamespace(namespace)
                .withLabels(labels)
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withReplicas(spec.getReplicas())
                .withNewSelector()
                .withMatchLabels(getMatchLabels(spec.getMatchLabels()))
                .endSelector()
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
                        spec.getMatchLabels()
                ))
                .withTerminationGracePeriodSeconds(spec.getGracePeriod().longValue())
                .withPriorityClassName(global.getPriorityClassName())
                .withContainers(containers)
                .withInitContainers(getInitContainers(spec.getInitContainers()))
                .withVolumes(volumes)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        patchResource(deployment);
    }

}
