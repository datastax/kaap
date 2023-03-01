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
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-broker-controller")
@JBossLog
public class BrokerController extends AbstractController<Broker> {

    public static List<String> enumerateBrokerSets(String clusterName, String componentBaseName, BrokerSpec broker) {
        Map<String, BrokerSetSpec> sets = broker.getSets();
        if (sets == null || sets.isEmpty()) {
            return List.of(BrokerResourcesFactory.getResourceName(clusterName, componentBaseName,
                    BrokerResourcesFactory.BROKER_DEFAULT_SET, broker.getOverrideResourceName()));
        } else {
            final TreeMap<String, BrokerSetSpec> sorted = new TreeMap<>(sets);
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, BrokerSetSpec> set : sorted.entrySet()) {
                names.add(BrokerResourcesFactory.getResourceName(clusterName, componentBaseName,
                        set.getKey(), set.getValue().getOverrideResourceName()));
            }
            return names;
        }
    }

    @Getter
    @AllArgsConstructor
    private static class BrokerSetInfo {
        private final String name;
        private final BrokerSetSpec setSpec;
        private final BrokerResourcesFactory brokerResourcesFactory;
    }

    public BrokerController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(Broker resource, Context<Broker> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final BrokerFullSpec spec = resource.getSpec();

        final OwnerReference ownerReference = getOwnerReference(resource);
        List<BrokerSetInfo> brokerSets = getBrokerSets(ownerReference, namespace, spec);

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
                    spec.getGlobal(), ownerReference);
            // always create default service
            defaultResourceFactory.patchService();

            for (BrokerSetInfo brokerSet : brokerSets) {
                patchAll(brokerSet);
            }
            cleanupDeletedBrokerSets(resource, namespace, brokerSets);
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
    }

    private void cleanupDeletedBrokerSets(Broker resource, String namespace, List<BrokerSetInfo> brokerSets) {
        final BrokerFullSpec lastAppliedResource = getLastAppliedResource(resource, BrokerFullSpec.class);
        if (lastAppliedResource != null) {
            final Set<String> currentBrokerSets = brokerSets.stream().map(BrokerSetInfo::getName)
                    .collect(Collectors.toSet());
            currentBrokerSets.add(BrokerResourcesFactory.BROKER_DEFAULT_SET);
            final List<BrokerSetInfo> toDeleteBrokerSets =
                    getBrokerSets(null, namespace, lastAppliedResource, currentBrokerSets);
            for (BrokerSetInfo brokerSet : toDeleteBrokerSets) {
                deleteAll(brokerSet);
                log.infof("Deleted broker set: %s", brokerSet.getName());
            }
        }
    }

    private List<BrokerSetInfo> getBrokerSets(OwnerReference ownerReference, String namespace, BrokerFullSpec spec) {
        return getBrokerSets(ownerReference, namespace, spec, Set.of());
    }


    private List<BrokerSetInfo> getBrokerSets(OwnerReference ownerReference, String namespace, BrokerFullSpec spec,
                                              Set<String> excludes) {
        List<BrokerSetInfo> result = new ArrayList<>();

        for (Map.Entry<String, BrokerSetSpec> brokerSet : getBrokerSetSpecs(spec).entrySet()) {
            final String brokerSetName = brokerSet.getKey();
            if (excludes.contains(brokerSetName)) {
                continue;
            }
            final BrokerSetSpec brokerSetSpec = brokerSet.getValue();
            final BrokerResourcesFactory
                    resourcesFactory = new BrokerResourcesFactory(
                    client, namespace, brokerSetName, brokerSetSpec,
                    spec.getGlobal(), ownerReference);
            result.add(new BrokerSetInfo(brokerSetName, brokerSetSpec, resourcesFactory));
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
        resourcesFactory.patchService();
    }

    private void deleteAll(BrokerSetInfo brokerSetInfo) {
        final BrokerResourcesFactory resourcesFactory = brokerSetInfo.getBrokerResourcesFactory();
        resourcesFactory.deletePodDisruptionBudget();
        resourcesFactory.deleteConfigMap();
        resourcesFactory.deleteStatefulSet();
        resourcesFactory.deleteService();
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
