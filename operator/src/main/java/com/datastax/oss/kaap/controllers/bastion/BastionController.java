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
package com.datastax.oss.kaap.controllers.bastion;

import com.datastax.oss.kaap.controllers.AbstractController;
import com.datastax.oss.kaap.controllers.BaseResourcesFactory;
import com.datastax.oss.kaap.crds.bastion.Bastion;
import com.datastax.oss.kaap.crds.bastion.BastionFullSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(name = "pulsar-bastion-controller")
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
