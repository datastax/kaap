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
package com.datastax.oss.kaap.controllers.bookkeeper;

import com.datastax.oss.kaap.autoscaler.bookkeeper.BookieAdminClient;
import com.datastax.oss.kaap.autoscaler.bookkeeper.BookieDecommissionUtil;
import com.datastax.oss.kaap.autoscaler.bookkeeper.PodExecBookieAdminClient;
import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.common.json.JSONComparator;
import com.datastax.oss.kaap.controllers.AbstractResourceSetsController;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.controllers.bookkeeper.racks.BookKeeperRackDaemon;
import com.datastax.oss.kaap.controllers.bookkeeper.racks.client.ZkClientRackClientFactory;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.SpecDiffer;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeper;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.quarkus.runtime.ShutdownEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.event.Observes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(name = "pulsar-bk-controller")
@JBossLog
public class BookKeeperController extends
        AbstractResourceSetsController<BookKeeper, BookKeeperFullSpec, BookKeeperSpec, BookKeeperSetSpec,
                BookKeeperResourcesFactory,
                BookKeeperController.BookKeeperSetsLastApplied> {

    private final BookKeeperRackDaemon bkRackDaemon;

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
        bkRackDaemon = this.initBookKeeperRackDaemon(client);
    }

    protected BookKeeperRackDaemon initBookKeeperRackDaemon(KubernetesClient client) {
        return new BookKeeperRackDaemon(client, new ZkClientRackClientFactory(client));
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
    protected void deleteResourceSet(SetInfo<BookKeeperSetSpec, BookKeeperResourcesFactory> set, BookKeeper resource) {
        final BookKeeperResourcesFactory resourcesFactory = set.getResourceFactory();
        if (!set.getName().equals(BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET)) {
            resourcesFactory.deleteService();
        }
        resourcesFactory.deleteStatefulSet();
        resourcesFactory.deleteStorageClass();
        resourcesFactory.deleteConfigMap();
        resourcesFactory.deletePodDisruptionBudget();
        cleanupOrphanPVCs(set, resource.getSpec(), resource.getMetadata().getNamespace());
    }

    @Override
    protected void onSetReady(BookKeeperFullSpec lastAppliedFullSpec, BookKeeper resource,
                              SetInfo<BookKeeperSetSpec, BookKeeperResourcesFactory> setInfo) {
        cleanupOrphanPVCs(setInfo, lastAppliedFullSpec, resource.getMetadata().getNamespace());
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

    private void cleanupOrphanPVCs(SetInfo<BookKeeperSetSpec, BookKeeperResourcesFactory> setInfo,
                                   BookKeeperFullSpec lastAppliedFullSpec,
                                   String namespace) {
        final int deletedPvcs = setInfo.getResourceFactory().cleanupOrphanPVCs();
        if (deletedPvcs > 0) {
            final BookieAdminClient bookieAdminClient = createBookieAdminClient(namespace, setInfo.getName(),
                    lastAppliedFullSpec);
            bookieAdminClient.triggerAudit();
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
            BookKeeper resource, SetInfo<BookKeeperSetSpec, BookKeeperResourcesFactory> setInfo,
            BookKeeperFullSpec spec,
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
        final JSONComparator.Result result = SpecDiffer.generateDiff(lastApplied, spec);
        if (!result.areEquals()) {
            if (lastApplied != null) {
                final BookKeeperSetSpec lastAppliedSetSpec =
                        lastApplied.getBookkeeper().getBookKeeperSetSpecRef(setInfo.getName());
                final BookKeeperSetSpec desiredSetSpec =
                        spec.getBookkeeper().getBookKeeperSetSpecRef(setInfo.getName());
                if (lastAppliedSetSpec != null && desiredSetSpec != null) {

                    final int currentReplicas = lastAppliedSetSpec.getReplicas().intValue();
                    final int desiredReplicas = desiredSetSpec.getReplicas().intValue();
                    final int delta = currentReplicas - desiredReplicas;
                    if (delta > 0) {
                        final BookieAdminClient bookieAdminClient =
                                createBookieAdminClient(resource.getMetadata().getNamespace(),
                                        setInfo.getName(), lastApplied);

                        final int decommissioned = BookieDecommissionUtil
                                .decommissionBookies(bookieAdminClient.collectBookieInfos(),
                                        delta, bookieAdminClient);
                        if (decommissioned != delta) {
                            throw new IllegalStateException(
                                    "Failed to decommission " + (delta - decommissioned) + " bookies, will retry");
                        }
                    }
                }
            }
            final PulsarClusterSpec pulsarClusterSpec = PulsarClusterSpec.builder()
                    .global(spec.getGlobal())
                    .bookkeeper(spec.getBookkeeper())
                    .build();
            bkRackDaemon.cancelTasks();
            final String namespace = resource.getMetadata().getNamespace();
            log.infof("Initializing bookie racks for bookkeeper-set '%s'", setInfo.getName());
            bkRackDaemon.triggerSync(namespace, spec);
            bkRackDaemon.onSpecChange(pulsarClusterSpec, namespace);
        }
        return result;
    }

    protected BookieAdminClient createBookieAdminClient(String namespace,
                                                        String setName,
                                                        BookKeeperFullSpec lastApplied) {
        final BookKeeperSetSpec lastAppliedSetSpec =
                lastApplied.getBookkeeper().getBookKeeperSetSpecRef(setName);
        return new PodExecBookieAdminClient(client,
                namespace,
                lastApplied.getGlobalSpec(),
                setName,
                lastAppliedSetSpec);
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

    void onStop(@Observes ShutdownEvent ev) {
        if (bkRackDaemon != null) {
            bkRackDaemon.close();
        }
    }
}
