package com.datastax.oss.pulsaroperator.controllers.proxy;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.proxy.Proxy;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxyFullSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-proxy-controller")
@JBossLog
public class ProxyController extends AbstractController<Proxy> {

    public ProxyController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(Proxy resource, Context<Proxy> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final ProxyFullSpec spec = resource.getSpec();

        final ProxyResourcesFactory
                resourcesFactory = new ProxyResourcesFactory(
                client, namespace, spec.getProxy(), spec.getGlobal(), getOwnerReference(resource));


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

    private void patchAll(ProxyResourcesFactory controller) {
        controller.patchPodDisruptionBudget();
        controller.patchConfigMap();
        controller.patchConfigMapWsConfig();
        controller.patchService();
        controller.patchDeployment();
    }


    private ReconciliationResult checkReady(Proxy resource,
                                            ProxyResourcesFactory resourcesFactory) {
        final Deployment deployment = resourcesFactory.getDeployment();
        if (deployment == null) {
            patchAll(resourcesFactory);
            return new ReconciliationResult(true,
                    List.of(createNotReadyInitializingCondition(resource)));
        } else {
            if (deployment.getStatus().getReadyReplicas() == deployment.getStatus().getReplicas()) {
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

