package com.datastax.oss.pulsaroperator.controllers.broker;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerFullSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-broker-controller")
@JBossLog
public class BrokerController extends AbstractController<Broker> {

    public BrokerController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(Broker resource, Context<Broker> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final BrokerFullSpec spec = resource.getSpec();

        final BrokerResourcesFactory
                resourcesFactory = new BrokerResourcesFactory(
                client, namespace, spec.getBroker(), spec.getGlobal(), getOwnerReference(resource));

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

    private void patchAll(BrokerResourcesFactory resourcesFactory) {
        resourcesFactory.patchPodDisruptionBudget();
        resourcesFactory.patchConfigMap();
        resourcesFactory.patchService();
        resourcesFactory.patchStatefulSet();

    }


    private ReconciliationResult checkReady(Broker resource,
                                            BrokerResourcesFactory resourcesFactory) {
        final StatefulSet sts = resourcesFactory.getStatefulSet();
        if (sts == null) {
            patchAll(resourcesFactory);
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
        if (sts.getStatus().getReadyReplicas() == sts.getStatus().getReplicas()) {
            resourcesFactory.createTransactionsInitJobIfNeeded();

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

