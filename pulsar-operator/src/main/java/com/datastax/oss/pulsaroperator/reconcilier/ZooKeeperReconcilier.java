package com.datastax.oss.pulsaroperator.reconcilier;

import com.datastax.oss.pulsaroperator.crd.GlobalSpec;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeperSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvFromSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorRequirementBuilder;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.PodAntiAffinityBuilder;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-zk-app")
@JBossLog
public class ZooKeeperReconcilier implements Reconciler<ZooKeeper> {

    private final KubernetesClient client;

    @SneakyThrows
    public ZooKeeperReconcilier(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<ZooKeeper> reconcile(ZooKeeper resource, Context context) {
        final String namespace = resource.getMetadata().getNamespace();
        final ZooKeeperFullSpec spec = resource.getSpec();

        log.infof("Zookeeper reconcilier, new spec %s, current spec %s", spec,
                resource.getStatus().getCurrentSpec());
        // TODO: handle immutable fields with proper methods (replicas for example)
        if (resource.getStatus().getCurrentSpec() != null) {
            client.apps().statefulSets().withName(spec.getGlobal().getFullname() + "-" + spec
                            .getZookeeper().getComponent())
                    .delete();
            log.info("Old stateful set deleted");
        }

        final ZooKeeperResourcesController controller = new ZooKeeperResourcesController(
                client, namespace, spec.getZookeeper(), spec.getGlobal());
        controller.createConfigMap();
        controller.createStatefulSet();
        controller.createService();
        controller.createCaService();

        resource.getStatus().setCurrentSpec(spec);
        // statefulSet
        // service
        // storageClass
        // pdb
        // metadata init job
        // config-map

        return UpdateControl.updateStatus(resource);
    }

}

