package com.datastax.oss.pulsaroperator.controllers.bookkeeper;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-bk-controller")
@JBossLog
public class BookKeeperController extends AbstractController<BookKeeper> {

    public BookKeeperController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(BookKeeper resource, Context<BookKeeper> context) throws Exception {

        final String namespace = resource.getMetadata().getNamespace();
        final BookKeeperFullSpec spec = resource.getSpec();

        final BookKeeperResourcesFactory resourcesFactory = new BookKeeperResourcesFactory(
                client, namespace, spec.getBookkeeper(), spec.getGlobal(), getOwnerReference(resource));

        if (!areSpecChanged(resource)) {
            return checkReady(resource, resourcesFactory);
        } else {
            patchAll(resourcesFactory);
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
    }

    private ReconciliationResult checkReady(BookKeeper resource,
                                            BookKeeperResourcesFactory resourcesFactory) {
        final StatefulSet statefulSet = resourcesFactory.getStatefulSet();
        if (statefulSet == null) {
            patchAll(resourcesFactory);
            return new ReconciliationResult(true,
                    List.of(createNotReadyInitializingCondition(resource)));
        } else {
            if (BaseResourcesFactory.isStatefulSetReady(statefulSet)) {
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
    }

    private void patchAll(BookKeeperResourcesFactory resourcesFactory) {
        resourcesFactory.patchPodDisruptionBudget();
        resourcesFactory.patchConfigMap();
        resourcesFactory.patchStorageClasses();
        resourcesFactory.patchService();
        resourcesFactory.patchStatefulSet();
    }
}

