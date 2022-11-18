package com.datastax.oss.pulsaroperator.controllers.autorecovery;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.autorecovery.Autorecovery;
import com.datastax.oss.pulsaroperator.crds.autorecovery.AutorecoveryFullSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-autorecovery-controller")
@JBossLog
public class AutorecoveryController extends AbstractController<Autorecovery> {

    public AutorecoveryController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected void patchResources(Autorecovery resource, Context<Autorecovery> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final AutorecoveryFullSpec spec = resource.getSpec();

        final AutorecoveryResourcesFactory
                controller = new AutorecoveryResourcesFactory(
                client, namespace, spec.getAutorecovery(), spec.getGlobal(), getOwnerReference(resource));

        controller.patchConfigMap();
        controller.patchDeployment();
    }
}

