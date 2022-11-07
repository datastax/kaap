package com.datastax.oss.pulsaroperator.controllers.zookeeper;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-zk-app")
@JBossLog
public class ZooKeeperController extends AbstractController<ZooKeeper> {

    public ZooKeeperController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected UpdateControl<ZooKeeper> createResources(ZooKeeper resource, Context context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final ZooKeeperFullSpec spec = resource.getSpec();
        final ZooKeeperResourcesFactory controller = new ZooKeeperResourcesFactory(
                client, namespace, spec.getZookeeper(), spec.getGlobal(), getOwnerReference(resource));
        controller.createConfigMap();
        if (spec.getGlobal().getPersistence()
                && spec.getZookeeper().getDataVolume().getExistingStorageClassName() == null
                && spec.getZookeeper().getDataVolume().getStorageClass() != null) {
            controller.createStorageClass();
        }
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
        return UpdateControl.updateStatus(resource);
    }
}

