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
package com.datastax.oss.pulsaroperator.controllers.proxy;

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
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
public class ProxyResourcesFactory extends BaseResourcesFactory<ProxySpec> {

    private static final Map<String, String> DEFAULT_CONFIG_MAP =
            Map.of("PULSAR_MEM", "-Xms1g -Xmx1g -XX:MaxDirectMemorySize=1g",
                    "PULSAR_GC", "-XX:+UseG1GC",
                    "PULSAR_LOG_LEVEL", "info",
                    "PULSAR_LOG_ROOT_LEVEL", "info",
                    "PULSAR_EXTRA_OPTS", "-Dpulsar.log.root.level=info",
                    "numHttpServerThreads", "10"
            );

    public static String getComponentBaseName(GlobalSpec globalSpec) {
        return globalSpec.getComponents().getProxyBaseName();
    }

    @Override
    protected boolean isComponentEnabled() {
        return spec.getReplicas() > 0;
    }

    private ConfigMap configMap;
    private ConfigMap wsConfigMap;

    public ProxyResourcesFactory(KubernetesClient client, String namespace,
                                 ProxySpec spec, GlobalSpec global,
                                 OwnerReference ownerReference) {
        super(client, namespace, spec, global, ownerReference);
    }

    @Override
    protected String getComponentBaseName() {
        return getComponentBaseName(global);
    }

    public void patchService() {

        final ProxySpec.ServiceConfig serviceSpec = spec.getService();

        Map<String, String> annotations = null;
        if (serviceSpec.getAnnotations() != null) {
            annotations = serviceSpec.getAnnotations();
        }
        List<ServicePort> ports = new ArrayList<>();
        final boolean tlsEnabledOnProxy = isTlsEnabledOnProxy();
        if (tlsEnabledOnProxy) {
            ports.add(new ServicePortBuilder()
                    .withName("https")
                    .withPort(8443)
                    .build());
            ports.add(new ServicePortBuilder()
                    .withName("pulsarssl")
                    .withPort(6651)
                    .build());

        }
        if (!tlsEnabledOnProxy || serviceSpec.getEnablePlainTextWithTLS()) {
            ports.add(new ServicePortBuilder()
                    .withName("http")
                    .withPort(8080)
                    .build());
            ports.add(new ServicePortBuilder()
                    .withName("pulsar")
                    .withPort(6650)
                    .build());
        }
        if (serviceSpec.getAdditionalPorts() != null) {
            ports.addAll(serviceSpec.getAdditionalPorts());
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
                .withLoadBalancerIP(serviceSpec.getLoadBalancerIP())
                .withType(serviceSpec.getType())
                .withSelector(getMatchLabels())
                .endSpec()
                .build();

        patchResource(service);
    }


    public void patchConfigMap() {
        Map<String, String> data = new HashMap<>();
        final String zkServers = getZkServers();
        data.put("brokerServiceURL", getBrokerServiceUrlPlain());
        data.put("brokerServiceURLTLS", getBrokerServiceUrlTls());
        data.put("brokerWebServiceURL", getBrokerWebServiceUrlPlain());
        data.put("brokerWebServiceURLTLS", getBrokerWebServiceUrlTls());
        data.put("zookeeperServers", zkServers);
        data.put("configurationStoreServers", zkServers);
        data.put("clusterName", global.getName());
        boolean isStandaloneFunctionsWorker = spec.getStandaloneFunctionsWorker() != null
                && spec.getStandaloneFunctionsWorker();
        final String functionsWorkerServiceUrl = getFunctionsWorkerServiceUrl();
        if (isStandaloneFunctionsWorker) {
            data.put("functionWorkerWebServiceURL", functionsWorkerServiceUrl);
        }

        data.putAll(DEFAULT_CONFIG_MAP);

        if (isAuthTokenEnabled()) {
            final AuthConfig.TokenAuthenticationConfig tokenConfig = global.getAuth().getToken();
            data.put("authenticationEnabled", "true");
            data.put("authorizationEnabled", "true");
            data.put("superUserRoles", tokenConfig.superUserRolesAsString());
            data.put("authenticationProviders", "org.apache.pulsar.broker.authentication.AuthenticationProviderToken");
            data.put("tokenPublicKey", "file:///pulsar/token-public-key/%s".formatted(tokenConfig.getPublicKeyFile()));
            data.put("brokerClientAuthenticationPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
            data.put("brokerClientAuthenticationParameters", "file:///pulsar/token-proxy/proxy.jwt");
        }
        if (isTlsEnabledOnProxy()) {
            data.put("tlsEnabledWithKeyStore", "true");
            data.put("tlsKeyStore", "/pulsar/tls.keystore.jks");
            data.put("tlsTrustStore", "/pulsar/tls.truststore.jks");
            data.put("brokerClientTlsTrustStore", "/pulsar/tls.truststore.jks");
            data.put("tlsEnabledInProxy", "true");
            data.put("tlsCertificateFilePath", "/pulsar/certs/tls.crt");
            data.put("tlsKeyFilePath", "/pulsar/tls-pk8.key");
            data.put("tlsTrustCertsFilePath", "/pulsar/certs/ca.crt");
            data.put("brokerClientTrustCertsFilePath", "/pulsar/certs/ca.crt");
            data.put("brokerServicePortTls", "6651");
            data.put("webServicePortTls", "8443");
            data.put("servicePortTls", "6651");
            if (global.getTls().getProxy().getEnabledWithBroker()) {
                data.put("tlsEnabledWithBroker", "true");
                data.put("tlsHostnameVerificationEnabled", "true");
            }
            if (isTlsEnabledOnFunctionsWorker()) {
                data.put("functionWorkerWebServiceURLTLS", functionsWorkerServiceUrl);
            }
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

    public void patchConfigMapWsConfig() {
        final ProxySpec.WebSocketConfig webSocketConfig = spec.getWebSocket();
        if (!webSocketConfig.getEnabled()) {
            return;
        }

        Map<String, String> data = new HashMap<>();
        final String zkServers = getZkServers();
        data.put("brokerServiceUrl", getBrokerServiceUrlPlain());
        data.put("brokerServiceUrlTls", getBrokerServiceUrlTls());
        data.put("serviceUrl", getBrokerWebServiceUrlPlain());
        data.put("serviceUrlTls", getBrokerWebServiceUrlTls());
        data.put("zookeeperServers", zkServers);
        data.put("configurationStoreServers", zkServers);
        data.put("clusterName", global.getName());

        data.putAll(DEFAULT_CONFIG_MAP);
        if (isAuthTokenEnabled()) {
            data.put("authenticationProviders", "org.apache.pulsar.broker.authentication.AuthenticationProviderToken,"
                    + "org.apache.pulsar.broker.authentication.AuthenticationProviderTls");
            final AuthConfig.TokenAuthenticationConfig tokenConfig = global.getAuth().getToken();
            data.put("authenticationEnabled", "true");
            data.put("authorizationEnabled", "true");
            data.put("superUserRoles", tokenConfig.superUserRolesAsString());
            data.put("authenticationProviders", "org.apache.pulsar.broker.authentication.AuthenticationProviderToken,"
                    + "org.apache.pulsar.broker.authentication.AuthenticationProviderTls");
            data.put("tokenPublicKey", "file:///pulsar/token-public-key/%s".formatted(tokenConfig.getPublicKeyFile()));
            data.put("brokerClientAuthenticationPlugin", "org.apache.pulsar.client.impl.auth.AuthenticationToken");
            data.put("brokerClientAuthenticationParameters", "file:///pulsar/token-websocket/websocket.jwt");
        }
        if (isTlsEnabledOnProxy()) {
            data.put("webServicePortTls", "8001");
            data.put("tlsEnabled", "true");
            data.put("tlsCertificateFilePath", "/pulsar/certs/tls.crt");
            data.put("tlsKeyFilePath", "/pulsar/tls-pk8.key");
            data.put("tlsEnabledWithKeyStore", "true");
            data.put("tlsKeyStore", "/pulsar/tls.keystore.jks");
            data.put("tlsTrustStore", "/pulsar/tls.truststore.jks");
            if (global.getTls().getProxy().getEnabledWithBroker()) {
                data.put("brokerClientTlsEnabled", "true");
                data.put("tlsTrustCertsFilePath", "/pulsar/certs/ca.crt");
                data.put("brokerClientTrustCertsFilePath", "/pulsar/certs/ca.crt");
            }
        }


        if (spec.getConfig() != null) {
            data.putAll(spec.getConfig());
        }
        if (!data.containsKey("webServicePort")) {
            data.put("webServicePort", "8000");
        }

        final ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withName("%s-ws".formatted(resourceName))
                .withNamespace(namespace)
                .withLabels(getLabels()).endMetadata()
                .withData(handleConfigPulsarPrefix(data))
                .build();
        patchResource(configMap);
        this.wsConfigMap = configMap;
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
        if (spec.getWebSocket().getEnabled()) {
            Objects.requireNonNull(wsConfigMap, "WsConfigMap should have been created at this point");
            addConfigMapChecksumAnnotation(wsConfigMap, allAnnotations);
        }
        if (spec.getAnnotations() != null) {
            allAnnotations.putAll(spec.getAnnotations());
        }

        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();
        addAdditionalVolumes(spec.getAdditionalVolumes(), volumeMounts, volumes);
        addTlsVolumesIfEnabled(volumeMounts, volumes, getTlsSecretNameForProxy());
        if (isAuthTokenEnabled()) {
            addSecretTokenVolume(volumeMounts, volumes, "public-key");
            addSecretTokenVolume(volumeMounts, volumes, "private-key");
            addSecretTokenVolume(volumeMounts, volumes, "proxy");
            addSecretTokenVolume(volumeMounts, volumes, "websocket");
            addSecretTokenVolume(volumeMounts, volumes, "superuser");
        }

        List<Container> initContainers = new ArrayList<>();

        if (spec.getInitContainer() != null) {
            volumes.add(
                    new VolumeBuilder()
                            .withName("lib-data")
                            .withNewEmptyDir().endEmptyDir()
                            .build()
            );
            volumeMounts.add(
                    new VolumeMountBuilder()
                            .withName("lib-data")
                            .withMountPath(spec.getInitContainer().getEmptyDirPath())
                            .build()
            );
            initContainers.add(new ContainerBuilder()
                    .withName("add-libs")
                    .withImage(spec.getInitContainer().getImage())
                    .withImagePullPolicy(spec.getInitContainer().getImagePullPolicy())
                    .withCommand(spec.getInitContainer().getCommand())
                    .withArgs(spec.getInitContainer().getArgs())
                    .withVolumeMounts(new VolumeMountBuilder()
                            .withName("lib-data")
                            .withMountPath(spec.getInitContainer().getEmptyDirPath())
                            .build())
                    .build());
        }

        final Probe probe = createProbe();
        String mainArg = "";
        if (isAuthTokenEnabled()) {
            mainArg += "cat /pulsar/token-superuser/superuser.jwt | tr -d '\\n' > /pulsar/token-superuser-stripped"
                    + ".jwt && ";
        }
        if (isTlsEnabledGlobally()) {
            mainArg += "openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key "
                    + "-out /pulsar/tls-pk8.key -nocrypt && "
                    + generateCertConverterScript() + " && ";
        }
        mainArg += "bin/apply-config-from-env.py conf/proxy.conf && ";
        mainArg += "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar proxy";


        List<ContainerPort> containerPorts = new ArrayList<>();
        containerPorts.add(new ContainerPortBuilder()
                .withName("wss")
                .withContainerPort(8001)
                .build()
        );
        List<Container> containers = new ArrayList<>();
        if (spec.getService().getAdditionalPorts() != null) {
            spec.getService().getAdditionalPorts()
                    .stream()
                    .map(s -> new ContainerPortBuilder()
                            .withName(s.getName())
                            .withContainerPort(s.getPort())
                            .build())
                    .forEachOrdered(p -> containerPorts.add(p));
        }
        containers.add(
                new ContainerBuilder()
                        .withName(resourceName)
                        .withImage(spec.getImage())
                        .withImagePullPolicy(spec.getImagePullPolicy())
                        .withLivenessProbe(probe)
                        .withReadinessProbe(probe)
                        .withResources(spec.getResources())
                        .withCommand("sh", "-c")
                        .withArgs(mainArg)
                        .withPorts(containerPorts)
                        .withEnvFrom(new EnvFromSourceBuilder()
                                .withNewConfigMapRef()
                                .withName(resourceName)
                                .endConfigMapRef()
                                .build())
                        .withVolumeMounts(volumeMounts)
                        .build()
        );
        final ProxySpec.WebSocketConfig webSocket = spec.getWebSocket();
        if (webSocket.getEnabled()) {

            String wsArg = "";
            if (isAuthTokenEnabled()) {
                wsArg += "echo \"tokenPublicKey=\" >> /pulsar/conf/websocket.conf && ";
            }
            if (isTlsEnabledGlobally()) {
                wsArg += "openssl pkcs8 -topk8 -inform PEM -outform PEM -in /pulsar/certs/tls.key "
                        + "-out /pulsar/tls-pk8.key -nocrypt && "
                        + generateCertConverterScript() + " && ";
            }
            wsArg += "bin/apply-config-from-env.py conf/websocket.conf && ";
            wsArg += "OPTS=\"${OPTS} -Dlog4j2.formatMsgNoLookups=true\" exec bin/pulsar websocket";


            final String wsResourceName = "%s-ws".formatted(resourceName);
            containers.add(
                    new ContainerBuilder()
                            .withName(wsResourceName)
                            .withImage(spec.getImage())
                            .withImagePullPolicy(spec.getImagePullPolicy())
                            .withResources(webSocket.getResources())
                            .withCommand("sh", "-c")
                            .withArgs(wsArg)
                            .withPorts(List.of(
                                    new ContainerPortBuilder()
                                            .withName("http")
                                            .withContainerPort(8080)
                                            .build()
                            ))
                            .withEnvFrom(new EnvFromSourceBuilder()
                                    .withNewConfigMapRef()
                                    .withName(wsResourceName)
                                    .endConfigMapRef()
                                    .build())
                            .withVolumeMounts(volumeMounts)
                            .build()
            );
        }

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
                .withStrategy(spec.getUpdateStrategy())
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .withAnnotations(allAnnotations)
                .endMetadata()
                .withNewSpec()
                .withTolerations(spec.getTolerations())
                .withDnsConfig(global.getDnsConfig())
                .withNodeSelector(spec.getNodeSelectors())
                .withTerminationGracePeriodSeconds(spec.getGracePeriod().longValue())
                .withInitContainers(initContainers)
                .withContainers(containers)
                .withVolumes(volumes)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
        patchResource(deployment);
    }

    private Probe createProbe() {
        final ProbeConfig specProbe = spec.getProbe();
        if (specProbe == null || !specProbe.getEnabled()) {
            return null;
        }
        final String authHeader = isAuthTokenEnabled()
                ? "-H \"Authorization: Bearer $(cat /pulsar/token-superuser/superuser.jwt | tr -d '\\r')\"" : "";
        return new ProbeBuilder()
                .withNewExec()
                .withCommand("sh", "-c", "curl -s --max-time %d --fail %s http://localhost:8080/metrics/ > /dev/null"
                        .formatted(specProbe.getTimeout(), authHeader))
                .endExec()
                .withInitialDelaySeconds(specProbe.getInitial())
                .withPeriodSeconds(specProbe.getPeriod())
                .withTimeoutSeconds(specProbe.getTimeout())
                .build();
    }

    public void patchPodDisruptionBudget() {
        createPodDisruptionBudgetIfEnabled(spec.getPdb());
    }

}

