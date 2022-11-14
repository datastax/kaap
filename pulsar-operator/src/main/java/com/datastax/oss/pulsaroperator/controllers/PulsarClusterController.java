package com.datastax.oss.pulsaroperator.controllers;

import com.datastax.oss.pulsaroperator.controllers.autoscaler.AutoscalerDaemon;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeper;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crds.broker.Broker;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerFullSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperFullSpec;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.quarkus.runtime.ShutdownEvent;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-cluster-app")
@JBossLog
@ApplicationScoped
public class PulsarClusterController extends AbstractController<PulsarCluster> {

    private final AutoscalerDaemon autoscaler;

    public PulsarClusterController(KubernetesClient client) {
        super(client);
        autoscaler = new AutoscalerDaemon(client);
    }

    @Override
    protected void createResources(PulsarCluster resource, Context<PulsarCluster> context)
            throws Exception {

        final String currentNamespace = resource.getMetadata().getNamespace();
        final PulsarClusterSpec clusterSpec = resource.getSpec();

        final List<OwnerReference> ownerReference = List.of(getOwnerReference(resource));

        createCustomResource(
                "zookeeper",
                ZooKeeper.class,
                ZooKeeperFullSpec.builder()
                        .global(clusterSpec.getGlobal())
                        .zookeeper(clusterSpec.getZookeeper())
                        .build(),
                currentNamespace,
                clusterSpec,
                ownerReference
        );

        createCustomResource(
                "bookkeeper",
                BookKeeper.class,
                BookKeeperFullSpec.builder()
                        .global(clusterSpec.getGlobal())
                        .bookkeeper(clusterSpec.getBookkeeper())
                        .build(),
                currentNamespace,
                clusterSpec,
                ownerReference
        );
        createCustomResource(
                "broker",
                Broker.class,
                BrokerFullSpec.builder()
                        .global(clusterSpec.getGlobal())
                        .broker(clusterSpec.getBroker())
                        .build(),
                currentNamespace,
                clusterSpec,
                ownerReference
        );

        if (clusterSpec.getAutoscaler() != null) {
            clusterSpec.getAutoscaler().applyDefaults(clusterSpec.getGlobalSpec());
        }
        autoscaler.onSpecChange(clusterSpec, currentNamespace);
    }

    @SneakyThrows
    private <CR extends CustomResource<SPEC, ?>, SPEC> void createCustomResource(
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

        ObjectMeta meta = new ObjectMeta();
        final String crFullName = "%s-%s".formatted(clusterSpec.getGlobal().getName(), customResourceName);
        meta.setName(crFullName);
        meta.setNamespace(namespace);
        meta.setOwnerReferences(ownerReferences);
        final CR cr = resourceClass.getConstructor().newInstance();
        cr.setMetadata(meta);
        cr.setSpec(spec);
        resourceClient
                .inNamespace(namespace)
                .resource(cr)
                .createOrReplace();
        log.infof("Patched custom resource %s with name %s ", customResourceName, crFullName);
    }

    void onStop(@Observes ShutdownEvent ev) {
        if (autoscaler != null) {
            autoscaler.close();
        }
    }
}

