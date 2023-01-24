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
package com.datastax.oss.pulsaroperator.controllers;

import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.SerializationUtil;
import com.datastax.oss.pulsaroperator.crds.configs.AdditionalVolumesConfig;
import com.datastax.oss.pulsaroperator.crds.configs.AntiAffinityConfig;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import com.datastax.oss.pulsaroperator.crds.configs.VolumeConfig;
import com.datastax.oss.pulsaroperator.crds.configs.tls.TlsConfig;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PodAffinityTerm;
import io.fabric8.kubernetes.api.model.PodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRule;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ObjectUtils;

@JBossLog
public abstract class BaseResourcesFactory<T> {

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

    public static String getResourceName(String clusterName, String baseName) {
        return "%s-%s".formatted(clusterName, baseName);
    }

    ;

    protected String getResourceName() {
        return getResourceName(global.getName(), getComponentBaseName());
    }

    ;

    protected abstract String getComponentBaseName();

    protected abstract boolean isComponentEnabled();

    private static boolean isImmutableResource(Class<? extends HasMetadata> resourceClass) {
        if (resourceClass.isAssignableFrom(Job.class)) {
            return true;
        }
        return false;
    }

    private static boolean isNonNamespacedResource(Class<? extends HasMetadata> resourceClass) {
        if (resourceClass.isAssignableFrom(StorageClass.class)) {
            return true;
        }
        return false;
    }

    protected <R extends HasMetadata> void patchResource(R resource) {
        if (ownerReference != null && !isNonNamespacedResource(resource.getClass())) {
            resource.getMetadata().setOwnerReferences(List.of(ownerReference));
        }
        final R current = (R) client.resources(resource.getClass())
                .inNamespace(namespace)
                .withName(resource.getMetadata().getName())
                .get();
        final boolean isImmutableResource = isImmutableResource(resource.getClass());
        if (current == null || isImmutableResource) {
            if (current != null && isImmutableResource) {
                client
                        .resource(current)
                        .inNamespace(namespace)
                        .withPropagationPolicy(DeletionPropagation.BACKGROUND)
                        .delete();
            }
            if (isComponentEnabled()) {
                client.resource(resource)
                        .inNamespace(namespace)
                        .create();
            } else {
                log.infof("Skipping creating resource %s since component is disabled",
                        resource.getFullResourceName());
            }
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
                && global.getTls().getZookeeper().getEnabled();
    }

    protected boolean isTlsEnabledOnBookKeeper() {
        return isTlsEnabledGlobally()
                && global.getTls().getBookkeeper() != null
                && global.getTls().getBookkeeper().getEnabled();
    }

    protected boolean isTlsEnabledOnBroker() {
        return isTlsEnabledGlobally()
                && global.getTls().getBroker() != null
                && global.getTls().getBroker().getEnabled();
    }

    protected boolean isTlsEnabledOnProxy() {
        return isTlsEnabledGlobally()
                && global.getTls().getProxy() != null
                && global.getTls().getProxy().getEnabled();
    }

    protected boolean isTlsEnabledOnFunctionsWorker() {
        return isTlsEnabledGlobally()
                && global.getTls().getFunctionsWorker() != null
                && global.getTls().getFunctionsWorker().getEnabled();
    }

    protected boolean isTlsGenerateSelfSignedCertEnabled() {
        final TlsConfig tls = global.getTls();
        return tls != null
                && tls.getEnabled()
                && tls.getCertProvisioner() != null
                && tls.getCertProvisioner().getSelfSigned() != null
                && tls.getCertProvisioner().getSelfSigned().getEnabled();
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


    private String getProxyServiceUrl(boolean tls) {
        return "%s://%s-%s.%s:%d/".formatted(
                tls ? "pulsar+ssl" : "pulsar",
                global.getName(),
                global.getComponents().getProxyBaseName(),
                getServiceDnsSuffix(), tls ? 6651 : 6650);
    }

    protected String getProxyServiceUrl() {
        final boolean tls = isTlsEnabledOnProxy();
        return getProxyServiceUrl(tls);
    }

    protected String getProxyServiceUrlTls() {
        return getProxyServiceUrl(true);
    }

    protected String getProxyServiceUrlPlain() {
        return getProxyServiceUrl(false);
    }

    private String getProxyWebServiceUrl(boolean tls) {
        return "%s://%s-%s.%s:%d/".formatted(
                tls ? "https" : "http",
                global.getName(),
                global.getComponents().getProxyBaseName(),
                getServiceDnsSuffix(), tls ? 8443 : 8080);
    }

    protected String getProxyWebServiceUrl() {
        final boolean tls = isTlsEnabledOnProxy();
        return getProxyWebServiceUrl(tls);
    }

    protected String getProxyWebServiceUrlTls() {
        return getProxyWebServiceUrl(true);
    }

    protected String getProxyWebServiceUrlPlain() {
        return getProxyWebServiceUrl(false);
    }

    protected String getFunctionsWorkerServiceUrl() {
        if (isTlsEnabledOnFunctionsWorker()) {
            return "https://%s-%s-ca.%s:6751".formatted(
                    global.getName(),
                    global.getComponents().getFunctionsWorkerBaseName(),
                    getServiceDnsSuffix()
            );
        } else {
            return "http://%s-%s-ca.%s:6750".formatted(
                    global.getName(),
                    global.getComponents().getFunctionsWorkerBaseName(),
                    getServiceDnsSuffix()
            );
        }
    }

    protected String getTlsSecretNameForZookeeper() {
        final String name = global.getTls().getZookeeper() == null
                ? null : global.getTls().getZookeeper().getSecretName();
        return ObjectUtils.firstNonNull(
                name,
                global.getTls().getDefaultSecretName());
    }

    protected String getTlsSecretNameForBookkeeper() {
        final String name = global.getTls().getBookkeeper() == null
                ? null : global.getTls().getBookkeeper().getSecretName();
        return ObjectUtils.firstNonNull(
                name,
                global.getTls().getDefaultSecretName());
    }

    protected String getTlsSecretNameForBroker() {
        final String name = global.getTls().getBroker() == null
                ? null : global.getTls().getBroker().getSecretName();
        return ObjectUtils.firstNonNull(
                name,
                global.getTls().getDefaultSecretName());
    }

    protected String getTlsSecretNameForProxy() {
        final String name = global.getTls().getProxy() == null
                ? null : global.getTls().getProxy().getSecretName();
        return ObjectUtils.firstNonNull(
                name,
                global.getTls().getDefaultSecretName());
    }

    protected String getTlsSecretNameForFunctionsWorker() {
        final String name = global.getTls().getFunctionsWorker() == null
                ? null : global.getTls().getFunctionsWorker().getSecretName();
        return ObjectUtils.firstNonNull(
                name,
                global.getTls().getDefaultSecretName());
    }

    public static String getTlsSsCaSecretName(GlobalSpec global) {
        final String name = global.getTls().getSsCa() == null
                ? null : global.getTls().getSsCa().getSecretName();
        return ObjectUtils.firstNonNull(
                name,
                "%s-ss-ca".formatted(global.getName()));
    }

    protected String getTlsSsCaSecretName() {
        return getTlsSsCaSecretName(global);
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
        volumes.add(
                new VolumeBuilder()
                        .withName("certs")
                        .withNewSecret().withSecretName(secretName)
                        .endSecret()
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
        String checksum = genChecksum(configMap.getData());
        annotations.put(
                "%s/configmap-%s".formatted(CRDConstants.GROUP, configMap.getMetadata().getName()),
                checksum
        );
    }

    protected String genChecksum(Object object) {
        return DigestUtils.sha256Hex(SerializationUtil.writeAsJsonBytes(object));
    }

    protected PersistentVolumeClaim createPersistentVolumeClaim(String name,
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

    public StatefulSet getStatefulSet() {
        return client.apps().statefulSets()
                .inNamespace(namespace)
                .withName(resourceName)
                .get();
    }

    public Deployment getDeployment() {
        return client.apps().deployments()
                .inNamespace(namespace)
                .withName(resourceName)
                .get();
    }

    public Job getJob(String name) {
        return client
                .batch()
                .v1()
                .jobs()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public Job getJob() {
        return getJob(resourceName);
    }

    public boolean isJobCompleted(String name) {
        return isJobCompleted(getJob(name));
    }

    public boolean isJobCompleted() {
        return isJobCompleted(getJob(resourceName));
    }

    public static boolean isJobCompleted(Job job) {
        if (job == null) {
            return false;
        }
        final Integer succeeded = job.getStatus().getSucceeded();
        return succeeded != null && succeeded > 0;
    }

    public static boolean isStatefulSetReady(StatefulSet sts) {
        final StatefulSetStatus status = sts.getStatus();
        if (status.getReplicas() == null || status.getReadyReplicas() == null) {
            return false;
        }
        return status.getReplicas().intValue() == status.getReadyReplicas().intValue();
    }

    public static boolean isDeploymentReady(Deployment deployment) {
        final DeploymentStatus deploymentStatus = deployment.getStatus();
        if (deploymentStatus.getReplicas() == null || deploymentStatus.getReadyReplicas() == null) {
            return false;
        }
        return deploymentStatus.getReplicas().intValue() == deploymentStatus.getReadyReplicas().intValue();
    }

    protected void patchServiceAccountSingleRole(boolean namespaced, List<PolicyRule> rules,
                                                 String serviceAccountName) {
        final ServiceAccount serviceAccount = new ServiceAccountBuilder()
                .withNewMetadata()
                .withName(serviceAccountName)
                .withNamespace(namespace)
                .endMetadata()
                .build();
        patchResource(serviceAccount);
        if (namespaced) {
            final Role role = new RoleBuilder()
                    .withNewMetadata()
                    .withName(resourceName)
                    .withNamespace(namespace)
                    .endMetadata()
                    .withRules(rules)
                    .build();

            final RoleBinding roleBinding = new RoleBindingBuilder()
                    .withNewMetadata()
                    .withName(resourceName)
                    .withNamespace(namespace)
                    .endMetadata()
                    .withNewRoleRef()
                    .withKind("Role")
                    .withName(resourceName)
                    .endRoleRef()
                    .withSubjects(new SubjectBuilder()
                            .withKind("ServiceAccount")
                            .withName(serviceAccountName)
                            .withNamespace(namespace)
                            .build()
                    )
                    .build();
            patchResource(role);
            patchResource(roleBinding);
        } else {
            final ClusterRole role = new ClusterRoleBuilder()
                    .withNewMetadata()
                    .withName(resourceName)
                    .endMetadata()
                    .withRules(rules)
                    .build();
            final ClusterRoleBinding roleBinding = new ClusterRoleBindingBuilder()
                    .withNewMetadata()
                    .withName(resourceName)
                    .endMetadata()
                    .withNewRoleRef()
                    .withKind("ClusterRole")
                    .withName(resourceName)
                    .endRoleRef()
                    .withSubjects(new SubjectBuilder()
                            .withKind("ServiceAccount")
                            .withName(serviceAccountName)
                            .withNamespace(namespace)
                            .build()
                    )
                    .build();
            patchResource(role);
            patchResource(roleBinding);
        }
    }

    protected boolean isAuthTokenEnabled() {
        final AuthConfig auth = global.getAuth();
        return auth != null
                && auth.getEnabled()
                && auth.getToken() != null;
    }

    protected void addSecretTokenVolume(List<VolumeMount> volumeMounts, List<Volume> volumes, String role) {
        final String tokenName = "token-%s".formatted(role);
        volumes.add(
                new VolumeBuilder()
                        .withName(tokenName)
                        .withNewSecret().withSecretName(tokenName).endSecret()
                        .build()
        );
        volumeMounts.add(
                new VolumeMountBuilder()
                        .withName(tokenName)
                        .withMountPath("/pulsar/%s".formatted(tokenName))
                        .withReadOnly(true)
                        .build()
        );
    }

    protected <T> Map<String, T> handleConfigPulsarPrefix(Map<String, T> data) {
        if (data == null) {
            return null;
        }
        Map<String, T> newData = new HashMap<>();
        data.forEach((k, v) -> {
            final String newKey;
            // don't modify PULSAR_XX
            if (k.startsWith("PULSAR_") || k.startsWith("BOOKIE_")) {
                newKey = k;
            } else {
                newKey = "PULSAR_PREFIX_%s".formatted(k);
            }
            newData.put(newKey, v);
        });
        return newData;
    }

    protected String generateCertConverterScript() {
        String script = """
                certconverter() {
                    local name=pulsar
                    local crtFile=/pulsar/certs/tls.crt
                    local keyFile=/pulsar/certs/tls.key
                    caFile=/pulsar/certs/ca.crt
                    p12File=/pulsar/tls.p12
                    keyStoreFile=/pulsar/tls.keystore.jks
                    trustStoreFile=/pulsar/tls.truststore.jks
                                
                    head /dev/urandom | base64 | head -c 24 > /pulsar/keystoreSecret.txt
                    export tlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                    export PF_tlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                    export tlsKeyStorePassword=$(cat /pulsar/keystoreSecret.txt)
                    export PF_tlsKeyStorePassword=$(cat /pulsar/keystoreSecret.txt)
                    export PULSAR_PREFIX_brokerClientTlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                                
                    openssl pkcs12 \\
                        -export \\
                        -in ${crtFile} \\
                        -inkey ${keyFile} \\
                        -out ${p12File} \\
                        -name ${name} \\
                        -passout "file:/pulsar/keystoreSecret.txt"
                                
                    keytool -importkeystore \\
                        -srckeystore ${p12File} \\
                        -srcstoretype PKCS12 -srcstorepass:file "/pulsar/keystoreSecret.txt" \\
                        -alias ${name} \\
                        -destkeystore ${keyStoreFile} \\
                        -deststorepass:file "/pulsar/keystoreSecret.txt"
                                
                    keytool -import \\
                        -file ${caFile} \\
                        -storetype JKS \\
                        -alias ${name} \\
                        -keystore ${trustStoreFile} \\
                        -storepass:file "/pulsar/keystoreSecret.txt" \\
                        -trustcacerts -noprompt
                } &&
                certconverter &&
                """;
        if (isTlsEnabledOnZooKeeper()) {
            script += """
                    passwordArg="passwordPath=/pulsar/keystoreSecret.txt" && 
                    echo $'\\n' >> conf/pulsar_env.sh &&
                    echo "PULSAR_EXTRA_OPTS=\\"${PULSAR_EXTRA_OPTS} -Dzookeeper.clientCnxnSocket=org.apache.zookeeper.ClientCnxnSocketNetty -Dzookeeper.client.secure=true -Dzookeeper.ssl.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.keyStore.${passwordArg} -Dzookeeper.ssl.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.trustStore.${passwordArg} -Dzookeeper.sslQuorum=true -Dzookeeper.serverCnxnFactory=org.apache.zookeeper.server.NettyServerCnxnFactory -Dzookeeper.ssl.quorum.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.quorum.keyStore.${passwordArg} -Dzookeeper.ssl.quorum.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.quorum.trustStore.${passwordArg} -Dzookeeper.ssl.hostnameVerification={{ .Values.tls.zookeeper.enableHostnameVerification }} -Dzookeeper.ssl.quorum.hostnameVerification={{ .Values.tls.zookeeper.enableHostnameVerification }}\\"" >> conf/pulsar_env.sh &&
                    echo $'\\n' >> conf/bkenv.sh &&
                    echo "BOOKIE_EXTRA_OPTS=\\"${BOOKIE_EXTRA_OPTS} -Dzookeeper.clientCnxnSocket=org.apache.zookeeper.ClientCnxnSocketNetty -Dzookeeper.client.secure=true -Dzookeeper.ssl.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.keyStore.${passwordArg} -Dzookeeper.ssl.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.trustStore.${passwordArg} -Dzookeeper.ssl.hostnameVerification={{ .Values.tls.zookeeper.enableHostnameVerification }}\\"" >> conf/bkenv.sh &&
                    """;
        }
        script += "echo ''";
        return script;
    }

    protected void addAdditionalVolumes(AdditionalVolumesConfig additionalVolumesConfig,
                                        List<VolumeMount> volumeMounts, List<Volume> volumes) {
        if (additionalVolumesConfig != null) {
            if (additionalVolumesConfig.getVolumes() != null) {
                volumes.addAll(additionalVolumesConfig.getVolumes());
            }
            if (additionalVolumesConfig.getMounts() != null) {
                volumeMounts.addAll(additionalVolumesConfig.getMounts());
            }
        }
    }

    protected Affinity getAffinity(NodeAffinity nodeAffinity) {
        final AffinityBuilder builder = new AffinityBuilder()
                .withNodeAffinity(nodeAffinity);

        if (global.getAntiAffinity() != null) {
            final AntiAffinityConfig.HostAntiAffinityConfig host = global.getAntiAffinity().getHost();

            List<PodAffinityTerm> podAffinityTerms = new ArrayList<>();
            List<WeightedPodAffinityTerm> weightedPodAffinityTerms = new ArrayList<>();

            if (host != null
                    && host.getEnabled() != null
                    && host.getEnabled()) {

                final PodAffinityTerm podAffinityTerm = createPodAffinityTerm("kubernetes.io/hostname");
                if (host.getRequired() != null && host.getRequired()) {
                    podAffinityTerms.add(podAffinityTerm);
                } else {
                    weightedPodAffinityTerms.add(createWeightedPodAffinityTerm("kubernetes.io/hostname"));
                }
            }
            final AntiAffinityConfig.ZoneAntiAffinityConfig zone = global.getAntiAffinity().getZone();
            if (zone != null
                    && zone.getEnabled() != null
                    && zone.getEnabled()) {
                final WeightedPodAffinityTerm weightedPodAffinityTerm =
                        createWeightedPodAffinityTerm("failure-domain.beta.kubernetes.io/zone");
                weightedPodAffinityTerms.add(weightedPodAffinityTerm);
            }

            builder.withNewPodAntiAffinity()
                    .withPreferredDuringSchedulingIgnoredDuringExecution(weightedPodAffinityTerms)
                    .withRequiredDuringSchedulingIgnoredDuringExecution(podAffinityTerms)
                    .endPodAntiAffinity()
                    .build();

        }

        return builder.build();
    }

    private PodAffinityTerm createPodAffinityTerm(String topologyKey) {
        final PodAffinityTerm podAffinityTerm = new PodAffinityTermBuilder()
                .withNewLabelSelector()
                .withMatchLabels(getMatchLabels())
                .endLabelSelector()
                .withTopologyKey(topologyKey)
                .build();
        return podAffinityTerm;
    }

    private WeightedPodAffinityTerm createWeightedPodAffinityTerm(String topologyKey) {
        final PodAffinityTerm podAffinityTerm = createPodAffinityTerm(topologyKey);
        return new WeightedPodAffinityTermBuilder()
                .withWeight(100)
                .withPodAffinityTerm(podAffinityTerm)
                .build();
    }


}
