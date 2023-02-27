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
package com.datastax.oss.pulsaroperator.migrationtool.specs;

import com.datastax.oss.pulsaroperator.controllers.proxy.ProxyResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.configs.tls.TlsConfig;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySetSpec;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySpec;
import com.datastax.oss.pulsaroperator.migrationtool.InputClusterSpecs;
import com.datastax.oss.pulsaroperator.migrationtool.PulsarClusterResourceGenerator;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class ProxySpecGenerator extends BaseSpecGenerator<ProxySpec> {

    public static final String SPEC_NAME = "Proxy";
    private final Map<String, BaseSpecGenerator> generators = new TreeMap<>();
    private ProxySpec generatedSpec;

    public ProxySpecGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        super(inputSpecs, client);
        internalGenerateSpec(inputSpecs, client);
    }

    private void internalGenerateSpec(InputClusterSpecs inputSpecs, KubernetesClient client) {
        final List<InputClusterSpecs.ProxySpecs.AdditionalProxy> additionalProxies =
                inputSpecs.getProxy().getAdditionalProxies();
        generatedSpec = new ProxySpec();
        if (additionalProxies == null || additionalProxies.isEmpty()) {
            final ProxySetSpecGenerator proxySetSpecGenerator = new ProxySetSpecGenerator(
                    inputSpecs,
                    new InputClusterSpecs.ProxySpecs.AdditionalProxy(ProxyResourcesFactory.PROXY_DEFAULT_SET, null),
                    client
            );
            generators.put(ProxyResourcesFactory.PROXY_DEFAULT_SET, proxySetSpecGenerator);
            final ProxySpec proxySpec = proxySetSpecGenerator.generateSpec();
            generatedSpec.setSets(Map.of(ProxyResourcesFactory.PROXY_DEFAULT_SET, proxySpec));
        } else {
            Map<String, ProxySetSpec> sets = new TreeMap<>();
            additionalProxies.stream().map(
                    setConfig -> Pair.of(setConfig.getName(), new ProxySetSpecGenerator(inputSpecs, setConfig, client))
            ).forEach(pair -> {
                generators.put(pair.getLeft(), pair.getRight());
                sets.put(pair.getLeft(), pair.getRight().generateSpec());
            });
            generatedSpec.setSets(sets);
        }
    }

    @Override
    public String getSpecName() {
        return SPEC_NAME;
    }

    @Override
    public ProxySpec generateSpec() {
        return generatedSpec;
    }

    @Override
    public PodDNSConfig getPodDnsConfig() {
        return PulsarClusterResourceGenerator
                .getValueAssertSame(p -> p.getPodDnsConfig(), false, "PodDnsConfig",
                        new ArrayList<>(generators.values()));
    }

    @Override
    public boolean isRestartOnConfigMapChange() {
        return PulsarClusterResourceGenerator
                .getValueAssertSame(p -> p.isRestartOnConfigMapChange(), false, "RestartOnConfigMapChange",
                        new ArrayList<>(generators.values()));
    }

    @Override
    public String getDnsName() {
        return null;
    }

    @Override
    public String getPriorityClassName() {
        return PulsarClusterResourceGenerator.getValueAssertSame(BaseSpecGenerator::getPriorityClassName, false,
                "priorityClassName", new ArrayList<>(generators.values()));
    }

    @Override
    public Map<String, Object> getConfig() {
        return getProxySetGenerator(ProxyResourcesFactory.PROXY_DEFAULT_SET).getConfig();
    }

    @Override
    public TlsConfig.TlsEntryConfig getTlsEntryConfig() {
        final BaseSpecGenerator defaultSet = getProxySetGenerator(ProxyResourcesFactory.PROXY_DEFAULT_SET);
        if (defaultSet == null) {
            return null;
        }
        return defaultSet.getTlsEntryConfig();
    }

    public Map<String, TlsConfig.ProxyTlsEntryConfig> getTlsEntryConfigForResourceSets() {
        return generators.entrySet()
                .stream()
                .filter(entry -> !Objects.equals(entry.getKey(), ProxyResourcesFactory.PROXY_DEFAULT_SET))
                .map(p -> Pair.of(p.getKey(), p.getValue().getTlsEntryConfig()))
                .filter(p -> p.getRight() != null)
                .collect(Collectors.toMap(p -> p.getKey(), p -> (TlsConfig.ProxyTlsEntryConfig) p.getRight()));
    }

    @Override
    public String getTlsCaPath() {
        if (getProxySetGenerator(ProxyResourcesFactory.PROXY_DEFAULT_SET).getTlsCaPath() != null) {
            return Objects.requireNonNull((String) getConfig().get("tlsTrustCertsFilePath"));
        }
        return null;
    }

    @Override
    public String getAuthPublicKeyFile() {
        String tokenPublicKey = (String) getConfig().get("tokenPublicKey");
        if (tokenPublicKey == null) {
            return null;
        }
        return getPublicKeyFileFromFileURL(tokenPublicKey);
    }

    private BaseSpecGenerator getProxySetGenerator(String setName) {
        return generators.get(setName);
    }

}
