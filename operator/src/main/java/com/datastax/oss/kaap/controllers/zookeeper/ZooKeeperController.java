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
package com.datastax.oss.kaap.controllers.zookeeper;

import com.datastax.oss.kaap.controllers.AbstractController;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeper;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(name = "pulsar-zk-controller")
@JBossLog
public class ZooKeeperController extends AbstractController<ZooKeeper> {

    public ZooKeeperController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(ZooKeeper resource, Context<ZooKeeper> context) throws Exception {

        final String namespace = resource.getMetadata().getNamespace();
        final ZooKeeperFullSpec spec = resource.getSpec();
        final ZooKeeperResourcesFactory resourcesFactory = new ZooKeeperResourcesFactory(
                client, namespace, spec.getZookeeper(), spec.getGlobal(), getOwnerReference(resource));


        if (!areSpecChanged(resource)) {
            return checkReady(resource, resourcesFactory);
        } else {
            patchAll(resourcesFactory);
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyCondition(resource,
                            CRDConstants.CONDITIONS_TYPE_READY_REASON_INITIALIZING,
                            null))
            );
        }
    }

    private void patchAll(ZooKeeperResourcesFactory resourcesFactory) {
        resourcesFactory.patchPodDisruptionBudget();
        resourcesFactory.patchConfigMap();
        resourcesFactory.patchStorageClass();
        resourcesFactory.patchService();
        resourcesFactory.patchCaService();
        resourcesFactory.patchStatefulSet();
        log.info("Patched zookkeeper resources");
    }

    private ReconciliationResult checkReady(ZooKeeper resource,
                                            ZooKeeperResourcesFactory resourcesFactory) {
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

            final Job job = resourcesFactory.getMetadataInitializationJob();
            if (job == null) {
                resourcesFactory.createMetadataInitializationJobIfNeeded();
                return new ReconciliationResult(
                        true,
                        List.of(createNotReadyInitializingCondition(resource))
                );
            } else {
                if (resourcesFactory.isJobCompleted(job)) {
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
        } else {
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
    }


}
