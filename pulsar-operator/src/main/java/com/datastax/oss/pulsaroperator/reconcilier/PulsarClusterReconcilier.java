package com.datastax.oss.pulsaroperator.reconcilier;

import com.datastax.oss.pulsaroperator.crd.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crd.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeperSpec;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.extern.java.Log;
import lombok.extern.jbosslog.JBossLog;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-cluster-app")
@JBossLog
public class PulsarClusterReconcilier implements Reconciler<PulsarCluster> {

    private final KubernetesClient client;

    public PulsarClusterReconcilier(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<PulsarCluster> reconcile(PulsarCluster resource, Context context) {
        log.infof("Zookeeper reconcilier, new spec %s, current spec %s", resource.getSpec(),
                resource.getStatus().getCurrentSpec());

        final MixedOperation<ZooKeeper, KubernetesResourceList<ZooKeeper>, Resource<ZooKeeper>> zk = client
                .customResources(ZooKeeper.class);
        final String currentNamespace = resource.getMetadata().getNamespace();
        final PulsarClusterSpec clusterSpec = resource.getSpec();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(clusterSpec.getCluster().getFullname() + "-zookeeeper-cr");
        meta.setNamespace(currentNamespace);

        final ZooKeeper zooKeeper = new ZooKeeper();
        zooKeeper.setMetadata(meta);
        final ZooKeeperSpec spec = clusterSpec.getZookeeper();
        spec.setClusterSpec(clusterSpec.getCluster());
        zooKeeper.setSpec(spec);

        zk.inNamespace(currentNamespace).createOrReplace(zooKeeper);

        resource.getStatus().setCurrentSpec(clusterSpec);

        /*
        - store da qualche parte la conf
        - new zk cr
         */
        return UpdateControl.updateStatus(resource);
    }
}

