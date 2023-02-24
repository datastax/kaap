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
package com.datastax.oss.pulsaroperator.controllers.broker;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.ConfigUtil;
import com.datastax.oss.pulsaroperator.crds.SpecDiffer;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerFullSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-broker-controller")
@JBossLog
public class BrokerController extends AbstractController<Broker> {

    public static List<String> enumerateBrokerSets(BrokerSpec broker) {
        Map<String, BrokerSetSpec> sets = broker.getSets();
        if (sets == null || sets.isEmpty()) {
            return List.of(BrokerResourcesFactory.BROKER_DEFAULT_SET);
        } else {
            final TreeMap<String, BrokerSetSpec> sorted = new TreeMap<>(sets);
            return new ArrayList<>(sorted.keySet());
        }
    }

    @Getter
    @AllArgsConstructor
    private static class BrokerSetInfo {
        private final String name;
        private final BrokerSetSpec setSpec;
        private final BrokerResourcesFactory brokerResourcesFactory;
        private final boolean needDedicatedService;


    }

    public BrokerController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(Broker resource, Context<Broker> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final BrokerFullSpec spec = resource.getSpec();

        List<BrokerSetInfo> brokerSets = getBrokerSets(resource, namespace, spec);

        if (!areSpecChanged(resource)) {
            ReconciliationResult lastResult = null;
            for (BrokerSetInfo brokerSetInfo : brokerSets) {
                final ReconciliationResult result = checkReady(resource, brokerSetInfo);
                if (result.isReschedule()) {
                    return result;
                }
                lastResult = result;
            }
            return lastResult;
        } else {
            final BrokerResourcesFactory
                    defaultResourceFactory = new BrokerResourcesFactory(
                    client, namespace, BrokerResourcesFactory.BROKER_DEFAULT_SET, spec.getBroker(),
                    spec.getGlobal(), getOwnerReference(resource));
            // always create default service
            defaultResourceFactory.patchService();

            for (BrokerSetInfo brokerSet : brokerSets) {
                patchAll(brokerSet);
            }
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
    }

    private List<BrokerSetInfo> getBrokerSets(Broker resource, String namespace, BrokerFullSpec spec) {
        List<BrokerSetInfo> result = new ArrayList<>();
        final OwnerReference ownerReference = getOwnerReference(resource);
        final BrokerSpec mainBrokerSpec = spec.getBroker();

        for (Map.Entry<String, BrokerSetSpec> brokerSet : getBrokerSetSpecs(spec).entrySet()) {
            final String brokerSetName = brokerSet.getKey();
            final BrokerSetSpec brokerSetSpec = brokerSet.getValue();
            final BrokerResourcesFactory
                    resourcesFactory = new BrokerResourcesFactory(
                    client, namespace, brokerSetName, brokerSetSpec,
                    spec.getGlobal(), ownerReference);

            boolean needDedicatedService =
                    !SpecDiffer.specsAreEquals(brokerSetSpec.getService(), mainBrokerSpec.getService());
            result.add(new BrokerSetInfo(brokerSetName, brokerSetSpec, resourcesFactory, needDedicatedService));
        }
        return result;
    }

    public static TreeMap<String, BrokerSetSpec> getBrokerSetSpecs(BrokerFullSpec fullSpec) {
        return getBrokerSetSpecs(fullSpec.getBroker());
    }

    public static TreeMap<String, BrokerSetSpec> getBrokerSetSpecs(BrokerSpec broker) {
        Map<String, BrokerSetSpec> sets = broker.getSets();
        if (sets == null || sets.isEmpty()) {
            sets = Map.of(BrokerResourcesFactory.BROKER_DEFAULT_SET,
                    ConfigUtil.applyDefaultsWithReflection(new BrokerSetSpec(),
                            () -> broker));
        } else {
            sets = new HashMap<>(sets);
            for (Map.Entry<String, BrokerSetSpec> set : sets.entrySet()) {
                sets.put(set.getKey(),
                        ConfigUtil.applyDefaultsWithReflection(set.getValue(), () -> broker)
                );
            }
        }
        return new TreeMap<>(sets);
    }

    private void patchAll(BrokerSetInfo brokerSetInfo) {
        final BrokerResourcesFactory resourcesFactory = brokerSetInfo.getBrokerResourcesFactory();
        resourcesFactory.patchPodDisruptionBudget();
        resourcesFactory.patchConfigMap();
        resourcesFactory.patchStatefulSet();
        if (brokerSetInfo.isNeedDedicatedService()) {
            resourcesFactory.patchService();
        }
    }

    private ReconciliationResult checkReady(Broker resource,
                                            BrokerSetInfo brokerSetInfo) {
        final BrokerResourcesFactory resourcesFactory = brokerSetInfo.getBrokerResourcesFactory();
        if (!resourcesFactory.isComponentEnabled()) {
            return new ReconciliationResult(
                    false,
                    List.of(createReadyConditionDisabled(resource))
            );
        }
        final StatefulSet sts = resourcesFactory.getStatefulSet();
        if (sts == null) {
            patchAll(brokerSetInfo);
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
        if (BaseResourcesFactory.isStatefulSetReady(sts)) {
            resourcesFactory.createTransactionsInitJobIfNeeded();

            return new ReconciliationResult(
                    false,
                    List.of(createReadyCondition(resource))
            );
        } else {
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
    }
}
