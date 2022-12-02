package com.datastax.oss.pulsaroperator.controllers.zookeeper;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-zk-controller")
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
    }

    private ReconciliationResult checkReady(ZooKeeper resource,
                                            ZooKeeperResourcesFactory resourcesFactory) {
        final StatefulSet sts = resourcesFactory.getStatefulSet();
        if (sts == null) {
            patchAll(resourcesFactory);
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
        if (sts.getStatus().getReadyReplicas() == sts.getStatus().getReplicas()) {

            final Job job = resourcesFactory.getJob();
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

