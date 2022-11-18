package com.datastax.oss.pulsaroperator.controllers.bookkeeper;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-bk-controller")
@JBossLog
public class BookKeeperController extends AbstractController<BookKeeper> {

    public BookKeeperController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected void patchResources(BookKeeper resource, Context<BookKeeper> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final BookKeeperFullSpec spec = resource.getSpec();

        final BookKeeperResourcesFactory controller = new BookKeeperResourcesFactory(
                client, namespace, spec.getBookkeeper(), spec.getGlobal(), getOwnerReference(resource));

        controller.patchPodDisruptionBudget();
        controller.patchConfigMap();
        controller.patchStorageClasses();
        controller.patchService();
        controller.patchStatefulSet();
    }
}

