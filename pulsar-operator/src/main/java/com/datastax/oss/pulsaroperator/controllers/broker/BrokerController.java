package com.datastax.oss.pulsaroperator.controllers.broker;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerFullSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-broker-controller")
@JBossLog
public class BrokerController extends AbstractController<Broker> {

    public BrokerController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected void createResources(Broker resource, Context context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final BrokerFullSpec spec = resource.getSpec();

        final BrokerResourcesFactory
                controller = new BrokerResourcesFactory(
                client, namespace, spec.getBroker(), spec.getGlobal(), getOwnerReference(resource));

        controller.createPodDisruptionBudgetIfEnabled();
        controller.createConfigMap();
        controller.createService();
        controller.createStatefulSet();
    }
}

