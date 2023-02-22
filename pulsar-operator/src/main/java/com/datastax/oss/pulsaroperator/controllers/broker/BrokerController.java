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
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerFullSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerWithSetsSpec;
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
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-broker-controller")
@JBossLog
public class BrokerController extends AbstractController<Broker> {

    public BrokerController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(Broker resource, Context<Broker> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final BrokerFullSpec spec = resource.getSpec();

        List<BrokerResourcesFactory> factories = new ArrayList<>();
        TreeMap<String, BrokerSpec> sets = getBrokerSets(spec);
        final OwnerReference ownerReference = getOwnerReference(resource);
        for (Map.Entry<String, BrokerSpec> stringBrokerSpecEntry : sets.entrySet()) {
            final BrokerResourcesFactory
                    resourcesFactory = new BrokerResourcesFactory(
                    client, namespace, stringBrokerSpecEntry.getKey(), stringBrokerSpecEntry.getValue(),
                    spec.getGlobal(), ownerReference);
            factories.add(resourcesFactory);
        }

        if (!areSpecChanged(resource)) {
            ReconciliationResult lastResult = null;
            for (BrokerResourcesFactory factory : factories) {
                final ReconciliationResult result = checkReady(resource, factory);
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
                    spec.getGlobal(), ownerReference);
            defaultResourceFactory.patchService();

            for (BrokerResourcesFactory factory : factories) {
                patchAll(factory);
            }
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
    }

    private TreeMap<String, BrokerSpec> getBrokerSets(BrokerFullSpec fullSpec) {
        final BrokerWithSetsSpec broker = fullSpec.getBroker();
        Map<String, BrokerSpec> sets = broker.getSets();
        if (sets == null || sets.isEmpty()) {
            sets = Map.of(BrokerResourcesFactory.BROKER_DEFAULT_SET,
                    ConfigUtil.applyDefaultsWithReflection(new BrokerSpec(),
                            () -> broker));
        } else {
            sets = new HashMap<>(sets);
            for (Map.Entry<String, BrokerSpec> set : sets.entrySet()) {
                sets.put(set.getKey(),
                        ConfigUtil.applyDefaultsWithReflection(set.getValue(), () -> broker)
                );
            }
        }
        return new TreeMap<>(sets);
    }

    private void patchAll(BrokerResourcesFactory resourcesFactory) {
        resourcesFactory.patchPodDisruptionBudget();
        resourcesFactory.patchConfigMap();
        resourcesFactory.patchStatefulSet();
    }

    private ReconciliationResult checkReady(Broker resource,
                                            BrokerResourcesFactory resourcesFactory) {
        if (!resourcesFactory.isComponentEnabled()) {
            return new ReconciliationResult(
                    false,
                    List.of(createReadyConditionDisabled(resource))
            );
        }
        final StatefulSet sts = resourcesFactory.getStatefulSet();
        if (sts == null) {
            patchAll(resourcesFactory);
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
