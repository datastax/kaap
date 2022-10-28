package com.datastax.oss.pulsaroperator.reconcilier;

import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-zk-app")
@JBossLog
public class ZooKeeperReconcilier implements Reconciler<ZooKeeper> {

    private final KubernetesClient client;

    @SneakyThrows
    public ZooKeeperReconcilier(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<ZooKeeper> reconcile(ZooKeeper resource, Context context) {
        final String namespace = resource.getMetadata().getNamespace();
        final ZooKeeperFullSpec spec = resource.getSpec();

        log.infof("Zookeeper reconcilier, new spec %s, current spec %s", spec,
                resource.getStatus().getCurrentSpec());

        final ZooKeeperResourcesController controller = new ZooKeeperResourcesController(
                client, namespace, spec.getZookeeper(), spec.getGlobal());
        controller.createConfigMap();
        controller.createStatefulSet();
        controller.createService();
        controller.createCaService();

        resource.getStatus().setCurrentSpec(spec);
        // statefulSet
        // service
        // storageClass
        // pdb
        // metadata init job
        // config-map

        return UpdateControl.updateStatus(resource);
    }

}

