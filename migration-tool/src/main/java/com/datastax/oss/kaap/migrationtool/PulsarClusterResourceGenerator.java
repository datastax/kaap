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
package com.datastax.oss.kaap.migrationtool;

import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarCluster;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.crds.configs.AuthConfig;
import com.datastax.oss.kaap.crds.configs.ResourceSetConfig;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.migrationtool.specs.AutorecoverySpecGenerator;
import com.datastax.oss.kaap.migrationtool.specs.BaseSpecGenerator;
import com.datastax.oss.kaap.migrationtool.specs.BastionSpecGenerator;
import com.datastax.oss.kaap.migrationtool.specs.BookKeeperSpecGenerator;
import com.datastax.oss.kaap.migrationtool.specs.BrokerSpecGenerator;
import com.datastax.oss.kaap.migrationtool.specs.FunctionsWorkerSpecGenerator;
import com.datastax.oss.kaap.migrationtool.specs.ProxySpecGenerator;
import com.datastax.oss.kaap.migrationtool.specs.ZooKeeperSpecGenerator;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class PulsarClusterResourceGenerator {

    private final InputClusterSpecs inputSpecs;
    private PulsarCluster generatedResource;

    private final ZooKeeperSpecGenerator zooKeeperSpecGenerator;
    private final BookKeeperSpecGenerator bookKeeperSpecGenerator;
    private final BrokerSpecGenerator brokerSpecGenerator;
    private final AutorecoverySpecGenerator autorecoverySpecGenerator;
    private final BastionSpecGenerator bastionSpecGenerator;
    private final ProxySpecGenerator proxySpecGenerator;
    private final FunctionsWorkerSpecGenerator functionsWorkerSpecGenerator;
    private final List<BaseSpecGenerator> specGenerators;


    public PulsarClusterResourceGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        this.inputSpecs = inputSpecs;
        zooKeeperSpecGenerator = new ZooKeeperSpecGenerator(inputSpecs, client);
        bookKeeperSpecGenerator = new BookKeeperSpecGenerator(inputSpecs, client);
        brokerSpecGenerator = new BrokerSpecGenerator(inputSpecs, client);
        autorecoverySpecGenerator = new AutorecoverySpecGenerator(inputSpecs, client);
        bastionSpecGenerator = new BastionSpecGenerator(inputSpecs, client);
        proxySpecGenerator = new ProxySpecGenerator(inputSpecs, client);
        functionsWorkerSpecGenerator = new FunctionsWorkerSpecGenerator(inputSpecs, client);
        specGenerators = List.of(
                zooKeeperSpecGenerator,
                bookKeeperSpecGenerator,
                brokerSpecGenerator,
                autorecoverySpecGenerator,
                bastionSpecGenerator,
                proxySpecGenerator,
                functionsWorkerSpecGenerator
        );
        internalGenerateSpec();
    }

    public PulsarCluster generatePulsarClusterCustomResource() {
        return generatedResource;
    }

    private void internalGenerateSpec() {

        final GlobalSpec global = GlobalSpec.builder()
                .name(inputSpecs.getClusterName())
                .clusterName(inputSpecs.getClusterName())
                .components(getComponentsConfig())
                .dnsConfig(getPodDNSConfig())
                .dnsName(getDnsName())
                .restartOnConfigMapChange(isRestartOnConfigMapChange())
                .priorityClassName(getPriorityClassName())
                .auth(getAuthConfig())
                .tls(getTlsConfig())
                .build();

        final PulsarClusterSpec clusterSpec = PulsarClusterSpec.builder()
                .global(global)
                .zookeeper(zooKeeperSpecGenerator.generateSpec())
                .bookkeeper(bookKeeperSpecGenerator.generateSpec())
                .broker(brokerSpecGenerator.generateSpec())
                .autorecovery(autorecoverySpecGenerator.generateSpec())
                .bastion(bastionSpecGenerator.generateSpec())
                .proxy(proxySpecGenerator.generateSpec())
                .functionsWorker(functionsWorkerSpecGenerator.generateSpec())
                .build();

        detectAndSetResourceSets(clusterSpec);
        clusterSpec.applyDefaults(global);
        generatedResource = new PulsarCluster();
        generatedResource.setMetadata(new ObjectMetaBuilder()
                .withName(inputSpecs.getClusterName())
                .build());
        generatedResource.setSpec(clusterSpec);
    }

    private void detectAndSetResourceSets(PulsarClusterSpec clusterSpec) {
        Map<String, ResourceSetConfig> resourceSets = new HashMap<>();
        if (clusterSpec.getBookkeeper() != null && clusterSpec.getBookkeeper().getSets() != null) {
            clusterSpec.getBookkeeper().getSets().keySet().forEach(k -> {
                resourceSets.put(k, null);
            });
        }
        if (clusterSpec.getBroker() != null && clusterSpec.getBroker().getSets() != null) {
            clusterSpec.getBroker().getSets().keySet().forEach(k -> {
                resourceSets.put(k, null);
            });
        }
        if (clusterSpec.getProxy() != null && clusterSpec.getProxy().getSets() != null) {
            clusterSpec.getProxy().getSets().keySet().forEach(k -> {
                resourceSets.put(k, null);
            });
        }
        if (!resourceSets.isEmpty()) {
            clusterSpec.getGlobal().setResourceSets(resourceSets);
        }
    }

    private TlsConfig getTlsConfig() {
        final TlsConfig.TlsEntryConfig zkTlsEntryConfig =
                specGeneratorByName(ZooKeeperSpecGenerator.SPEC_NAME).getTlsEntryConfig();
        final TlsConfig.TlsEntryConfig bkTlsEntryConfig =
                specGeneratorByName(BookKeeperSpecGenerator.SPEC_NAME).getTlsEntryConfig();
        final BaseSpecGenerator brokerSpecGenerator = specGeneratorByName(BrokerSpecGenerator.SPEC_NAME);
        final TlsConfig.TlsEntryConfig brokerTlsEntryConfig =
                brokerSpecGenerator.getTlsEntryConfig();

        final Map<String, TlsConfig.TlsEntryConfig> tlsEntryConfigForBrokerResourceSets =
                ((BrokerSpecGenerator) brokerSpecGenerator).getTlsEntryConfigForResourceSets();


        final TlsConfig.TlsEntryConfig autorecoveryTlsEntryConfig =
                specGeneratorByName(AutorecoverySpecGenerator.SPEC_NAME).getTlsEntryConfig();
        final BaseSpecGenerator proxySpecGenerator = specGeneratorByName(ProxySpecGenerator.SPEC_NAME);
        final TlsConfig.TlsEntryConfig proxyTlsEntryConfig =
                proxySpecGenerator.getTlsEntryConfig();
        final Map<String, TlsConfig.ProxyTlsEntryConfig> tlsEntryConfigForProxyResourceSets =
                ((ProxySpecGenerator) proxySpecGenerator).getTlsEntryConfigForResourceSets();

        final TlsConfig.TlsEntryConfig functionTlsEntryConfig =
                specGeneratorByName(FunctionsWorkerSpecGenerator.SPEC_NAME).getTlsEntryConfig();


        if (zkTlsEntryConfig == null
                && bkTlsEntryConfig == null
                && brokerTlsEntryConfig == null
                && tlsEntryConfigForBrokerResourceSets == null
                && autorecoveryTlsEntryConfig == null
                && proxyTlsEntryConfig == null
                && tlsEntryConfigForProxyResourceSets == null
                && functionTlsEntryConfig == null) {
            return TlsConfig.builder()
                    .build();
        }


        final TlsConfig.TlsEntryConfig ssCaEntryConfig =
                specGeneratorByName(BastionSpecGenerator.SPEC_NAME).getTlsEntryConfig();
        final String caPath = getValueAssertSame(spec -> spec.getTlsCaPath(), true, "caPath");

        return TlsConfig.builder()
                .enabled(true)
                .zookeeper(zkTlsEntryConfig)
                .bookkeeper(bkTlsEntryConfig)
                .broker(brokerTlsEntryConfig)
                .brokerResourceSets(tlsEntryConfigForBrokerResourceSets)
                .autorecovery(autorecoveryTlsEntryConfig)
                .proxy((TlsConfig.ProxyTlsEntryConfig) proxyTlsEntryConfig)
                .proxyResourceSets(tlsEntryConfigForProxyResourceSets)
                .functionsWorker((TlsConfig.FunctionsWorkerTlsEntryConfig) functionTlsEntryConfig)
                .ssCa(ssCaEntryConfig)
                .caPath(caPath)
                .build();
    }

    private AuthConfig getAuthConfig() {
        final Predicate<BaseSpecGenerator> filterComponents =
                spec -> !List.of(
                                BookKeeperSpecGenerator.SPEC_NAME,
                                ZooKeeperSpecGenerator.SPEC_NAME,
                                AutorecoverySpecGenerator.SPEC_NAME,
                                BastionSpecGenerator.SPEC_NAME
                        )
                        .contains(spec.getSpecName()) && spec.isEnabled();

        final boolean authEnabled = BooleanUtils.isTrue(getValueAssertSame(
                filterComponents,
                spec -> parseConfigValueBool(spec.getConfig(), "authenticationEnabled"),
                false,
                "authenticationEnabled"));
        if (!authEnabled) {
            log.info("Found authenticationEnabled=false, auth will be disabled in the CRD");
            return AuthConfig.builder()
                    .enabled(false)
                    .token(AuthConfig.TokenAuthenticationConfig.builder()
                            .initialize(false)
                            .build())
                    .build();
        }

        specGenerators.stream()
                .filter(filterComponents)
                .forEach(spec -> {
                    checkAuthenticationProvidersContainTokenAuth(spec.getConfig(), spec.getSpecName());
                });

        final AuthConfig.AuthConfigBuilder authConfigBuilder = AuthConfig.builder()
                .enabled(true);

        final AuthConfig.TokenAuthenticationConfig.TokenAuthenticationConfigBuilder tokenAuthBuilder =
                AuthConfig.TokenAuthenticationConfig.builder()
                        .initialize(false);
        final List<String> superUserRoles =
                getValueAssertSame(
                        filterComponents,
                        spec -> {
                            final List<String> parsed = parseConfigList(spec.getConfig(), "superUserRoles");
                            Collections.sort(parsed);
                            return parsed;
                        },
                        false, "superUserRoles");
        if (superUserRoles.isEmpty()) {
            throw new IllegalArgumentException("superUserRoles must be set if authentication is enabled");
        }
        tokenAuthBuilder.superUserRoles(new TreeSet<>(superUserRoles));

        final String proxyRoles =
                (String) specGeneratorByName(BrokerSpecGenerator.SPEC_NAME).getConfig().get("proxyRoles");
        if (StringUtils.isBlank(proxyRoles)) {
            tokenAuthBuilder.proxyRoles(null);
        } else {
            tokenAuthBuilder.proxyRoles(new TreeSet<>(List.of(proxyRoles.split(","))));
        }

        final String publicKeyFile = getValueAssertSame(spec -> spec.getAuthPublicKeyFile(), true,
                "publicKeyFile");
        final AuthConfig.TokenAuthenticationConfig tokenAuthenticationConfig = tokenAuthBuilder
                .publicKeyFile(publicKeyFile)
                .build();
        return authConfigBuilder
                .token(tokenAuthenticationConfig)
                .build();
    }

    @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
    private Boolean parseConfigValueBool(Map<String, Object> config, String key) {
        final Object val = config.get(key);
        if (val == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(val));
    }

    private GlobalSpec.Components getComponentsConfig() {
        return GlobalSpec.Components.builder()
                .zookeeperBaseName(inputSpecs.getZookeeper().getBaseName())
                .bookkeeperBaseName(inputSpecs.getBookkeeper().getBaseName())
                .brokerBaseName(inputSpecs.getBroker().getBaseName())
                .autorecoveryBaseName(inputSpecs.getAutorecovery().getBaseName())
                .bastionBaseName(inputSpecs.getBastion().getBaseName())
                .proxyBaseName(inputSpecs.getProxy().getBaseName())
                .functionsWorkerBaseName(inputSpecs.getFunctionsWorker().getBaseName())
                .build();
    }

    private PodDNSConfig getPodDNSConfig() {
        return getValueAssertSame(BaseSpecGenerator::getPodDnsConfig, false, "PodDNSConfig");
    }

    private String getDnsName() {
        return getValueAssertSame(BaseSpecGenerator::getDnsName, true, "dnsName");
    }

    private boolean isRestartOnConfigMapChange() {
        return getValueAssertSame(BaseSpecGenerator::isRestartOnConfigMapChange, false,
                "restartOnConfigMapChange");
    }

    private String getPriorityClassName() {
        return getValueAssertSame(BaseSpecGenerator::getPriorityClassName, false,
                "priorityClassName");
    }

    private <T> T getValueAssertSame(Function<BaseSpecGenerator, T> mapper, boolean skipNulls, String configName) {
        return getValueAssertSame(s -> true, mapper, skipNulls, configName);
    }

    private <T> T getValueAssertSame(Predicate<BaseSpecGenerator> filter,
                                     Function<BaseSpecGenerator, T> mapper,
                                     boolean skipNulls,
                                     String configName) {

        final List<BaseSpecGenerator> filteredGenerators = specGenerators.stream()
                .filter(filter)
                .collect(Collectors.toList());
        return getValueAssertSame(mapper, skipNulls, configName, filteredGenerators);
    }

    public static <T> T getValueAssertSame(Function<BaseSpecGenerator, T> mapper, boolean skipNulls, String configName,
                                           List<BaseSpecGenerator> filteredGenerators) {
        if (filteredGenerators.isEmpty()) {
            throw new IllegalStateException("No spec generators found for " + configName + " after filtering");
        }
        T firstValue = skipNulls ? null : mapper.apply(filteredGenerators.get(0));
        String firstValueSpecName = skipNulls ? null : filteredGenerators.get(0).getSpecName();
        for (BaseSpecGenerator specGenerator : filteredGenerators) {
            if (!specGenerator.isEnabled()) {
                continue;
            }
            final T specGenValue = mapper.apply(specGenerator);
            if (skipNulls) {
                if (specGenValue == null) {
                    continue;
                }
                if (firstValue == null) {
                    firstValue = specGenValue;
                    firstValueSpecName = specGenerator.getSpecName();
                    continue;
                }
            }
            if (!Objects.equals(specGenValue, firstValue)) {
                throw new IllegalStateException(
                        """
                                Detected two different '%s' setting (%s has '%s' and '%s' has '%s') for different components.
                                It's required to be the same for all components. %s
                                """.formatted(configName, firstValueSpecName, firstValue, specGenerator.getSpecName(),
                                specGenValue,
                                (skipNulls ? "(or empty)" : "")));
            }
        }
        return firstValue;
    }

    private BaseSpecGenerator specGeneratorByName(String name) {
        for (BaseSpecGenerator specGenerator : specGenerators) {
            if (specGenerator.getSpecName().equals(name)) {
                return specGenerator;
            }
        }
        return null;
    }

    public static void checkAuthenticationProvidersContainTokenAuth(Map<String, ?> config, String specName) {
        final List<String> parsed = parseConfigList(config, "authenticationProviders");
        if (parsed == null) {
            throw new IllegalArgumentException("authenticationProviders not set in " + specName);
        }
        if (!parsed.contains("org.apache.pulsar.broker.authentication."
                + "AuthenticationProviderToken")) {
            throw new IllegalArgumentException(
                    "authenticationProviders does not include Token auth in " + specName);
        }

    }

    private static List<String> parseConfigList(Map<String, ?> config, String key) {
        final Object val = config.get(key);
        if (val == null) {
            return new ArrayList<>();
        }
        if (val instanceof List) {
            return new ArrayList<>((List<String>) val);
        }
        if (val instanceof String) {
            return new ArrayList<>(List.of(((String) val).split(",")));
        }
        throw new IllegalArgumentException("Invalid value for " + key + ": " + val);
    }

}
