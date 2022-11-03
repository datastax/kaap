package com.datastax.oss.pulsaroperator.reconcilier;

import com.datastax.oss.pulsaroperator.crd.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crd.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.extern.jbosslog.JBossLog;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-cluster-app")
@JBossLog
public class PulsarClusterReconcilier extends AbstractReconcilier<PulsarCluster> {

    public PulsarClusterReconcilier(KubernetesClient client) {
        super(client);
    }

    @Override
    protected UpdateControl<PulsarCluster> createResources(PulsarCluster resource, Context<PulsarCluster> context)
            throws Exception {
        final MixedOperation<ZooKeeper, KubernetesResourceList<ZooKeeper>, Resource<ZooKeeper>> zk = client
                .customResources(ZooKeeper.class);
        final String currentNamespace = resource.getMetadata().getNamespace();
        final PulsarClusterSpec clusterSpec = resource.getSpec();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(clusterSpec.getGlobal().getName() + "-zookeeeper-cr");
        meta.setNamespace(currentNamespace);

        final ZooKeeper zooKeeper = new ZooKeeper();
        zooKeeper.setMetadata(meta);

        zooKeeper.setSpec(ZooKeeperFullSpec.builder()
                .global(clusterSpec.getGlobal())
                .zookeeper(clusterSpec.getZookeeper())
                .build());

        zk.inNamespace(currentNamespace).createOrReplace(zooKeeper);
        resource.getStatus().setCurrentSpec(clusterSpec);
        return UpdateControl.updateStatus(resource);
    }
}

