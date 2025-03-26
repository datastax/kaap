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
package com.datastax.oss.kaap.controllers.autorecovery;

import com.datastax.oss.kaap.controllers.AbstractController;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.autorecovery.Autorecovery;
import com.datastax.oss.kaap.crds.autorecovery.AutorecoveryFullSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(name = "pulsar-autorecovery-controller")
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
        if (!resourcesFactory.isComponentEnabled()) {
            return new ReconciliationResult(
                    false,
                    List.of(createReadyConditionDisabled(resource))
            );
        }
        final Deployment deployment = resourcesFactory.getDeployment();
        if (deployment == null) {
            patchAll(resourcesFactory);
            return new ReconciliationResult(true,
                    List.of(createNotReadyInitializingCondition(resource)));
        } else {
            if (BaseResourcesFactory.isDeploymentReady(deployment, client)) {
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
