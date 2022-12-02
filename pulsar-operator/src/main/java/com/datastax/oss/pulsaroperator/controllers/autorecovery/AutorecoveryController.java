package com.datastax.oss.pulsaroperator.controllers.autorecovery;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.autorecovery.Autorecovery;
import com.datastax.oss.pulsaroperator.crds.autorecovery.AutorecoveryFullSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-autorecovery-controller")
@JBossLog
public class AutorecoveryController extends AbstractController<Autorecovery> {

    public AutorecoveryController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(Autorecovery resource, Context<Autorecovery> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final AutorecoveryFullSpec spec = resource.getSpec();

        final AutorecoveryResourcesFactory
                resourcesFactory = new AutorecoveryResourcesFactory(
                client, namespace, spec.getAutorecovery(), spec.getGlobal(), getOwnerReference(resource));


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

    private void patchAll(AutorecoveryResourcesFactory controller) {
        controller.patchConfigMap();
        controller.patchDeployment();
    }


    private ReconciliationResult checkReady(Autorecovery resource,
                                            AutorecoveryResourcesFactory resourcesFactory) {
        final Deployment deployment = resourcesFactory.getDeployment();
        if (deployment == null) {
            patchAll(resourcesFactory);
            return new ReconciliationResult(true,
                    List.of(createNotReadyInitializingCondition(resource)));
        } else {
            if (BaseResourcesFactory.isDeploymentReady(deployment)) {
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

}

