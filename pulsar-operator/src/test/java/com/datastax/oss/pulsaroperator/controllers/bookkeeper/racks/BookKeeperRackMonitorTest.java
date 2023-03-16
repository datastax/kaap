package com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks;

import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks.client.BkRackClient;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.RackConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ResourceSetConfig;
import com.datastax.oss.pulsaroperator.mocks.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.mocks.MockResourcesResolver;
import io.fabric8.kubernetes.api.model.PodBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BookKeeperRackMonitorTest {

    @Test
    public void testRunDefaultRack() {

        final String namespace = "ns";
        final MockResourcesResolver resourcesResolver = new MockResourcesResolver();
        resourcesResolver.putResource("pulsar-bookkeeper-0", new PodBuilder()
                .withNewMetadata()
                .withLabels(
                        Map.of(
                                CRDConstants.LABEL_COMPONENT, "bookkeeper",
                                CRDConstants.LABEL_CLUSTER, "pulsar",
                                CRDConstants.LABEL_RESOURCESET, "bookkeeper"
                        ))
                .endMetadata()
                .withNewSpec()
                .withHostname("pulsar-bookkeeper-0")
                .endSpec()
                .build()
        );
        MockKubernetesClient client = new MockKubernetesClient(namespace, resourcesResolver);
        final GlobalSpec build = GlobalSpec.builder()
                .name("pulsar")
                .racks(Map.of("rack1", RackConfig.builder().build()))
                .resourceSets(Map.of("bookkeeper", ResourceSetConfig.builder().rack("rack1").build()))
                .build();
        build.applyDefaults(null);
        final PulsarClusterSpec clusterSpec = PulsarClusterSpec.builder()
                .global(build)
                .build();
        clusterSpec.applyDefaults(build);
        clusterSpec.getBookkeeper().setReplicas(2);
        final MockBkRackClient bkRackClient = new MockBkRackClient();

        new BookKeeperRackMonitor(client.getClient(), namespace,
                new BookKeeperFullSpec(clusterSpec.getGlobal(), clusterSpec.getBookkeeper()),
                bkRackClient).internalRun();
        final var update = bkRackClient.getUpdate();
        Assert.assertEquals(update, Map.of("defaultgroup", Map.of(
                "pulsar-bookkeeper-0.pulsar-bookkeeper.ns.svc.cluster.local:3181",
                BkRackClient.BookieRackInfo.builder()
                        .rack("rack1/unknown-node")
                        .hostname("pulsar-bookkeeper-0.pulsar-bookkeeper.ns.svc.cluster.local:3181")
                        .build(),
                "pulsar-bookkeeper-1.pulsar-bookkeeper.ns.svc.cluster.local:3181",
                BkRackClient.BookieRackInfo.builder()
                        .rack("rack1/unknown-node")
                        .hostname("pulsar-bookkeeper-1.pulsar-bookkeeper.ns.svc.cluster.local:3181")
                        .build())
        ));
    }

    @Test
    public void testRunWithRacks() {

        final String namespace = "ns";
        final MockResourcesResolver resourcesResolver = new MockResourcesResolver();
        resourcesResolver.putResource("pulsar-bookkeeper-bk1-0", new PodBuilder()
                .withNewMetadata()
                .withLabels(
                        Map.of(
                                CRDConstants.LABEL_COMPONENT, "bookkeeper",
                                CRDConstants.LABEL_CLUSTER, "pulsar",
                                CRDConstants.LABEL_RESOURCESET, "bookkeeper"
                        ))
                .endMetadata()
                .withNewSpec()
                .withHostname("pulsar-bookkeeper1-0")
                .withNodeName("node1")
                .endSpec()
                .build()
        );
        MockKubernetesClient client = new MockKubernetesClient(namespace, resourcesResolver);
        final GlobalSpec build = GlobalSpec.builder()
                .name("pulsar")
                .racks(Map.of("rack1", RackConfig.builder().build()))
                .racks(Map.of("rack2", RackConfig.builder().build()))
                .resourceSets(Map.of("bk1", ResourceSetConfig.builder().rack("rack1").build(),
                        "bk2", ResourceSetConfig.builder().rack("rack2").build(),
                        "bk3", ResourceSetConfig.builder().build()))
                .build();
        build.applyDefaults(null);
        final PulsarClusterSpec clusterSpec = PulsarClusterSpec.builder()
                .global(build)
                .build();
        clusterSpec.applyDefaults(build);
        clusterSpec.getBookkeeper().setSets(new LinkedHashMap<>());
        clusterSpec.getBookkeeper().getSets().put("bk1", BookKeeperSetSpec.builder().replicas(2).build());
        clusterSpec.getBookkeeper().getSets().put("bk2", BookKeeperSetSpec.builder().replicas(3).build());
        clusterSpec.getBookkeeper().getSets().put("bk3", BookKeeperSetSpec.builder().replicas(5).build());
        final MockBkRackClient bkRackClient = new MockBkRackClient();

        new BookKeeperRackMonitor(client.getClient(), namespace,
                new BookKeeperFullSpec(clusterSpec.getGlobal(), clusterSpec.getBookkeeper()),
                bkRackClient).internalRun();
        final var update = bkRackClient.getUpdate();
        Assert.assertEquals(SerializationUtil.writeAsYaml(update), """
                ---
                defaultgroup:
                  pulsar-bookkeeper-bk1-0.pulsar-bookkeeper-bk1.ns.svc.cluster.local:3181:
                    rack: rack1/node1
                    hostname: pulsar-bookkeeper-bk1-0.pulsar-bookkeeper-bk1.ns.svc.cluster.local:3181
                  pulsar-bookkeeper-bk1-1.pulsar-bookkeeper-bk1.ns.svc.cluster.local:3181:
                    rack: rack1/unknown-node
                    hostname: pulsar-bookkeeper-bk1-1.pulsar-bookkeeper-bk1.ns.svc.cluster.local:3181
                  pulsar-bookkeeper-bk2-0.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181:
                    rack: rack2/unknown-node
                    hostname: pulsar-bookkeeper-bk2-0.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181
                  pulsar-bookkeeper-bk2-1.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181:
                    rack: rack2/unknown-node
                    hostname: pulsar-bookkeeper-bk2-1.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181
                  pulsar-bookkeeper-bk2-2.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181:
                    rack: rack2/unknown-node
                    hostname: pulsar-bookkeeper-bk2-2.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181
                """);
    }

    private static class MockBkRackClient implements BkRackClient {
        @Getter
        private BookiesRackConfiguration update;

        @Override
        public BookiesRackOp newBookiesRackOp() {
            return new BookiesRackOp() {
                @Override
                public BookiesRackConfiguration get() {
                    return new BookiesRackConfiguration();
                }

                @Override
                public void update(BookiesRackConfiguration newConfig) {
                    update = newConfig;
                }
            };
        }

        @Override
        public void close() throws Exception {

        }
    }
}