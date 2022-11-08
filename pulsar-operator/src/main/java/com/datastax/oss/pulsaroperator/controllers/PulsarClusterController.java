package com.datastax.oss.pulsaroperator.controllers;

import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterStatus;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
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

        final MixedOperation<ZooKeeper, KubernetesResourceList<ZooKeeper>, Resource<ZooKeeper>> zkResourceClient =
                client.resources(ZooKeeper.class);

        if (zkResourceClient == null) {
            throw new IllegalStateException("ZooKeeper CRD not found");
        }

        final String currentNamespace = resource.getMetadata().getNamespace();
        final PulsarClusterSpec clusterSpec = resource.getSpec();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(clusterSpec.getGlobal().getName() + "-zookeeeper");
        meta.setNamespace(currentNamespace);
        meta.setOwnerReferences(List.of(getOwnerReference(resource)));
        final ZooKeeper zooKeeper = new ZooKeeper();
        zooKeeper.setMetadata(meta);

        zooKeeper.setSpec(ZooKeeperFullSpec.builder()
                .global(clusterSpec.getGlobal())
                .zookeeper(clusterSpec.getZookeeper())
                .build());

        zkResourceClient
                .inNamespace(currentNamespace)
                .resource(zooKeeper).createOrReplace();
        final PulsarClusterStatus status = new PulsarClusterStatus();
        status.setError(null);
        status.setCurrentSpec(clusterSpec);
        resource.setStatus(status);
        return UpdateControl.updateStatus(resource);
    }
}

