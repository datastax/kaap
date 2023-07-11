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
package com.datastax.oss.kaap.crds;

import com.datastax.oss.kaap.crds.configs.AntiAffinityConfig;
import com.datastax.oss.kaap.crds.configs.AuthConfig;
import com.datastax.oss.kaap.crds.configs.RackConfig;
import com.datastax.oss.kaap.crds.configs.ResourceSetConfig;
import com.datastax.oss.kaap.crds.configs.StorageClassConfig;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodDNSConfigBuilder;
import io.fabric8.kubernetes.api.model.PodDNSConfigOptionBuilder;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalSpec extends ValidableSpec<GlobalSpec> implements WithDefaults {


    public static final String DEFAULT_TLS_SECRET_NAME = "pulsar-tls";

    private static final Supplier<TlsConfig> DEFAULT_TLS_CONFIG = () -> TlsConfig.builder()
            .enabled(false)
            .caPath("/etc/ssl/certs/ca-certificates.crt")
            .defaultSecretName(DEFAULT_TLS_SECRET_NAME)
            .zookeeper(TlsConfig.TlsEntryConfig.builder()
                    .enabled(false)
                    .build())
            .bookkeeper(TlsConfig.TlsEntryConfig.builder()
                    .enabled(false)
                    .build())
            .autorecovery(TlsConfig.TlsEntryConfig.builder()
                    .enabled(false)
                    .build())
            .broker(TlsConfig.TlsEntryConfig.builder()
                    .enabled(false)
                    .build())
            .proxy(TlsConfig.ProxyTlsEntryConfig.proxyBuilder()
                    .enabled(false)
                    .enabledWithBroker(false)
                    .build())
            .ssCa(TlsConfig.TlsEntryConfig.builder()
                    .enabled(false)
                    .build())
            .build();

    private static final Supplier<AuthConfig> DEFAULT_AUTH_CONFIG = () -> AuthConfig.builder()
            .enabled(false)
            .token(AuthConfig.TokenAuthenticationConfig.builder()
                    .publicKeyFile("my-public.key")
                    .privateKeyFile("my-private.key")
                    .superUserRoles(new TreeSet<>(Set.of("superuser", "admin", "websocket", "proxy")))
                    .proxyRoles(new TreeSet<>(Set.of("proxy")))
                    .initialize(true)
                    .build())
            .build();

    private static final Supplier<PodDNSConfig> DEFAULT_DNS_CONFIG = () -> new PodDNSConfigBuilder()
            .withOptions(new PodDNSConfigOptionBuilder()
                    .withName("ndots")
                    .withValue("4")
                    .build())
            .build();

    private static final Supplier<AntiAffinityConfig> DEFAULT_ANTI_AFFINITY_CONFIG = () -> AntiAffinityConfig.builder()
            .host(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                    .enabled(true)
                    .required(true)
                    .build())
            .zone(AntiAffinityConfig.AntiAffinityTypeConfig.builder()
                    .enabled(false)
                    .build())
            .build();

    private static final Supplier<RackConfig> DEFAULT_RACK_CONFIG = () -> RackConfig.builder()
            .host(RackConfig.HostRackTypeConfig.builder()
                    .enabled(false)
                    .requireRackAffinity(false)
                    .requireRackAntiAffinity(true)
                    .build())
            .zone(RackConfig.ZoneRackTypeConfig.builder()
                    .enabled(false)
                    .requireRackAffinity(false)
                    .requireRackAntiAffinity(true)
                    .enableHostAntiAffinity(true)
                    .requireRackHostAntiAffinity(true)
                    .build())
            .build();


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Components {
        @JsonPropertyDescription("Zookeeper base name. Default value is 'zookeeper'.")
        private String zookeeperBaseName;
        @JsonPropertyDescription("BookKeeper base name. Default value is 'bookkeeper'.")
        private String bookkeeperBaseName;
        @JsonPropertyDescription("Broker base name. Default value is 'broker'.")
        private String brokerBaseName;
        @JsonPropertyDescription("Proxy base name. Default value is 'proxy'.")
        private String proxyBaseName;
        @JsonPropertyDescription("Autorecovery base name. Default value is 'autorecovery'.")
        private String autorecoveryBaseName;
        @JsonPropertyDescription("Bastion base name. Default value is 'bastion'.")
        private String bastionBaseName;
        @JsonPropertyDescription("Functions Worker base name. Default value is 'function'.")
        private String functionsWorkerBaseName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GlobalStorageConfig {
        @JsonPropertyDescription("Indicates if a StorageClass is used. The operator will create the StorageClass if "
                + "needed.")
        private StorageClassConfig storageClass;
        @JsonPropertyDescription("Indicates if an already existing storage class should be used.")
        private String existingStorageClassName;
    }


    @NotNull
    @Required
    @JsonPropertyDescription("Pulsar cluster name.")
    private String name;
    @JsonPropertyDescription("Pulsar cluster components names.")
    private Components components;
    @JsonPropertyDescription("DNS config for each pod.")
    private PodDNSConfig dnsConfig;
    @JsonPropertyDescription("""
            The domain name for your kubernetes cluster.
            This domain is documented here: https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#a-aaaa-records-1 .
            It's used to fully qualify service names when configuring Pulsar.
            The default value is 'cluster.local'.
            """)
    private String kubernetesClusterDomain;
    @JsonPropertyDescription("Public dns name for the cluster's load balancer.")
    private String dnsName;
    @JsonPropertyDescription("Global node selector. If set, this will apply to all the components.")
    protected Map<String, String> nodeSelectors;
    @JsonPropertyDescription("TLS configuration for the cluster.")
    private TlsConfig tls;
    @JsonPropertyDescription("""
            If persistence is enabled, components that has state will be deployed with PersistentVolumeClaims, otherwise, for test purposes, they will be deployed with emptyDir
            """)
    private Boolean persistence;
    @JsonPropertyDescription("""
            By default, Kubernetes will not restart pods when only their configmap is changed. This setting will restart pods when their configmap is changed using an annotation that calculates the checksum of the configmap.
            """)
    private Boolean restartOnConfigMapChange;

    @JsonPropertyDescription("""
            Authentication and authorization configuration.
            """)
    private AuthConfig auth;

    // overridable parameters
    @JsonPropertyDescription("Default Pulsar image to use. Any components can be configured to use a different image.")
    private String image;
    @JsonPropertyDescription("Default Pulsar image pull policy to use. Any components can be configured to use a "
            + "different image pull policy. Default value is 'IfNotPresent'.")
    private String imagePullPolicy;
    @JsonPropertyDescription("Storage configuration.")
    private GlobalStorageConfig storage;

    @JsonPropertyDescription("Pod anti affinity configuration.")
    private AntiAffinityConfig antiAffinity;
    @JsonPropertyDescription("Priority class name to attach to each pod.")
    private String priorityClassName;
    @JsonPropertyDescription("Resource sets.")
    private Map<String, ResourceSetConfig> resourceSets;
    @JsonPropertyDescription("Racks configuration.")
    private Map<String, RackConfig> racks;
    @JsonPropertyDescription("Use plain password in zookeeper server and client configuration. Default is false. Old "
            + "versions of Apache Zookeeper (<3.8.0) does not support getting password from file. In that case, set "
            + "this to true.")
    private Boolean zookeeperPlainSslStorePassword;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        applyComponentsDefaults();

        if (kubernetesClusterDomain == null) {
            kubernetesClusterDomain = "cluster.local";
        }
        if (imagePullPolicy == null) {
            imagePullPolicy = "IfNotPresent";
        }
        if (persistence == null) {
            persistence = true;
        }
        if (restartOnConfigMapChange == null) {
            restartOnConfigMapChange = false;
        }
        if (storage == null) {
            storage = new GlobalStorageConfig();
        }
        if (storage.getStorageClass() == null && storage.getExistingStorageClassName() == null) {
            storage.setExistingStorageClassName("default");
        }
        if (storage.getStorageClass() != null && storage.getStorageClass().getReclaimPolicy() == null) {
            storage.getStorageClass().setReclaimPolicy("Retain");
        }
        if (dnsConfig == null) {
            dnsConfig = DEFAULT_DNS_CONFIG.get();
        }
        applyTlsDefaults();
        applyAuthDefaults();
        applyAntiAffinityDefaults();
        applyRacksDefaults();
        if (zookeeperPlainSslStorePassword == null) {
            zookeeperPlainSslStorePassword = false;
        }
    }

    private void applyTlsDefaults() {
        if (tls == null) {
            tls = DEFAULT_TLS_CONFIG.get();
        } else {
            tls = ConfigUtil.applyDefaultsWithReflection(
                    tls, DEFAULT_TLS_CONFIG
            );
        }
    }

    private void applyComponentsDefaults() {
        if (components == null) {
            components = new Components();
        }
        components.setZookeeperBaseName(ObjectUtils.firstNonNull(components.getZookeeperBaseName(), "zookeeper"));
        components.setBookkeeperBaseName(ObjectUtils.firstNonNull(components.getBookkeeperBaseName(), "bookkeeper"));
        components.setBrokerBaseName(ObjectUtils.firstNonNull(components.getBrokerBaseName(), "broker"));
        components.setProxyBaseName(ObjectUtils.firstNonNull(components.getProxyBaseName(), "proxy"));
        components.setAutorecoveryBaseName(
                ObjectUtils.firstNonNull(components.getAutorecoveryBaseName(), "autorecovery"));
        components.setBastionBaseName(ObjectUtils.firstNonNull(components.getBastionBaseName(), "bastion"));
        components.setFunctionsWorkerBaseName(
                ObjectUtils.firstNonNull(components.getFunctionsWorkerBaseName(), "function"));
    }

    private void applyAuthDefaults() {
        if (auth == null) {
            auth = DEFAULT_AUTH_CONFIG.get();
        } else {
            auth = ConfigUtil.applyDefaultsWithReflection(auth, DEFAULT_AUTH_CONFIG);
        }
    }

    private void applyAntiAffinityDefaults() {
        if (antiAffinity == null) {
            antiAffinity = DEFAULT_ANTI_AFFINITY_CONFIG.get();
        } else {
            antiAffinity = ConfigUtil.applyDefaultsWithReflection(antiAffinity, DEFAULT_ANTI_AFFINITY_CONFIG);
        }
    }

    private void applyRacksDefaults() {
        if (racks != null) {
            racks = racks.entrySet().stream()
                    .map(entry ->
                            Map.entry(
                                    entry.getKey(),
                                    ConfigUtil.applyDefaultsWithReflection(entry.getValue(), DEFAULT_RACK_CONFIG)
                            ))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    @Override
    public boolean isValid(GlobalSpec value, ConstraintValidatorContext context) {
        return true;
    }
}
