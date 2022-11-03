package com.datastax.oss.pulsaroperator.reconcilier;

import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-zk-app")
@JBossLog
public class ZooKeeperReconcilier extends AbstractReconcilier<ZooKeeper> {

    public ZooKeeperReconcilier(KubernetesClient client) {
        super(client);
    }

    @Override
    protected UpdateControl<ZooKeeper> createResources(ZooKeeper resource, Context context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final ZooKeeperFullSpec spec = resource.getSpec();
        final ZooKeeperResourcesController controller = new ZooKeeperResourcesController(
                client, namespace, spec.getZookeeper(), spec.getGlobal());
        controller.createConfigMap();
        controller.createStatefulSet();
        controller.createService();
        controller.createCaService();

        // statefulSet
        // service
        // storageClass
        // pdb
        // metadata init job
        // config-map

        resource.getStatus().setCurrentSpec(spec);
        return UpdateControl.updateResource(resource);
    }
}

