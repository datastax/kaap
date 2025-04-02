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
package com.datastax.oss.kaap.controllers.broker;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.common.json.JSONComparator;
import com.datastax.oss.kaap.controllers.AbstractResourceSetsController;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.SpecDiffer;
import com.datastax.oss.kaap.crds.broker.Broker;
import com.datastax.oss.kaap.crds.broker.BrokerFullSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSetSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSpec;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(name = "pulsar-broker-controller")
@JBossLog
public class BrokerController extends
        AbstractResourceSetsController<Broker, BrokerFullSpec, BrokerSpec, BrokerSetSpec,
                BrokerResourcesFactory,
                BrokerController.BrokerSetsLastApplied> {

    public static List<String> enumerateBrokerSets(String clusterName, String componentBaseName, BrokerSpec broker) {
        LinkedHashMap<String, BrokerSetSpec> sets = broker.getSets();
        if (sets == null || sets.isEmpty()) {
            return List.of(BrokerResourcesFactory.getResourceName(clusterName, componentBaseName,
                    BrokerResourcesFactory.BROKER_DEFAULT_SET, broker.getOverrideResourceName()));
        } else {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, BrokerSetSpec> set : sets.entrySet()) {
                names.add(BrokerResourcesFactory.getResourceName(clusterName, componentBaseName,
                        set.getKey(), set.getValue().getOverrideResourceName()));
            }
            return names;
        }
    }


    public static LinkedHashMap<String, BrokerSetSpec> getBrokerSetSpecs(BrokerFullSpec spec) {
        return getBrokerSetSpecs(spec.getBroker());
    }

    public static LinkedHashMap<String, BrokerSetSpec> getBrokerSetSpecs(BrokerSpec spec) {
        return new BrokerController(null).getSetSpecs(spec);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BrokerSetsLastApplied
            implements AbstractResourceSetsController.SetsLastApplied<BrokerFullSpec> {
        private BrokerFullSpec common;
        private Map<String, BrokerFullSpec> sets = new HashMap<>();
    }


    public BrokerController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected String getComponentNameForLogs() {
        return "broker";
    }

    @Override
    protected void patchResourceSet(SetInfo<BrokerSetSpec, BrokerResourcesFactory> set) {
        final BrokerResourcesFactory resourcesFactory = set.getResourceFactory();
        resourcesFactory.patchPodDisruptionBudget();
        resourcesFactory.patchConfigMap();
        resourcesFactory.patchStatefulSet();
        resourcesFactory.patchService();

    }

    @Override
    protected void deleteResourceSet(SetInfo<BrokerSetSpec, BrokerResourcesFactory> set, Broker resource) {
        final BrokerResourcesFactory resourcesFactory = set.getResourceFactory();
        if (!set.getName().equals(BrokerResourcesFactory.BROKER_DEFAULT_SET)) {
            resourcesFactory.deleteService();
        }
        resourcesFactory.deleteStatefulSet();
        resourcesFactory.deleteConfigMap();
        resourcesFactory.deletePodDisruptionBudget();
    }

    @Override
    protected void patchCommonResources(SetInfo<BrokerSetSpec, BrokerResourcesFactory> set) {
        set.getResourceFactory().patchService();
    }

    @Override
    protected ReconciliationResult checkReady(Broker resource, SetInfo<BrokerSetSpec, BrokerResourcesFactory> set) {
        final BrokerResourcesFactory resourcesFactory = set.getResourceFactory();
        if (!resourcesFactory.isComponentEnabled()) {
            return new ReconciliationResult(
                    false,
                    List.of(createReadyConditionDisabled(resource))
            );
        }
        final StatefulSet sts = resourcesFactory.getStatefulSet();
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

    @Override
    protected String getDefaultSetName() {
        return BrokerResourcesFactory.BROKER_DEFAULT_SET;
    }

    @Override
    protected BrokerSpec getSpec(BrokerFullSpec fullSpec) {
        return fullSpec.getBroker();
    }

    @Override
    protected Map<String, BrokerSetSpec> getSets(BrokerSpec brokerSpec) {
        return brokerSpec.getSets();
    }

    @Override
    protected BrokerResourcesFactory newFactory(OwnerReference ownerReference, String namespace, String setName,
                                                BrokerSetSpec setSpec, GlobalSpec globalSpec) {
        return new BrokerResourcesFactory(client, namespace, setName, setSpec, globalSpec, ownerReference);
    }

    @Override
    protected JSONComparator.Result compareLastAppliedSetSpec(Broker resource,
                                                              SetInfo<BrokerSetSpec, BrokerResourcesFactory> setInfo,
                                                              BrokerFullSpec spec, BrokerFullSpec lastApplied) {
        if (spec.getBroker().getSets() != null) {
            spec = SerializationUtil.deepCloneObject(spec);
            spec.getBroker().getSets().entrySet()
                    .removeIf(e -> !e.getKey().equals(setInfo.getName()));
        }

        if (lastApplied != null && lastApplied.getBroker().getSets() != null) {
            lastApplied = SerializationUtil.deepCloneObject(lastApplied);
            lastApplied.getBroker().getSets().entrySet()
                    .removeIf(e -> !e.getKey().equals(setInfo.getName()));
        }
        return SpecDiffer.generateDiff(lastApplied, spec);
    }

    @Override
    protected BrokerSetsLastApplied readSetsLastApplied(Broker resource) {
        final BrokerSetsLastApplied
                setsLastApplied = getLastAppliedResource(resource, BrokerSetsLastApplied.class);
        if (setsLastApplied == null) {
            return new BrokerSetsLastApplied();
        }
        return setsLastApplied;
    }

    @Override
    protected boolean isRollingUpdate(BrokerFullSpec fullSpec) {
        return BrokerSpec.BrokerSetsUpdateStrategy.RollingUpdate.toString()
                .equals(fullSpec.getBroker().getSetsUpdateStrategy());
    }
}
