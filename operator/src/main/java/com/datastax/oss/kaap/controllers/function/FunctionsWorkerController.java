/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.kaap.controllers.function;

import com.datastax.oss.kaap.controllers.AbstractController;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.function.FunctionsWorker;
import com.datastax.oss.kaap.crds.function.FunctionsWorkerFullSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(name = "pulsar-fn-worker-controller")
@JBossLog
public class FunctionsWorkerController extends AbstractController<FunctionsWorker> {

    public FunctionsWorkerController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(FunctionsWorker resource, Context<FunctionsWorker> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final FunctionsWorkerFullSpec spec = resource.getSpec();

        final FunctionsWorkerResourcesFactory
                resourcesFactory = new FunctionsWorkerResourcesFactory(
                client, namespace, spec.getFunctionsWorker(), spec.getGlobal(), getOwnerReference(resource));


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

    private void patchAll(FunctionsWorkerResourcesFactory controller) {
        controller.patchRBAC();
        controller.patchPodDisruptionBudget();
        controller.patchConfigMap();
        controller.patchExtraConfigMap();
        controller.patchStorageClass();
        controller.patchCaService();
        controller.patchService();
        controller.patchStatefulSet();
    }


    private ReconciliationResult checkReady(FunctionsWorker resource,
                                            FunctionsWorkerResourcesFactory resourcesFactory) {
        if (!resourcesFactory.isComponentEnabled()) {
            return new ReconciliationResult(
                    false,
                    List.of(createReadyConditionDisabled(resource))
            );
        }
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

}
