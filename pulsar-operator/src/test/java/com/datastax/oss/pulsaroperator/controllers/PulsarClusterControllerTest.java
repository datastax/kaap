package com.datastax.oss.pulsaroperator.controllers;

import static org.mockito.Mockito.mock;
import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeper;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.Test;

@JBossLog
public class PulsarClusterControllerTest {

    static final String NAMESPACE = "ns";

    @Test
    public void testDefaults() throws Exception {
        String spec = """
                global:
                    name: pulsarname
                    image: apachepulsar/pulsar:2.10.2
                """;
        final MockKubernetesClient client = invokeController(spec);

        final MockKubernetesClient.ResourceInteraction<ZooKeeper> zk = client
                .getCreatedResource(ZooKeeper.class);
        Assert.assertEquals("""
                ---
                apiVersion: com.datastax.oss/v1alpha1
                kind: ZooKeeper
                metadata:
                  name: pulsarname-zookeeeper
                  namespace: ns
                  ownerReferences:
                  - apiVersion: com.datastax.oss/v1alpha1
                    kind: PulsarCluster
                    blockOwnerDeletion: true
                    controller: true
                    name: pulsar-cluster
                spec:
                  global:
                    name: pulsarname
                    kubernetesClusterDomain: cluster.local
                    persistence: true
                    image: apachepulsar/pulsar:2.10.2
                    imagePullPolicy: IfNotPresent
                    storage:
                      existingStorageClassName: default
                status: {}
                """, zk.getResourceYaml());

    }

    @SneakyThrows
    private void invokeControllerAndAssertError(String spec, String expectedErrorMessage) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<PulsarCluster> result = invokeController(mockKubernetesClient, spec);
        Assert.assertTrue(result.isUpdateStatus());
        Assert.assertEquals(result.getResource().getStatus().getError(),
                expectedErrorMessage);
    }

    @SneakyThrows
    private MockKubernetesClient invokeController(String spec) {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        final UpdateControl<PulsarCluster>
                result = invokeController(mockKubernetesClient, spec);
        Assert.assertTrue(result.isUpdateStatus());
        return mockKubernetesClient;
    }

    private UpdateControl<PulsarCluster> invokeController(MockKubernetesClient mockKubernetesClient, String spec)
            throws Exception {
        final PulsarClusterController controller = new PulsarClusterController(mockKubernetesClient.getClient());

        final PulsarCluster cluster = new PulsarCluster();
        ObjectMeta meta = new ObjectMeta();
        meta.setName("pulsar-cluster");
        meta.setNamespace(NAMESPACE);
        cluster.setMetadata(meta);

        final PulsarClusterSpec clusterSpec = MockKubernetesClient.readYaml(spec, PulsarClusterSpec.class);
        cluster.setSpec(clusterSpec);

        final UpdateControl<PulsarCluster> result = controller.reconcile(cluster, mock(Context.class));
        return result;
    }
}