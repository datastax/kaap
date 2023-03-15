package com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks;

import com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks.client.BkRackClient;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.RackConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ResourceSetConfig;
import com.datastax.oss.pulsaroperator.mocks.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.mocks.MockResourcesResolver;
import io.fabric8.kubernetes.api.model.PodBuilder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BookKeeperRackMonitorTest {


    @Test
    public void testRun() {

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
        final MockBkRackClient bkRackClient = new MockBkRackClient();

        new BookKeeperRackMonitor(client.getClient(), namespace,
                new BookKeeperFullSpec(clusterSpec.getGlobal(), clusterSpec.getBookkeeper()),
                bkRackClient).internalRun();
        final List<Pair<String, BkRackClient.BookieRackInfo>> update = bkRackClient.getUpdate();
        Assert.assertEquals(update, List.of(
                Pair.of("pulsar-bookkeeper-0.pulsar-bookkeeper.ns.svc.cluster.local:3181",
                        BkRackClient.BookieRackInfo.builder()
                                .rack("rack1/pulsar-bookkeeper-0.pulsar-bookkeeper.ns.svc.cluster.local:3181")
                                .hostname("pulsar-bookkeeper-0.pulsar-bookkeeper.ns.svc.cluster.local:3181")
                                .build())
        ));
    }

    private static class MockBkRackClient implements BkRackClient {
        @Getter
        private List<Pair<String, BookieRackInfo>> update;

        @Override
        public Map<String, BookieRackInfo> getBookiesRackInfo() {
            return Collections.emptyMap();
        }

        @Override
        public void updateBookiesRackInfo(List<Pair<String, BookieRackInfo>> infos) {
            update = infos;
        }

        @Override
        public void close() throws Exception {

        }
    }
}