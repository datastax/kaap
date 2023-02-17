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
package com.datastax.oss.pulsaroperator.migrationtool;

import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.tls.TlsConfig;
import com.datastax.oss.pulsaroperator.migrationtool.specs.AutorecoverySpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.BaseSpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.BastionSpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.BookKeeperSpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.BrokerSpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.FunctionsWorkerSpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.ProxySpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.ZooKeeperSpecGenerator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class PulsarClusterResourceGenerator {

    private final InputClusterSpecs inputSpecs;
    private final KubernetesClient client;
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
        this.client = client;
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

    public List<HasMetadata> getAllResources() {
        return specGenerators.stream()
                .map(BaseSpecGenerator::getAllResources)
                .flatMap(List::stream)
                .toList();
    }


    private void internalGenerateSpec() {

        final GlobalSpec global = GlobalSpec.builder()
                .name(inputSpecs.getClusterName())
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
        clusterSpec.applyDefaults(global);
        generatedResource = new PulsarCluster();
        generatedResource.setMetadata(new ObjectMetaBuilder()
                .withName(inputSpecs.getClusterName())
                .build());
        generatedResource.setSpec(clusterSpec);
    }

    private TlsConfig getTlsConfig() {
        final TlsConfig.TlsEntryConfig zkTlsEntryConfig =
                specGeneratorByName(ZooKeeperSpecGenerator.SPEC_NAME).getTlsEntryConfig();
        final TlsConfig.TlsEntryConfig bkTlsEntryConfig =
                specGeneratorByName(BookKeeperSpecGenerator.SPEC_NAME).getTlsEntryConfig();
        final TlsConfig.TlsEntryConfig brokerTlsEntryConfig =
                specGeneratorByName(BrokerSpecGenerator.SPEC_NAME).getTlsEntryConfig();
        final TlsConfig.TlsEntryConfig autorecoveryTlsEntryConfig =
                specGeneratorByName(AutorecoverySpecGenerator.SPEC_NAME).getTlsEntryConfig();
        final TlsConfig.TlsEntryConfig proxyTlsEntryConfig =
                specGeneratorByName(ProxySpecGenerator.SPEC_NAME).getTlsEntryConfig();
        final TlsConfig.TlsEntryConfig functionTlsEntryConfig =
                specGeneratorByName(FunctionsWorkerSpecGenerator.SPEC_NAME).getTlsEntryConfig();


        if (zkTlsEntryConfig == null
                && bkTlsEntryConfig == null
                && brokerTlsEntryConfig == null
                && autorecoveryTlsEntryConfig == null
                && proxyTlsEntryConfig == null
                && functionTlsEntryConfig == null) {
            return TlsConfig.builder()
                    .enabled(false)
                    .build();
        }


        final TlsConfig.TlsEntryConfig ssCaEntryConfig =
                specGeneratorByName(BastionSpecGenerator.SPEC_NAME).getTlsEntryConfig();
        final String caPath = (String) getValueAssertSame((Function<BaseSpecGenerator, Object>) baseSpecGenerator -> {
            if (baseSpecGenerator.getSpecName().equals(ZooKeeperSpecGenerator.SPEC_NAME)) {
                return null;
            } else if (baseSpecGenerator.getSpecName().equals(BookKeeperSpecGenerator.SPEC_NAME)) {
                if (bkTlsEntryConfig == null) {
                    return null;
                }
                return Objects.requireNonNull(baseSpecGenerator.getConfig().get("bookkeeperTLSTrustCertsFilePath"));
            } else if (baseSpecGenerator.getSpecName().equals(BrokerSpecGenerator.SPEC_NAME)) {
                if (brokerTlsEntryConfig == null) {
                    return null;
                }
                return Objects.requireNonNull(baseSpecGenerator.getConfig().get("tlsTrustCertsFilePath"));
            } else if (baseSpecGenerator.getSpecName().equals(AutorecoverySpecGenerator.SPEC_NAME)) {
                if (autorecoveryTlsEntryConfig == null) {
                    return null;
                }
                return Objects.requireNonNull(baseSpecGenerator.getConfig().get("tlsTrustStore"));
            } else if (baseSpecGenerator.getSpecName().equals(ProxySpecGenerator.SPEC_NAME)) {
                if (proxyTlsEntryConfig == null) {
                    return null;
                }
                return Objects.requireNonNull(baseSpecGenerator.getConfig().get("tlsTrustCertsFilePath"));
            } else if (baseSpecGenerator.getSpecName().equals(FunctionsWorkerSpecGenerator.SPEC_NAME)) {
                if (functionTlsEntryConfig == null) {
                    return null;
                }
                return Objects.requireNonNull(baseSpecGenerator.getConfig().get("tlsTrustCertsFilePath"));
            } else if (baseSpecGenerator.getSpecName().equals(BastionSpecGenerator.SPEC_NAME)) {
                if (ssCaEntryConfig == null) {
                    return null;
                }
                return Objects.requireNonNull(baseSpecGenerator.getConfig().get("tlsTrustCertsFilePath"));
            }
            return null;
        }, true, "caPath");

        return TlsConfig.builder()
                .enabled(true)
                .zookeeper(zkTlsEntryConfig)
                .bookkeeper(bkTlsEntryConfig)
                .broker(brokerTlsEntryConfig)
                .autorecovery(autorecoveryTlsEntryConfig)
                .proxy((TlsConfig.ProxyTlsEntryConfig) proxyTlsEntryConfig)
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
                        .contains(spec.getSpecName());

        final boolean authEnabled = getValueAssertSame(
                filterComponents,
                spec -> parseConfigValueBool(spec.getConfig(), "authenticationEnabled"),
                false,
                "authenticationEnabled");
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
        return authConfigBuilder
                .token(tokenAuthBuilder.build())
                .build();
    }

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
        if (filteredGenerators.isEmpty()) {
            throw new IllegalStateException("No spec generators found for " + configName + " after filtering");
        }
        T firstValue = skipNulls ? null : mapper.apply(filteredGenerators.get(0));
        String firstValueSpecName = skipNulls ? null : filteredGenerators.get(0).getSpecName();
        for (BaseSpecGenerator specGenerator : filteredGenerators) {
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
