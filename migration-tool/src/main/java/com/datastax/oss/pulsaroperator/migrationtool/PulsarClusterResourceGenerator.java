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
import com.datastax.oss.pulsaroperator.crds.configs.AntiAffinityConfig;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.migrationtool.specs.BaseSpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.BookKeeperSpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.BrokerSpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.ZooKeeperSpecGenerator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
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
    private final List<BaseSpecGenerator> specGenerators;


    public PulsarClusterResourceGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        this.inputSpecs = inputSpecs;
        this.client = client;
        zooKeeperSpecGenerator = new ZooKeeperSpecGenerator(inputSpecs, client);
        bookKeeperSpecGenerator = new BookKeeperSpecGenerator(inputSpecs, client);
        brokerSpecGenerator = new BrokerSpecGenerator(inputSpecs, client);
        specGenerators = List.of(
                zooKeeperSpecGenerator,
                bookKeeperSpecGenerator,
                brokerSpecGenerator
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
                .antiAffinity(getAntiAffinityConfig())
                .dnsName(getDnsName())
                .restartOnConfigMapChange(isRestartOnConfigMapChange())
                .priorityClassName(getPriorityClassName())
                .auth(getAuthConfig())
                .build();

        final PulsarClusterSpec clusterSpec = PulsarClusterSpec.builder()
                .global(global)
                .zookeeper(zooKeeperSpecGenerator.generateSpec())
                .bookkeeper(bookKeeperSpecGenerator.generateSpec())
                .broker(brokerSpecGenerator.generateSpec())
                .build();
        clusterSpec.applyDefaults(global);
        generatedResource = new PulsarCluster();
        generatedResource.setMetadata(new ObjectMetaBuilder()
                .withName(inputSpecs.getClusterName())
                .build());
        generatedResource.setSpec(clusterSpec);
    }

    private AuthConfig getAuthConfig() {
        final Predicate<BaseSpecGenerator> filterComponents =
                spec -> !List.of(BookKeeperSpecGenerator.SPEC_NAME, ZooKeeperSpecGenerator.SPEC_NAME)
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
        final String authPlugin =
                (String) getValueAssertSame(
                        filterComponents,
                        spec -> spec.getConfig().get("authPlugin"), false, "authPlugin");
        if (!"org.apache.pulsar.client.impl.auth.AuthenticationToken".equals(authPlugin)) {
            log.info("Found unexpected authPlugin {} (expected org.apache.pulsar.client.impl.auth.AuthenticationToken), "
                    + "auth will be disabled in the CRD", authPlugin);
            return AuthConfig.builder()
                    .enabled(false)
                    .token(AuthConfig.TokenAuthenticationConfig.builder()
                            .initialize(false)
                            .build())
                    .build();
        }
        final AuthConfig.AuthConfigBuilder authConfigBuilder = AuthConfig.builder()
                .enabled(true);

        final AuthConfig.TokenAuthenticationConfig.TokenAuthenticationConfigBuilder tokenAuthBuilder =
                AuthConfig.TokenAuthenticationConfig.builder()
                        .initialize(false);
        final String superUserRoles =
                (String) getValueAssertSame(
                        filterComponents,
                        spec -> spec.getConfig().get("superUserRoles"),
                        false, "superUserRoles");
        if (StringUtils.isBlank(superUserRoles)) {
            throw new IllegalArgumentException("superUserRoles must be set if authentication is enabled");
        }
        tokenAuthBuilder.superUserRoles(new TreeSet<>(List.of(superUserRoles.split(","))));

        final String proxyRoles =
                (String) getValueAssertSame(
                        filterComponents,
                        spec -> spec.getConfig().get("proxyRoles"),
                        false, "proxyRoles");
        if (StringUtils.isBlank(proxyRoles)) {
            tokenAuthBuilder.proxyRoles(null);
        } else {
            tokenAuthBuilder.proxyRoles(new TreeSet<>(List.of(proxyRoles.split(","))));
        }
        return authConfigBuilder
                .token(tokenAuthBuilder.build())
                .build();
    }

    private boolean parseConfigValueBool(Map<String, Object> config, String key) {
        return Boolean.parseBoolean(String.valueOf(config.get(key)));
    }

    private GlobalSpec.Components getComponentsConfig() {
        return GlobalSpec.Components.builder()
                .zookeeperBaseName(inputSpecs.getZookeeper().getBaseName())
                .bookkeeperBaseName(inputSpecs.getBookkeeper().getBaseName())
                .brokerBaseName(inputSpecs.getBroker().getBaseName())
                .build();
    }

    private PodDNSConfig getPodDNSConfig() {
        return getValueAssertSame(BaseSpecGenerator::getPodDnsConfig, false, "PodDNSConfig");
    }

    private AntiAffinityConfig getAntiAffinityConfig() {
        return getValueAssertSame(BaseSpecGenerator::getAntiAffinityConfig, false, "AntiAffinityConfig");
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

}
