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
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-proxy-controller")
@JBossLog
public class ProxyController extends AbstractController<Proxy> {

    public static List<String> enumerateProxySets(String clusterName, String componentBaseName, ProxySpec proxy) {
        Map<String, ProxySetSpec> sets = proxy.getSets();
        if (sets == null || sets.isEmpty()) {
            return List.of(ProxyResourcesFactory.getResourceName(clusterName, componentBaseName,
                    ProxyResourcesFactory.PROXY_DEFAULT_SET, proxy.getOverrideResourceName()));
        } else {
            final TreeMap<String, ProxySetSpec> sorted = new TreeMap<>(sets);
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, ProxySetSpec> set : sorted.entrySet()) {
                names.add(ProxyResourcesFactory.getResourceName(clusterName, componentBaseName,
                        set.getKey(), set.getValue()));
            }
            return names;
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

        final OwnerReference ownerReference = getOwnerReference(resource);
        List<ProxySetInfo> proxySets = getProxySets(ownerReference, namespace, spec);

        if (!areSpecChanged(resource)) {
            ReconciliationResult lastResult = null;
            for (ProxySetInfo proxySetInfo : proxySets) {
                final ReconciliationResult result = checkReady(resource, proxySetInfo);
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
                    spec.getGlobal(), ownerReference);
            // always create default service
            defaultResourceFactory.patchService();

            for (ProxySetInfo proxySet : proxySets) {
                patchAll(proxySet.getProxyResourcesFactory());
            }
            cleanupDeletedProxySets(resource, namespace, proxySets);
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


    private void deleteAll(ProxyResourcesFactory controller) {
        controller.deletePodDisruptionBudget();
        controller.deleteConfigMap();
        controller.deleteService();
        controller.deleteDeployment();
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

    private void cleanupDeletedProxySets(Proxy resource, String namespace, List<ProxySetInfo> proxySets) {
        final ProxyFullSpec lastAppliedResource = getLastAppliedResource(resource, ProxyFullSpec.class);
        if (lastAppliedResource != null) {
            final Set<String> currentProxySets = proxySets.stream().map(ProxySetInfo::getName)
                    .collect(Collectors.toSet());
            final List<ProxySetInfo> toDeleteProxySets =
                    getProxySets(null, namespace, lastAppliedResource, currentProxySets);
            for (ProxySetInfo proxySet : toDeleteProxySets) {
                deleteAll(proxySet.getProxyResourcesFactory());
                log.infof("Deleted proxy set: %s", proxySet.getName());
            }
        }
    }


    private List<ProxySetInfo> getProxySets(OwnerReference ownerReference, String namespace, ProxyFullSpec spec) {
        return getProxySets(ownerReference, namespace, spec, Set.of());
    }

    private List<ProxySetInfo> getProxySets(OwnerReference ownerReference, String namespace, ProxyFullSpec spec,
                                            Set<String> excludes) {
        List<ProxySetInfo> result = new ArrayList<>();

        for (Map.Entry<String, ProxySetSpec> proxySpec : getProxySetSpecs(spec).entrySet()) {
            final String proxySetName = proxySpec.getKey();
            if (excludes.contains(proxySetName)) {
                continue;
            }
            final ProxySetSpec proxySetSpec = proxySpec.getValue();
            final ProxyResourcesFactory
                    resourcesFactory = new ProxyResourcesFactory(
                    client, namespace, proxySetName, proxySetSpec,
                    spec.getGlobal(), ownerReference);
            result.add(new ProxySetInfo(proxySetName, proxySetSpec, resourcesFactory));
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
