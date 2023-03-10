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

import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import com.datastax.oss.pulsaroperator.common.json.JSONComparator;
import com.datastax.oss.pulsaroperator.controllers.AbstractResourceSetsController;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.SpecDiffer;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
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


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-bk-controller")
@JBossLog
public class BookKeeperController extends
        AbstractResourceSetsController<BookKeeper, BookKeeperFullSpec, BookKeeperSpec, BookKeeperSetSpec,
                BookKeeperResourcesFactory,
                BookKeeperController.BookKeeperSetsLastApplied> {

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

    public static LinkedHashMap<String, BookKeeperSetSpec> getBookKeeperSetSpecs(BookKeeperFullSpec spec) {
        return getBookKeeperSetSpecs(spec.getBookkeeper());
    }

    public static LinkedHashMap<String, BookKeeperSetSpec> getBookKeeperSetSpecs(BookKeeperSpec spec) {
        return new BookKeeperController(null).getSetSpecs(spec);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BookKeeperSetsLastApplied
            implements AbstractResourceSetsController.SetsLastApplied<BookKeeperFullSpec> {
        private BookKeeperFullSpec common;
        private Map<String, BookKeeperFullSpec> sets = new HashMap<>();
    }


    public BookKeeperController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected String getComponentNameForLogs() {
        return "bookkeeper";
    }

    @Override
    protected void patchResourceSet(SetInfo<BookKeeperSetSpec, BookKeeperResourcesFactory> set) {
        final BookKeeperResourcesFactory resourcesFactory = set.getResourceFactory();
        resourcesFactory.patchPodDisruptionBudget();
        resourcesFactory.patchConfigMap();
        resourcesFactory.patchStorageClasses();
        resourcesFactory.patchStatefulSet();
        resourcesFactory.patchService();
    }

    @Override
    protected void deleteResourceSet(SetInfo<BookKeeperSetSpec, BookKeeperResourcesFactory> set) {
        final BookKeeperResourcesFactory resourcesFactory = set.getResourceFactory();
        if (!set.getName().equals(BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET)) {
            resourcesFactory.deleteService();
        }
        resourcesFactory.deleteStatefulSet();
        resourcesFactory.deleteStorageClass();
        resourcesFactory.deleteConfigMap();
        resourcesFactory.deletePodDisruptionBudget();
    }

    @Override
    protected void patchCommonResources(SetInfo<BookKeeperSetSpec, BookKeeperResourcesFactory> set) {
        set.getResourceFactory().patchService();
    }

    @Override
    protected ReconciliationResult checkReady(BookKeeper resource,
                                              SetInfo<BookKeeperSetSpec, BookKeeperResourcesFactory> set) {
        final BookKeeperResourcesFactory resourcesFactory = set.getResourceFactory();
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

    @Override
    protected String getDefaultSetName() {
        return BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET;
    }

    @Override
    protected BookKeeperSpec getSpec(BookKeeperFullSpec bookKeeperFullSpec) {
        return bookKeeperFullSpec.getBookkeeper();
    }

    @Override
    protected Map<String, BookKeeperSetSpec> getSets(BookKeeperSpec bookKeeperSpec) {
        return bookKeeperSpec.getSets();
    }

    @Override
    protected BookKeeperResourcesFactory newFactory(OwnerReference ownerReference, String namespace, String setName,
                                                    BookKeeperSetSpec setSpec, GlobalSpec globalSpec) {
        return new BookKeeperResourcesFactory(client, namespace, setName, setSpec, globalSpec, ownerReference);
    }

    @Override
    protected JSONComparator.Result compareLastAppliedSetSpec(
            SetInfo<BookKeeperSetSpec, BookKeeperResourcesFactory> setInfo, BookKeeperFullSpec spec,
            BookKeeperFullSpec lastApplied) {
        if (spec.getBookkeeper().getSets() != null) {
            spec = SerializationUtil.deepCloneObject(spec);
            spec.getBookkeeper().getSets().entrySet()
                    .removeIf(e -> !e.getKey().equals(setInfo.getName()));
        }

        if (lastApplied != null && lastApplied.getBookkeeper().getSets() != null) {
            lastApplied = SerializationUtil.deepCloneObject(lastApplied);
            lastApplied.getBookkeeper().getSets().entrySet()
                    .removeIf(e -> !e.getKey().equals(setInfo.getName()));
        }
        return SpecDiffer.generateDiff(spec, lastApplied);
    }

    @Override
    protected BookKeeperSetsLastApplied readSetsLastApplied(BookKeeper resource) {
        final BookKeeperSetsLastApplied
                last = getLastAppliedResource(resource, BookKeeperSetsLastApplied.class);
        if (last == null) {
            return new BookKeeperSetsLastApplied();
        }
        return last;
    }

    @Override
    protected boolean isRollingUpdate(BookKeeperFullSpec bookKeeperFullSpec) {
        return BookKeeperSpec.BookKeeperSetsUpdateStrategy.RollingUpdate.toString()
                .equals(bookKeeperFullSpec.getBookkeeper().getSetsUpdateStrategy());
    }
}
