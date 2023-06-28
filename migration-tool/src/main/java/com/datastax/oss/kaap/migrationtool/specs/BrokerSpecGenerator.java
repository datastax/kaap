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
package com.datastax.oss.kaap.migrationtool.specs;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.kaap.crds.broker.BrokerSetSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSpec;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.migrationtool.InputClusterSpecs;
import com.datastax.oss.kaap.migrationtool.PulsarClusterResourceGenerator;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class BrokerSpecGenerator extends BaseSpecGenerator<BrokerSpec> {

    public static final String SPEC_NAME = "Broker";
    private final Map<String, BaseSpecGenerator> generators = new TreeMap<>();
    private BrokerSpec generatedSpec;

    public BrokerSpecGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        super(inputSpecs, client);
        internalGenerateSpec(inputSpecs, client);
    }

    private void internalGenerateSpec(InputClusterSpecs inputSpecs, KubernetesClient client) {
        final List<InputClusterSpecs.BrokerSpecs.BrokerSetSpecs> brokerSets =
                inputSpecs.getBroker().getBrokerSets();
        if (brokerSets == null || brokerSets.isEmpty()) {
            final BrokerSetSpecGenerator brokerSetSpecGenerator = new BrokerSetSpecGenerator(
                    inputSpecs,
                    new InputClusterSpecs.BrokerSpecs.BrokerSetSpecs(BrokerResourcesFactory.BROKER_DEFAULT_SET, null),
                    client
            );
            generators.put(BrokerResourcesFactory.BROKER_DEFAULT_SET, brokerSetSpecGenerator);
            generatedSpec = SerializationUtil.convertValue(brokerSetSpecGenerator.generateSpec(), BrokerSpec.class);
        } else {
            LinkedHashMap<String, BrokerSetSpec> sets = new LinkedHashMap<>();
            brokerSets.stream().map(
                    setConfig -> Pair.of(setConfig.getName(), new BrokerSetSpecGenerator(inputSpecs, setConfig, client))
            ).forEach(pair -> {
                generators.put(pair.getLeft(), pair.getRight());
                sets.put(pair.getLeft(), pair.getRight().generateSpec());
            });
            generatedSpec = new BrokerSpec();
            generatedSpec.setSets(sets);
        }
    }

    @Override
    public String getSpecName() {
        return SPEC_NAME;
    }

    @Override
    public BrokerSpec generateSpec() {
        return generatedSpec;
    }

    @Override
    public boolean isEnabled() {
        return generators.values().stream().filter(gen -> gen.isEnabled()).findAny().isPresent();
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
        return getBrokerSpecGenerator(BrokerResourcesFactory.BROKER_DEFAULT_SET).getConfig();
    }

    @Override
    public TlsConfig.TlsEntryConfig getTlsEntryConfig() {
        final BaseSpecGenerator defaultSet = getBrokerSpecGenerator(BrokerResourcesFactory.BROKER_DEFAULT_SET);
        if (defaultSet == null) {
            return null;
        }
        return defaultSet.getTlsEntryConfig();
    }

    public Map<String, TlsConfig.TlsEntryConfig> getTlsEntryConfigForResourceSets() {
        return generators.entrySet()
                .stream()
                .filter(entry -> !Objects.equals(entry.getKey(), BrokerResourcesFactory.BROKER_DEFAULT_SET))
                .map(p -> Pair.of(p.getKey(), p.getValue().getTlsEntryConfig()))
                .filter(p -> p.getRight() != null)
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getRight()));
    }

    @Override
    public String getTlsCaPath() {
        return PulsarClusterResourceGenerator
                .getValueAssertSame(p -> p.getTlsCaPath(), false, "tlsTrustCertsFilePath",
                        new ArrayList<>(generators.values()));
    }

    @Override
    public String getAuthPublicKeyFile() {
        String tokenPublicKey = (String) getConfig().get("tokenPublicKey");
        if (tokenPublicKey == null) {
            return null;
        }
        return getPublicKeyFileFromFileURL(tokenPublicKey);
    }

    private BaseSpecGenerator getBrokerSpecGenerator(String setName) {
        return generators.get(setName);
    }

}
