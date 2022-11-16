package com.datastax.oss.pulsaroperator.controllers.proxy;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.proxy.Proxy;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxyFullSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-proxy-controller")
@JBossLog
public class ProxyController extends AbstractController<Proxy> {

    public ProxyController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected void patchResources(Proxy resource, Context context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final ProxyFullSpec spec = resource.getSpec();

        final ProxyResourcesFactory
                controller = new ProxyResourcesFactory(
                client, namespace, spec.getProxy(), spec.getGlobal(), getOwnerReference(resource));

        controller.createPodDisruptionBudgetIfEnabled();
        controller.createConfigMap();
        controller.createConfigMapWsConfig();
        controller.createService();
        controller.createDeployment();
    }
}

