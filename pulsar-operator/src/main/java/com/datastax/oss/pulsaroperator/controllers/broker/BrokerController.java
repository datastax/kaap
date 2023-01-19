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
package com.datastax.oss.pulsaroperator.controllers.broker;

import com.datastax.oss.pulsaroperator.controllers.AbstractController;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerFullSpec;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;


@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-broker-controller")
@JBossLog
public class BrokerController extends AbstractController<Broker> {

    public BrokerController(KubernetesClient client) {
        super(client);
    }

    @Override
    protected ReconciliationResult patchResources(Broker resource, Context<Broker> context) throws Exception {
        final String namespace = resource.getMetadata().getNamespace();
        final BrokerFullSpec spec = resource.getSpec();

        final BrokerResourcesFactory
                resourcesFactory = new BrokerResourcesFactory(
                client, namespace, spec.getBroker(), spec.getGlobal(), getOwnerReference(resource));

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

    private void patchAll(BrokerResourcesFactory resourcesFactory) {
        resourcesFactory.patchPodDisruptionBudget();
        resourcesFactory.patchConfigMap();
        resourcesFactory.patchService();
        resourcesFactory.patchStatefulSet();

    }


    private ReconciliationResult checkReady(Broker resource,
                                            BrokerResourcesFactory resourcesFactory) {
        final StatefulSet sts = resourcesFactory.getStatefulSet();
        if (sts == null) {
            patchAll(resourcesFactory);
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
        if (BaseResourcesFactory.isStatefulSetReady(sts)) {
            resourcesFactory.createTransactionsInitJobIfNeeded();

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

