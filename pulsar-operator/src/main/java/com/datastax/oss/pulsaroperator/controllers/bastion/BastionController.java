package com.datastax.oss.pulsaroperator.controllers.bastion;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.bastion.Bastion;
import com.datastax.oss.pulsaroperator.crds.bastion.BastionFullSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-bastion-controller")
@JBossLog
public class BastionController extends AbstractController<Bastion> {

    public BastionController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected void patchResources(Bastion resource, Context<Bastion> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final BastionFullSpec spec = resource.getSpec();

        final BastionResourcesFactory
                controller = new BastionResourcesFactory(
                client, namespace, spec.getBastion(), spec.getGlobal(), getOwnerReference(resource));

        controller.patchConfigMap();
        controller.patchDeployment();
    }
}

