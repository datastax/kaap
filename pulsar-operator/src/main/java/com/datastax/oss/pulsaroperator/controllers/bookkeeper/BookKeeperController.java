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
package com.datastax.oss.pulsaroperator.controllers.bookkeeper;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.ConfigUtil;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-bk-controller")
@JBossLog
public class BookKeeperController extends AbstractController<BookKeeper> {

    public static List<String> enumerateBookKeeperSets(String clusterName, String componentBaseName,
                                                       BookKeeperSpec bk) {
        LinkedHashMap<String, BookKeeperSetSpec> sets = bk.getSets();
        if (sets == null || sets.isEmpty()) {
            return List.of(BookKeeperResourcesFactory.getResourceName(clusterName, componentBaseName,
                    BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET, bk.getOverrideResourceName()));
        } else {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, BookKeeperSetSpec> set : sets.entrySet()) {
                names.add(BookKeeperResourcesFactory.getResourceName(clusterName, componentBaseName,
                        set.getKey(), set.getValue().getOverrideResourceName()));
            }
            return names;
        }
    }


    @Getter
    @AllArgsConstructor
    private static class BookKeeperSetInfo {
        private final String name;
        private final BookKeeperSetSpec setSpec;
        private final BookKeeperResourcesFactory bookKeeperResourcesFactory;
    }


    public BookKeeperController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(BookKeeper resource, Context<BookKeeper> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final BookKeeperFullSpec spec = resource.getSpec();

        final OwnerReference ownerReference = getOwnerReference(resource);
        List<BookKeeperSetInfo> bkSets = getBookKeeperSets(ownerReference, namespace, spec);

        if (!areSpecChanged(resource)) {
            ReconciliationResult lastResult = null;
            for (BookKeeperSetInfo bkSetInfo : bkSets) {
                final ReconciliationResult result = checkReady(resource, bkSetInfo);
                if (result.isReschedule()) {
                    log.infof("Bookkeeper set '%s' is not ready, rescheduling", bkSetInfo.getName());
                    return result;
                }
                lastResult = result;
            }
            return lastResult;
        } else {
            final BookKeeperResourcesFactory
                    defaultResourceFactory = new BookKeeperResourcesFactory(
                    client, namespace, BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET, spec.getBookkeeper(),
                    spec.getGlobal(), ownerReference);
            // always create default service
            defaultResourceFactory.patchService();

            final boolean isRollingUpdate = isRollingUpdate(spec);
            for (BookKeeperSetInfo bkSet : bkSets) {
                patchAll(bkSet);
                if (isRollingUpdate && checkReady(resource, bkSet).isReschedule()) {
                    log.infof("Bookkeeper set '%s' is not ready, rescheduling", bkSet.getName());
                    return new ReconciliationResult(
                            true,
                            List.of(createNotReadyInitializingCondition(resource)),
                            true
                    );
                } else {
                    log.infof("Bookkeeper set '%s' is ready", bkSet.getName());
                }
            }
            cleanupDeletedBookkeeperSets(resource, namespace, bkSets);
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
    }

    private boolean isRollingUpdate(BookKeeperFullSpec spec) {
        return BookKeeperSpec.BookKeeperSetsUpdateStrategy.RollingUpdate.toString()
                .equals(spec.getBookkeeper().getSetsUpdateStrategy());
    }

    private void cleanupDeletedBookkeeperSets(BookKeeper resource, String namespace, List<BookKeeperSetInfo> bkSets) {
        final BookKeeperFullSpec lastAppliedResource = getLastAppliedResource(resource, BookKeeperFullSpec.class);
        if (lastAppliedResource != null) {
            final Set<String> currentBookkeeperSets = bkSets.stream().map(BookKeeperSetInfo::getName)
                    .collect(Collectors.toSet());
            final List<BookKeeperSetInfo> toDeleteBookkeeperSets =
                    getBookKeeperSets(null, namespace, lastAppliedResource, currentBookkeeperSets);
            for (BookKeeperSetInfo set : toDeleteBookkeeperSets) {
                deleteAll(set);
                log.infof("Deleted bookkeeper set: '%s'", set.getName());
            }
        }
    }

    private List<BookKeeperSetInfo> getBookKeeperSets(OwnerReference ownerReference, String namespace,
                                                      BookKeeperFullSpec spec) {
        return getBookKeeperSets(ownerReference, namespace, spec, Set.of());
    }


    private List<BookKeeperSetInfo> getBookKeeperSets(OwnerReference ownerReference, String namespace,
                                                      BookKeeperFullSpec spec,
                                                      Set<String> excludes) {
        List<BookKeeperSetInfo> result = new ArrayList<>();

        for (Map.Entry<String, BookKeeperSetSpec> bkSet : getBookKeeperSetSpecs(spec).entrySet()) {
            final String name = bkSet.getKey();
            if (excludes.contains(name)) {
                continue;
            }
            final BookKeeperSetSpec setSpec = bkSet.getValue();
            final BookKeeperResourcesFactory
                    resourcesFactory = new BookKeeperResourcesFactory(
                    client, namespace, name, setSpec,
                    spec.getGlobal(), ownerReference);
            result.add(new BookKeeperSetInfo(name, setSpec, resourcesFactory));
        }
        return result;
    }

    public static LinkedHashMap<String, BookKeeperSetSpec> getBookKeeperSetSpecs(BookKeeperFullSpec fullSpec) {
        return getBookKeeperSetSpecs(fullSpec.getBookkeeper());
    }

    public static LinkedHashMap<String, BookKeeperSetSpec> getBookKeeperSetSpecs(BookKeeperSpec bk) {
        LinkedHashMap<String, BookKeeperSetSpec> sets = bk.getSets();
        if (sets == null || sets.isEmpty()) {
            sets = new LinkedHashMap<>(Map.of(BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET,
                    ConfigUtil.applyDefaultsWithReflection(new BookKeeperSetSpec(),
                            () -> bk)));
        } else {
            for (Map.Entry<String, BookKeeperSetSpec> set : sets.entrySet()) {
                sets.put(set.getKey(),
                        ConfigUtil.applyDefaultsWithReflection(set.getValue(), () -> bk)
                );
            }
        }
        return sets;
    }

    private void patchAll(BookKeeperSetInfo info) {
        final BookKeeperResourcesFactory resourcesFactory = info.getBookKeeperResourcesFactory();
        resourcesFactory.patchPodDisruptionBudget();
        resourcesFactory.patchConfigMap();
        resourcesFactory.patchStorageClasses();
        resourcesFactory.patchStatefulSet();
        resourcesFactory.patchService();
    }

    private void deleteAll(BookKeeperSetInfo info) {
        final BookKeeperResourcesFactory resourcesFactory = info.getBookKeeperResourcesFactory();
        if (!info.getName().equals(BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET)) {
            resourcesFactory.deleteService();
        }
        resourcesFactory.deleteStatefulSet();
        resourcesFactory.deleteStorageClass();
        resourcesFactory.deleteConfigMap();
        resourcesFactory.deletePodDisruptionBudget();
    }

    private ReconciliationResult checkReady(BookKeeper resource,
                                            BookKeeperSetInfo setInfo) {
        final BookKeeperResourcesFactory resourcesFactory = setInfo.getBookKeeperResourcesFactory();
        if (!resourcesFactory.isComponentEnabled()) {
            return new ReconciliationResult(
                    false,
                    List.of(createReadyConditionDisabled(resource))
            );
        }
        final StatefulSet sts = resourcesFactory.getStatefulSet();
        if (BaseResourcesFactory.isStatefulSetReady(sts)) {
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
