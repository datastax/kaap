package com.datastax.oss.pulsaroperator.reconcilier;


import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crd.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeper;
import com.datastax.oss.pulsaroperator.crd.zookeeper.ZooKeeperSpec;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.extern.jbosslog.JBossLog;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

@JBossLog
public class ZooKeeperReconcilierTest {

    static final String NAMESPACE = "ns";
    static final String CLUSTER_FULLNAME = "pulsar-fullname";
    static final String ZK_BASE_NAME = CLUSTER_FULLNAME + "-zookeeper";

    @Test
    public void testReconcile() throws Exception {
        final MockKubernetesClient client = new MockKubernetesClient("ns");
        final ZooKeeperReconcilier reconcilier = new ZooKeeperReconcilier(client.getClient());

        final ZooKeeper zooKeeper = new ZooKeeper();
        ObjectMeta meta = new ObjectMeta();
        meta.setName(CLUSTER_FULLNAME + "-cr");
        meta.setNamespace(NAMESPACE);
        zooKeeper.setMetadata(meta);
        zooKeeper.setSpec(ZooKeeperSpec.builder()
                        .clusterSpec(PulsarClusterSpec.ClusterSpec.builder()
                                .fullname(CLUSTER_FULLNAME)
                                .name("mypulsar")
                                .persistence(true)
                                .enableTls(false)
                                .build())
                .config(Map.of("zkconfig", "zkconfigvalue"))
                .annotations(Map.of("ann1", "ann1value"))
                .containerImage("apachepulsar/pulsar:2.10.2")
                .dataVolume(ZooKeeperSpec.VolumeConfig.builder()
                        .name("volume-name")
                        .size("1g")
                        .storageClass("default")
                        .build())
                .gracePeriod(60)
                .nodeSelectors(Map.of("selector1", "zk"))
                .probe(ZooKeeperSpec.ProbeConfig.builder()
                        .enabled(true)
                        .period(60)
                        .initial(50)
                        .timeout(30)
                        .build())
                .imagePullPolicy("IfNotPresent")
                .podManagementPolicy("Parallel")
                .service(ZooKeeperSpec.ServiceConfig.builder().
                        ports(List.of(new ServicePortBuilder()
                                .withName("port1")
                                .withPort(2181)
                                .build())).build())

                .build());

        final UpdateControl<ZooKeeper> result = reconcilier.reconcile(zooKeeper, mock(Context.class));
        Assert.assertTrue(result.isUpdateStatus());

        final MockKubernetesClient.ResourceInteraction configMap = client.getCreatedResources().get(0);
        Assert.assertEquals(configMap.getResource().getFullResourceName(), "configmaps");
        Assert.assertEquals(configMap.getResource().getMetadata().getName(), ZK_BASE_NAME);
        Mockito.verify(configMap.getInteraction()).createOrReplace();

    }

}