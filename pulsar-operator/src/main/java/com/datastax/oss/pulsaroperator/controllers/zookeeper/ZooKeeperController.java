package com.datastax.oss.pulsaroperator.controllers.zookeeper;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-zk-controller")
@JBossLog
public class ZooKeeperController extends AbstractController<ZooKeeper> {

    public ZooKeeperController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected void createResources(ZooKeeper resource, Context context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final ZooKeeperFullSpec spec = resource.getSpec();
        final ZooKeeperResourcesFactory controller = new ZooKeeperResourcesFactory(
                client, namespace, spec.getZookeeper(), spec.getGlobal(), getOwnerReference(resource));
        controller.createPodDisruptionBudget();
        controller.createConfigMap();
        controller.createStorageClassIfNeeded();
        controller.createService();
        controller.createCaService();
        controller.createStatefulSet();

        if (!controller.metadataInitializationJobExists()) {
            controller.createMetadataInitializationJob();
            log.info("Resources created");
        } else {
            log.info("Resources patched");
        }
    }
}

