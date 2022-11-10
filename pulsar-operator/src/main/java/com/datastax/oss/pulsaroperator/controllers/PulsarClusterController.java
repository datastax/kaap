package com.datastax.oss.pulsaroperator.controllers;

import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterStatus;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-cluster-app")
@JBossLog
public class PulsarClusterController extends AbstractController<PulsarCluster> {

    public PulsarClusterController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected UpdateControl<PulsarCluster> createResources(PulsarCluster resource, Context<PulsarCluster> context)
            throws Exception {

        final String currentNamespace = resource.getMetadata().getNamespace();
        final PulsarClusterSpec clusterSpec = resource.getSpec();

        final List<OwnerReference> ownerReference = List.of(getOwnerReference(resource));

        createZookeeper(currentNamespace, clusterSpec, ownerReference);
        createBookkeeper(currentNamespace, clusterSpec, ownerReference);


        final PulsarClusterStatus status = new PulsarClusterStatus();
        status.setError(null);
        status.setCurrentSpec(clusterSpec);
        resource.setStatus(status);
        return UpdateControl.updateStatus(resource);
    }

    private void createZookeeper(String currentNamespace, PulsarClusterSpec clusterSpec,
                                 List<OwnerReference> ownerReferences) {
        final MixedOperation<ZooKeeper, KubernetesResourceList<ZooKeeper>, Resource<ZooKeeper>> zkResourceClient =
                client.resources(ZooKeeper.class);

        if (zkResourceClient == null) {
            throw new IllegalStateException("ZooKeeper CRD not found");
        }
        ObjectMeta meta = new ObjectMeta();
        meta.setName(clusterSpec.getGlobal().getName() + "-zookeeper");
        meta.setNamespace(currentNamespace);
        meta.setOwnerReferences(ownerReferences);
        final ZooKeeper zooKeeper = new ZooKeeper();
        zooKeeper.setMetadata(meta);

        zooKeeper.setSpec(ZooKeeperFullSpec.builder()
                .global(clusterSpec.getGlobal())
                .zookeeper(clusterSpec.getZookeeper())
                .build());

        zkResourceClient
                .inNamespace(currentNamespace)
                .resource(zooKeeper).createOrReplace();
    }

    private void createBookkeeper(String currentNamespace, PulsarClusterSpec clusterSpec,
                                  List<OwnerReference> ownerReferences) {
        final MixedOperation<BookKeeper, KubernetesResourceList<BookKeeper>, Resource<BookKeeper>> bkResourceClient =
                client.resources(BookKeeper.class);
        if (bkResourceClient == null) {
            throw new IllegalStateException("BookKeeper CRD not found");
        }

        ObjectMeta meta = new ObjectMeta();
        meta.setName(clusterSpec.getGlobal().getName() + "-bookkeeper");
        meta.setNamespace(currentNamespace);
        meta.setOwnerReferences(ownerReferences);
        final BookKeeper bk = new BookKeeper();
        bk.setMetadata(meta);

        bk.setSpec(BookKeeperFullSpec.builder()
                .global(clusterSpec.getGlobal())
                .bookkeeper(clusterSpec.getBookkeeper())
                .build());

        bkResourceClient
                .inNamespace(currentNamespace)
                .resource(bk)
                .createOrReplace();
    }
}

