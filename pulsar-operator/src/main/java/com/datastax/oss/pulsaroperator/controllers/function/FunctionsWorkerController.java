package com.datastax.oss.pulsaroperator.controllers.function;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorker;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorkerFullSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-fn-worker-controller")
@JBossLog
public class FunctionsWorkerController extends AbstractController<FunctionsWorker> {

    public FunctionsWorkerController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected void patchResources(FunctionsWorker resource, Context<FunctionsWorker> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final FunctionsWorkerFullSpec spec = resource.getSpec();

        final FunctionsWorkerResourcesFactory
                controller = new FunctionsWorkerResourcesFactory(
                client, namespace, spec.getFunctionsWorker(), spec.getGlobal(), getOwnerReference(resource));

        controller.patchRBAC();
        controller.patchPodDisruptionBudget();
        controller.patchConfigMap();
        controller.patchStorageClass();
        controller.patchCaService();
        controller.patchService();
        controller.patchStatefulSet();
    }
}

