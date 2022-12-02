package com.datastax.oss.pulsaroperator.controllers.bastion;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.bastion.Bastion;
import com.datastax.oss.pulsaroperator.crds.bastion.BastionFullSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-bastion-controller")
@JBossLog
public class BastionController extends AbstractController<Bastion> {

    public BastionController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(Bastion resource, Context<Bastion> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final BastionFullSpec spec = resource.getSpec();

        final BastionResourcesFactory
                resourcesFactory = new BastionResourcesFactory(
                client, namespace, spec.getBastion(), spec.getGlobal(), getOwnerReference(resource));

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

    private void patchAll(BastionResourcesFactory controller) {
        controller.patchConfigMap();
        controller.patchDeployment();
    }


    private ReconciliationResult checkReady(Bastion resource,
                                            BastionResourcesFactory resourcesFactory) {
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

