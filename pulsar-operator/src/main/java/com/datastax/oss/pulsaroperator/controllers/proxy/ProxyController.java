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
package com.datastax.oss.pulsaroperator.controllers.proxy;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.ConfigUtil;
import com.datastax.oss.pulsaroperator.crds.proxy.Proxy;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxyFullSpec;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySetSpec;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySpec;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.Deployment;
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


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-proxy-controller")
@JBossLog
public class ProxyController extends AbstractController<Proxy> {

    public static List<String> enumerateProxySets(ProxySpec proxy) {
        Map<String, ProxySetSpec> sets = proxy.getSets();
        if (sets == null || sets.isEmpty()) {
            return List.of(ProxyResourcesFactory.PROXY_DEFAULT_SET);
        } else {
            final TreeMap<String, ProxySetSpec> sorted = new TreeMap<>(sets);
            return new ArrayList<>(sorted.keySet());
        }
    }

    @Getter
    @AllArgsConstructor
    private static class ProxySetInfo {
        private final String name;
        private final ProxySetSpec setSpec;
        private final ProxyResourcesFactory proxyResourcesFactory;
    }


    public ProxyController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(Proxy resource, Context<Proxy> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final ProxyFullSpec spec = resource.getSpec();

        List<ProxySetInfo> proxySets = getProxySets(resource, namespace, spec);

        if (!areSpecChanged(resource)) {
            ReconciliationResult lastResult = null;
            for (ProxySetInfo brokerSetInfo : proxySets) {
                final ReconciliationResult result = checkReady(resource, brokerSetInfo);
                if (result.isReschedule()) {
                    return result;
                }
                lastResult = result;
            }
            return lastResult;
        } else {
            final ProxyResourcesFactory
                    defaultResourceFactory = new ProxyResourcesFactory(
                    client, namespace, ProxyResourcesFactory.PROXY_DEFAULT_SET, spec.getProxy(),
                    spec.getGlobal(), getOwnerReference(resource));
            // always create default service
            defaultResourceFactory.patchService();

            for (ProxySetInfo proxySet : proxySets) {
                patchAll(proxySet.getProxyResourcesFactory());
            }
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
    }

    private void patchAll(ProxyResourcesFactory controller) {
        controller.patchPodDisruptionBudget();
        controller.patchConfigMap();
        controller.patchConfigMapWsConfig();
        controller.patchService();
        controller.patchDeployment();
    }


    private ReconciliationResult checkReady(Proxy resource,
                                            ProxySetInfo proxySetInfo) {
        final ProxyResourcesFactory resourcesFactory = proxySetInfo.getProxyResourcesFactory();
        if (!resourcesFactory.isComponentEnabled()) {
            return new ReconciliationResult(
                    false,
                    List.of(createReadyConditionDisabled(resource))
            );
        }
        final Deployment deployment = resourcesFactory.getDeployment();
        if (deployment == null) {
            patchAll(resourcesFactory);
            return new ReconciliationResult(true,
                    List.of(createNotReadyInitializingCondition(resource)));
        } else {
            if (BaseResourcesFactory.isDeploymentReady(deployment)) {
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



    private List<ProxySetInfo> getProxySets(Proxy resource, String namespace, ProxyFullSpec spec) {
        List<ProxySetInfo> result = new ArrayList<>();
        final OwnerReference ownerReference = getOwnerReference(resource);

        for (Map.Entry<String, ProxySetSpec> proxySpec : getProxySetSpecs(spec).entrySet()) {
            final String proxySetName = proxySpec.getKey();
            final ProxySetSpec brokerSetSpec = proxySpec.getValue();
            final ProxyResourcesFactory
                    resourcesFactory = new ProxyResourcesFactory(
                    client, namespace, proxySetName, brokerSetSpec,
                    spec.getGlobal(), ownerReference);
            result.add(new ProxySetInfo(proxySetName, brokerSetSpec, resourcesFactory));
        }
        return result;
    }

    public static TreeMap<String, ProxySetSpec> getProxySetSpecs(ProxyFullSpec fullSpec) {
        return getProxySetSpecs(fullSpec.getProxy());
    }

    public static TreeMap<String, ProxySetSpec> getProxySetSpecs(ProxySpec proxy) {
        Map<String, ProxySetSpec> sets = proxy.getSets();
        if (sets == null || sets.isEmpty()) {
            sets = Map.of(ProxyResourcesFactory.PROXY_DEFAULT_SET,
                    ConfigUtil.applyDefaultsWithReflection(new ProxySetSpec(),
                            () -> proxy));
        } else {
            sets = new HashMap<>(sets);
            for (Map.Entry<String, ProxySetSpec> set : sets.entrySet()) {
                sets.put(set.getKey(),
                        ConfigUtil.applyDefaultsWithReflection(set.getValue(), () -> proxy)
                );
            }
        }
        return new TreeMap<>(sets);
    }


}
