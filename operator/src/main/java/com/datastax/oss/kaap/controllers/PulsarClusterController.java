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
package com.datastax.oss.kaap.controllers;

import com.datastax.oss.kaap.autoscaler.AutoscalerDaemon;
import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.common.json.JSONComparator;
import com.datastax.oss.kaap.controllers.bookkeeper.BookKeeperController;
import com.datastax.oss.kaap.controllers.broker.BrokerController;
import com.datastax.oss.kaap.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.kaap.controllers.utils.CertManagerCertificatesProvisioner;
import com.datastax.oss.kaap.controllers.utils.TokenAuthProvisioner;
import com.datastax.oss.kaap.crds.BaseComponentStatus;
import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.SpecDiffer;
import com.datastax.oss.kaap.crds.autorecovery.Autorecovery;
import com.datastax.oss.kaap.crds.autorecovery.AutorecoveryFullSpec;
import com.datastax.oss.kaap.crds.bastion.Bastion;
import com.datastax.oss.kaap.crds.bastion.BastionFullSpec;
import com.datastax.oss.kaap.crds.bastion.BastionSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeper;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.kaap.crds.broker.Broker;
import com.datastax.oss.kaap.crds.broker.BrokerFullSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSetSpec;
import com.datastax.oss.kaap.crds.cluster.PulsarCluster;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.kaap.crds.configs.AuthConfig;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.crds.function.FunctionsWorker;
import com.datastax.oss.kaap.crds.function.FunctionsWorkerFullSpec;
import com.datastax.oss.kaap.crds.proxy.Proxy;
import com.datastax.oss.kaap.crds.proxy.ProxyFullSpec;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeper;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.quarkus.runtime.ShutdownEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;

@ControllerConfiguration(name = "pulsar-cluster-app")
@JBossLog
@ApplicationScoped
public class PulsarClusterController extends AbstractController<PulsarCluster> {

    public static final String CUSTOM_RESOURCE_BROKER = "broker";
    public static final String CUSTOM_RESOURCE_BOOKKEEPER = "bookkeeper";
    public static final String CUSTOM_RESOURCE_ZOOKEEPER = "zookeeper";
    public static final String CUSTOM_RESOURCE_PROXY = "proxy";
    public static final String CUSTOM_RESOURCE_AUTORECOVERY = "autorecovery";
    public static final String CUSTOM_RESOURCE_BASTION = "bastion";
    public static final String CUSTOM_RESOURCE_FUNCTIONS_WORKER = "functionsworker";

    public static String computeCustomResourceName(PulsarClusterSpec clusterSpec, String customResourceName) {
        return "%s-%s".formatted(clusterSpec.getGlobal().getName(), customResourceName);
    }

    private final AutoscalerDaemon autoscaler;

    public PulsarClusterController(KubernetesClient client) {
        super(client);
        autoscaler = new AutoscalerDaemon(client);
    }

    @Override
    protected ReconciliationResult patchResources(PulsarCluster resource, Context<PulsarCluster> context)
            throws Exception {

        final String currentNamespace = resource.getMetadata().getNamespace();
        final PulsarClusterSpec clusterSpec = resource.getSpec();

        final List<OwnerReference> ownerReference = List.of(getOwnerReference(resource));
        generateSecretsIfAbsent(currentNamespace, clusterSpec);
        setupTls(currentNamespace, clusterSpec);

        if (!checkReadyOrPatchZooKeeper(currentNamespace, clusterSpec, ownerReference)) {
            log.info("waiting for zookeeper to become ready");
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }


        if (!checkReadyOrPatchBookKeeper(currentNamespace, clusterSpec, ownerReference)) {
            log.info("waiting for bookkeeper to become ready");
            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }
        autoscaler.getBookKeeperAutoscalerDaemon().onSpecChange(clusterSpec, currentNamespace);

        final boolean brokerReady = checkReadyOrPatchBroker(currentNamespace, clusterSpec, ownerReference);
        autoscaler.getBrokerAutoscalerDaemon().onSpecChange(clusterSpec, currentNamespace);

        adjustProxyFunctionsWorkerDeployment(clusterSpec);
        final boolean proxyReady = checkReadyOrPatchProxy(currentNamespace, clusterSpec, ownerReference);

        adjustBastionTarget(clusterSpec);
        final boolean bastionReady = checkReadyOrPatchBastion(currentNamespace, clusterSpec, ownerReference);
        boolean functionsWorkerReady = false;
        if (brokerReady) {
            functionsWorkerReady =
                    checkReadyOrPatchFunctionsWorker(currentNamespace, clusterSpec, ownerReference);
        }
        final boolean autorecoveryReady = checkReadyOrPatchAutorecovery(currentNamespace, clusterSpec, ownerReference);

        boolean allReady = autorecoveryReady
                && brokerReady
                && proxyReady
                && bastionReady
                && functionsWorkerReady;


        if (allReady) {
            log.info("all resources ready, setting cluster to ready state");
            return new ReconciliationResult(
                    false,
                    List.of(createReadyCondition(resource))
            );
        } else {
            List<String> notReady = new ArrayList<>();
            if (!autorecoveryReady) {
                notReady.add("autorecovery");
            }
            if (!brokerReady) {
                notReady.add("broker");
            }
            if (!proxyReady) {
                notReady.add("proxy");
            }
            if (!bastionReady) {
                notReady.add("bastion");
            }
            if (!functionsWorkerReady && brokerReady) {
                notReady.add("functionsworker");
            }

            log.infof("waiting for %s to become ready", notReady);

            return new ReconciliationResult(
                    true,
                    List.of(createNotReadyInitializingCondition(resource))
            );
        }

    }

    private void adjustBastionTarget(PulsarClusterSpec clusterSpec) {
        if (clusterSpec.getBastion() == null
                || clusterSpec.getBastion().getTargetProxy() == null) {
            boolean isProxyEnabled = clusterSpec.getProxy() != null
                    && clusterSpec.getProxy().getReplicas() > 0;
            if (clusterSpec.getBastion() == null) {
                clusterSpec.setBastion(BastionSpec.builder()
                        .targetProxy(isProxyEnabled)
                        .build()
                );
            } else {
                clusterSpec.getBastion().setTargetProxy(isProxyEnabled);
            }
        }
    }

    private void adjustProxyFunctionsWorkerDeployment(PulsarClusterSpec clusterSpec) {
        boolean isFunctionsWorkerStandaloneMode = clusterSpec.getFunctionsWorker() != null
                && clusterSpec.getFunctionsWorker().getReplicas() > 0;

        if (clusterSpec.getProxy() != null
                && isFunctionsWorkerStandaloneMode) {
            clusterSpec.getProxy().setStandaloneFunctionsWorker(true);
        }
    }

    private void adjustBrokerReplicas(Broker current, PulsarClusterSpec clusterSpec) {
        if (clusterSpec.getBroker() != null) {
            final LinkedHashMap<String, BrokerSetSpec> desiredBrokerSetSpecs =
                    BrokerController.getBrokerSetSpecs(clusterSpec.getBroker());
            final LinkedHashMap<String, BrokerSetSpec> currentBrokerSetSpecs =
                    BrokerController.getBrokerSetSpecs(current.getSpec().getBroker());
            for (Map.Entry<String, BrokerSetSpec> currentSet : currentBrokerSetSpecs.entrySet()) {
                final BrokerSetSpec desiredBrokerSetSpec = desiredBrokerSetSpecs.get(currentSet.getKey());
                if (desiredBrokerSetSpec != null
                        && desiredBrokerSetSpec.getAutoscaler() != null
                        && desiredBrokerSetSpec.getAutoscaler().getEnabled()) {
                    final BrokerSetSpec currentBrokerSetSpec = currentSet.getValue();
                    if (currentBrokerSetSpec.getReplicas() != null) {
                        final Integer currentReplicas = currentBrokerSetSpec.getReplicas();
                        // do not update replicas if patching, leave whatever the autoscaler have set
                        if (currentSet.getKey().equals(BrokerResourcesFactory.BROKER_DEFAULT_SET)) {
                            clusterSpec.getBroker().getDefaultBrokerSpecRef().setReplicas(currentReplicas);
                        } else {
                            clusterSpec.getBroker().getSets().get(currentSet.getKey()).setReplicas(currentReplicas);
                        }
                    }
                }
            }
        }
    }

    private void adjustBookKeeperReplicas(BookKeeper current, PulsarClusterSpec clusterSpec) {
        if (clusterSpec.getBookkeeper() != null) {
            final LinkedHashMap<String, BookKeeperSetSpec> desiredSpecs =
                    BookKeeperController.getBookKeeperSetSpecs(clusterSpec.getBookkeeper());
            final LinkedHashMap<String, BookKeeperSetSpec> currentSpecs =
                    BookKeeperController.getBookKeeperSetSpecs(current.getSpec().getBookkeeper());

            for (Map.Entry<String, BookKeeperSetSpec> currentSet : currentSpecs.entrySet()) {
                final BookKeeperSetSpec desiredSetSpec = desiredSpecs.get(currentSet.getKey());
                if (desiredSetSpec != null
                        && desiredSetSpec.getAutoscaler() != null
                        && desiredSetSpec.getAutoscaler().getEnabled()) {
                    final BookKeeperSetSpec currentSetSpec = currentSet.getValue();
                    if (currentSetSpec.getReplicas() != null) {
                        final Integer currentReplicas = currentSetSpec.getReplicas();
                        // do not update replicas if patching, leave whatever the autoscaler have set
                        clusterSpec.getBookkeeper().getBookKeeperSetSpecRef(currentSet.getKey())
                                .setReplicas(currentReplicas);
                    }
                }
            }
        }
    }

    private boolean checkReadyOrPatchZooKeeper(String currentNamespace, PulsarClusterSpec clusterSpec,
                                               List<OwnerReference> ownerReference) {
        return checkReadyOrPatch(
                CUSTOM_RESOURCE_ZOOKEEPER,
                ZooKeeper.class,
                ZooKeeperFullSpec.builder()
                        .global(clusterSpec.getGlobal())
                        .zookeeper(clusterSpec.getZookeeper())
                        .build(),
                currentNamespace,
                clusterSpec,
                ownerReference
        );
    }

    private boolean checkReadyOrPatchBookKeeper(String currentNamespace, PulsarClusterSpec clusterSpec,
                                                List<OwnerReference> ownerReference) {
        return checkReadyOrPatch(CUSTOM_RESOURCE_BOOKKEEPER,
                BookKeeper.class,
                BookKeeperFullSpec.builder()
                        .global(clusterSpec.getGlobal())
                        .bookkeeper(clusterSpec.getBookkeeper())
                        .build(),
                currentNamespace,
                clusterSpec,
                ownerReference
        );
    }

    private boolean checkReadyOrPatchAutorecovery(String currentNamespace, PulsarClusterSpec clusterSpec,
                                                  List<OwnerReference> ownerReference) {
        return checkReadyOrPatch(CUSTOM_RESOURCE_AUTORECOVERY,
                Autorecovery.class,
                AutorecoveryFullSpec.builder()
                        .global(clusterSpec.getGlobal())
                        .autorecovery(clusterSpec.getAutorecovery())
                        .build(),
                currentNamespace,
                clusterSpec,
                ownerReference
        );
    }

    private boolean checkReadyOrPatchBroker(String currentNamespace, PulsarClusterSpec clusterSpec,
                                            List<OwnerReference> ownerReference) {
        return checkReadyOrPatch(
                CUSTOM_RESOURCE_BROKER,
                Broker.class,
                BrokerFullSpec.builder()
                        .global(clusterSpec.getGlobal())
                        .broker(clusterSpec.getBroker())
                        .build(),
                currentNamespace,
                clusterSpec,
                ownerReference
        );
    }

    private boolean checkReadyOrPatchProxy(String currentNamespace, PulsarClusterSpec clusterSpec,
                                           List<OwnerReference> ownerReference) {
        return checkReadyOrPatch(CUSTOM_RESOURCE_PROXY,
                Proxy.class,
                ProxyFullSpec.builder()
                        .global(clusterSpec.getGlobal())
                        .proxy(clusterSpec.getProxy())
                        .build(),
                currentNamespace,
                clusterSpec,
                ownerReference
        );
    }

    private boolean checkReadyOrPatchBastion(String currentNamespace, PulsarClusterSpec clusterSpec,
                                             List<OwnerReference> ownerReference) {
        return checkReadyOrPatch(CUSTOM_RESOURCE_BASTION,
                Bastion.class,
                BastionFullSpec.builder()
                        .global(clusterSpec.getGlobal())
                        .bastion(clusterSpec.getBastion())
                        .build(),
                currentNamespace,
                clusterSpec,
                ownerReference
        );
    }

    private boolean checkReadyOrPatchFunctionsWorker(String currentNamespace, PulsarClusterSpec clusterSpec,
                                                     List<OwnerReference> ownerReference) {
        return checkReadyOrPatch(CUSTOM_RESOURCE_FUNCTIONS_WORKER,
                FunctionsWorker.class,
                FunctionsWorkerFullSpec.builder()
                        .global(clusterSpec.getGlobal())
                        .functionsWorker(clusterSpec.getFunctionsWorker())
                        .build(),
                currentNamespace,
                clusterSpec,
                ownerReference
        );
    }

    @SneakyThrows
    private <CR extends CustomResource<SPEC, ?>, SPEC> boolean checkReadyOrPatch(
            String customResourceName,
            Class<CR> resourceClass,
            SPEC spec,
            String namespace,
            PulsarClusterSpec clusterSpec,
            List<OwnerReference> ownerReferences) {
        final MixedOperation<CR, KubernetesResourceList<CR>, Resource<CR>> resourceClient =
                client.resources(resourceClass);
        if (resourceClient == null) {
            throw new IllegalStateException(customResourceName + " CRD not found");
        }

        final String crFullName = computeCustomResourceName(clusterSpec, customResourceName);
        final CR current = getExistingCustomResource(resourceClass, namespace, crFullName);
        if (current != null) {
            if (CUSTOM_RESOURCE_BOOKKEEPER.equals(customResourceName)) {
                adjustBookKeeperReplicas((BookKeeper) current, clusterSpec);
            }
            if (CUSTOM_RESOURCE_BROKER.equals(customResourceName)) {
                adjustBrokerReplicas((Broker) current, clusterSpec);
            }
            final SPEC currentSpec = current.getSpec();

            final String currentAsJson = SerializationUtil.writeAsJson(currentSpec);
            final String newSpecAsJson = SerializationUtil.writeAsJson(spec);
            final JSONComparator.Result diff = SpecDiffer.generateDiff(currentAsJson, newSpecAsJson);
            if (diff.areEquals()) {
                final BaseComponentStatus currentStatus = (BaseComponentStatus) current.getStatus();
                final Condition readyCondition = currentStatus.getConditions().stream()
                        .filter(c -> c.getType().equals(CRDConstants.CONDITIONS_TYPE_READY))
                        .findFirst()
                        .orElse(null);
                if (readyCondition != null && readyCondition.getStatus().equals(CRDConstants.CONDITIONS_STATUS_TRUE)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                log.infof("detected diff in %s, updating resource", customResourceName);
                SpecDiffer.logDetailedSpecDiff(diff, currentAsJson, newSpecAsJson);
            }
        }

        ObjectMeta meta = new ObjectMeta();
        meta.setName(crFullName);
        meta.setNamespace(namespace);
        meta.setOwnerReferences(ownerReferences);

        final CR resource = resourceClass.getConstructor().newInstance();
        resource.setMetadata(meta);
        resource.setSpec(spec);


        resourceClient
                .inNamespace(namespace)
                .resource(resource)
                .createOrReplace();
        log.infof("Patched custom resource %s with name %s ", customResourceName, crFullName);
        return false;
    }


    protected <CR extends CustomResource<SPEC, ?>, SPEC> CR getExistingCustomResource(
            Class<CR> resourceClass, String namespace,
            String crFullName) {
        return client.resources(resourceClass)
                .inNamespace(namespace)
                .withName(crFullName)
                .get();
    }


    @SneakyThrows
    private void generateSecretsIfAbsent(String namespace, PulsarClusterSpec clusterSpec) {
        final AuthConfig auth = clusterSpec.getGlobal().getAuth();
        if (auth == null) {
            return;
        }
        if (!auth.getEnabled()) {
            return;
        }
        getTokenAuthProvisioner(namespace).generateSecretsIfAbsent(auth.getToken());
    }

    @SneakyThrows
    private void setupTls(String namespace, PulsarClusterSpec clusterSpec) {
        final TlsConfig tls = clusterSpec.getGlobal().getTls();
        if (tls == null || !tls.getEnabled()) {
            return;
        }
        final TlsConfig.CertProvisionerConfig certProvisioner = tls.getCertProvisioner();
        if (certProvisioner == null
                || certProvisioner.getSelfSigned() == null
                || !certProvisioner.getSelfSigned().getEnabled()) {
            return;
        }
        new CertManagerCertificatesProvisioner(client, namespace, clusterSpec)
                .generateCertificates();
    }

    protected TokenAuthProvisioner getTokenAuthProvisioner(String namespace) {
        return new TokenAuthProvisioner(client, namespace);
    }

    void onStop(@Observes ShutdownEvent ev) {
        if (autoscaler != null) {
            autoscaler.close();
        }
    }
}
