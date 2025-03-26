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
package com.datastax.oss.kaap.controllers.proxy;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.common.json.JSONComparator;
import com.datastax.oss.kaap.controllers.AbstractResourceSetsController;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.SpecDiffer;
import com.datastax.oss.kaap.crds.proxy.Proxy;
import com.datastax.oss.kaap.crds.proxy.ProxyFullSpec;
import com.datastax.oss.kaap.crds.proxy.ProxySetSpec;
import com.datastax.oss.kaap.crds.proxy.ProxySpec;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.Deployment;
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


@ControllerConfiguration(name = "pulsar-proxy-controller")
@JBossLog
public class ProxyController
        extends AbstractResourceSetsController<Proxy, ProxyFullSpec, ProxySpec, ProxySetSpec, ProxyResourcesFactory,
        ProxyController.ProxySetsLastApplied> {

    public static List<String> enumerateProxySets(String clusterName, String componentBaseName, ProxySpec proxy) {
        LinkedHashMap<String, ProxySetSpec> sets = proxy.getSets();
        if (sets == null || sets.isEmpty()) {
            return List.of(ProxyResourcesFactory.getResourceName(clusterName, componentBaseName,
                    ProxyResourcesFactory.PROXY_DEFAULT_SET, proxy.getOverrideResourceName()));
        } else {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, ProxySetSpec> set : sets.entrySet()) {
                names.add(ProxyResourcesFactory.getResourceName(clusterName, componentBaseName,
                        set.getKey(), set.getValue()));
            }
            return names;
        }
    }

    public static LinkedHashMap<String, ProxySetSpec> getProxySetSpecs(ProxySpec proxy) {
        return new ProxyController(null).getSetSpecs(proxy);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProxySetsLastApplied implements SetsLastApplied<ProxyFullSpec> {
        private ProxyFullSpec common;
        private Map<String, ProxyFullSpec> sets = new HashMap<>();
    }

    @Override
    protected boolean isRollingUpdate(ProxyFullSpec proxyFullSpec) {
        return ProxySpec.ProxySetsUpdateStrategy.RollingUpdate.toString()
                .equals(proxyFullSpec.getProxy().getSetsUpdateStrategy());
    }

    public ProxyController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected String getComponentNameForLogs() {
        return "proxy";
    }

    @Override
    protected String getDefaultSetName() {
        return ProxyResourcesFactory.PROXY_DEFAULT_SET;
    }

    @Override
    protected ProxySpec getSpec(ProxyFullSpec proxyFullSpec) {
        return proxyFullSpec.getProxy();
    }

    @Override
    protected void patchResourceSet(SetInfo<ProxySetSpec, ProxyResourcesFactory> set) {
        final ProxyResourcesFactory resourceFactory = set.getResourceFactory();
        resourceFactory.patchPodDisruptionBudget();
        resourceFactory.patchConfigMap();
        resourceFactory.patchConfigMapWsConfig();
        resourceFactory.patchService();
        resourceFactory.patchDeployment();
    }

    @Override
    protected void deleteResourceSet(SetInfo<ProxySetSpec, ProxyResourcesFactory> set, Proxy resource) {
        final ProxyResourcesFactory resourcesFactory = set.getResourceFactory();
        resourcesFactory.deletePodDisruptionBudget();
        resourcesFactory.deleteConfigMap();
        if (!set.getName().equals(ProxyResourcesFactory.PROXY_DEFAULT_SET)) {
            resourcesFactory.deleteService();
        }
        resourcesFactory.deleteDeployment();
    }

    @Override
    protected void patchCommonResources(SetInfo<ProxySetSpec, ProxyResourcesFactory> set) {
        set.getResourceFactory().patchService();
    }

    @Override
    protected ReconciliationResult checkReady(Proxy resource, SetInfo<ProxySetSpec, ProxyResourcesFactory> set) {
        final ProxyResourcesFactory resourcesFactory = set.getResourceFactory();
        if (!resourcesFactory.isComponentEnabled()) {
            return new ReconciliationResult(
                    false,
                    List.of(createReadyConditionDisabled(resource))
            );
        }
        final Deployment deployment = resourcesFactory.getDeployment();
        if (BaseResourcesFactory.isDeploymentReady(deployment, client)) {
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
    protected Map<String, ProxySetSpec> getSets(ProxySpec proxySpec) {
        return proxySpec.getSets();
    }

    @Override
    protected ProxyResourcesFactory newFactory(OwnerReference ownerReference, String namespace, String setName,
                                               ProxySetSpec setSpec, GlobalSpec globalSpec) {
        return new ProxyResourcesFactory(
                client, namespace, setName, setSpec,
                globalSpec, ownerReference);
    }

    @Override
    protected ProxySetsLastApplied readSetsLastApplied(Proxy resource) {
        final ProxySetsLastApplied proxySetsLastApplied = getLastAppliedResource(resource, ProxySetsLastApplied.class);
        if (proxySetsLastApplied == null) {
            return new ProxySetsLastApplied();
        }
        return proxySetsLastApplied;
    }

    @Override
    protected JSONComparator.Result compareLastAppliedSetSpec(Proxy proxy,
                                                              SetInfo<ProxySetSpec, ProxyResourcesFactory> info,
                                                              ProxyFullSpec spec, ProxyFullSpec lastApplied) {
        if (spec.getProxy().getSets() != null) {
            spec = SerializationUtil.deepCloneObject(spec);
            spec.getProxy().getSets().entrySet()
                    .removeIf(e -> !e.getKey().equals(info.getName()));
        }

        if (lastApplied != null && lastApplied.getProxy().getSets() != null) {
            lastApplied = SerializationUtil.deepCloneObject(lastApplied);
            lastApplied.getProxy().getSets().entrySet()
                    .removeIf(e -> !e.getKey().equals(info.getName()));
        }
        return SpecDiffer.generateDiff(lastApplied, spec);
    }
}
