package com.datastax.oss.pulsaroperator.controllers.zookeeper;

import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.testng.Assert;
import org.testng.annotations.Test;

@JBossLog
public class ZooKeeperJobTest {
    private static final String NAMESPACE = "ns";


    @Builder(setterPrefix = "with")
    public static class MockServer implements AutoCloseable {

        enum CurrentJobState {
            NotExists,
            Running,
            Completed
        }
        @Data
        @AllArgsConstructor
        public static class Op {
            String method;
        }

        List<Op> ops;

        private PulsarClusterSpec pulsarClusterSpec;
        private CurrentJobState currentJobState;
        KubernetesServer server;

        @SneakyThrows
        void start() {
            ops = new ArrayList<>();
            Objects.requireNonNull(currentJobState);
            pulsarClusterSpec.getGlobal().applyDefaults(null);
            pulsarClusterSpec.getZookeeper().applyDefaults(pulsarClusterSpec.getGlobalSpec());


            final String clusterName = pulsarClusterSpec.getGlobal().getName();

            server = new KubernetesServer(false);
            server.before();

            server.expect()
                    .post()
                    .withPath("/apis/batch/v1/namespaces/ns/jobs")
                    .andReply(HttpURLConnection.HTTP_OK, recordedRequest -> {
                        MockServer.this.ops.add(new Op("POST"));
                        return null;
                    })
                    .once();

            server.expect()
                    .delete()
                    .withPath("/apis/batch/v1/namespaces/ns/jobs/%s-zookeeper".formatted(clusterName))
                    .andReply(HttpURLConnection.HTTP_OK, recordedRequest -> {
                        MockServer.this.ops.add(new Op("DELETE"));
                        return null;
                    })
                    .once();



            final Job currentJob;
            if (currentJobState == CurrentJobState.NotExists) {
                currentJob = null;
            } else {
                currentJob = new JobBuilder()
                        .withNewMetadata()
                        .withName("%s-zookeeper".formatted(clusterName))
                        .endMetadata()
                        .withNewStatus()
                        .withSucceeded(currentJobState == CurrentJobState.Running ? 0 : 1)
                        .endStatus()
                        .build();
            }

            server.expect()
                    .get()
                    .withPath("/apis/batch/v1/namespaces/ns/jobs/%s-zookeeper".formatted(clusterName))
                    .andReturn(HttpURLConnection.HTTP_OK, currentJob)
                    .always();
        }

        @Override
        public void close() {
            server.after();
        }
    }

    @Test
    public void testCreate() throws Exception {
        final String spec = """
                global:
                   name: pul
                zookeeper:
                    image: apachepulsar/pulsar:global
                """;

        final MockServer server = invokeJobCreate(spec, MockServer.CurrentJobState.NotExists);
        Assert.assertEquals(server.ops.size(), 1);
        Assert.assertEquals(server.ops.get(0).method, "POST");
    }


    @Test
    public void testRecreateIfRunning() throws Exception {
        final String spec = """
                global:
                   name: pul
                zookeeper:
                    image: apachepulsar/pulsar:global
                """;

        final MockServer server = invokeJobCreate(spec, MockServer.CurrentJobState.Running);
        Assert.assertEquals(server.ops.size(), 2);
        Assert.assertEquals(server.ops.get(0).method, "DELETE");
        Assert.assertEquals(server.ops.get(1).method, "POST");
    }


    @Test
    public void testDoNotRecreateIfCompleted() throws Exception {
        final String spec = """
                global:
                   name: pul
                zookeeper:
                    image: apachepulsar/pulsar:global
                """;

        final MockServer server = invokeJobCreate(spec, MockServer.CurrentJobState.Completed);
        Assert.assertEquals(server.ops.size(), 0);
    }

    private MockServer invokeJobCreate(String spec, MockServer.CurrentJobState state) {

        final PulsarClusterSpec clusterSpec = MockKubernetesClient.readYaml(spec, PulsarClusterSpec.class);

        try (final MockServer server = MockServer.builder()
                .withPulsarClusterSpec(clusterSpec)
                .withCurrentJobState(state)
                .build();) {
            server.start();

            final ZooKeeperResourcesFactory factory =
                    new ZooKeeperResourcesFactory(server.server.getClient(), NAMESPACE,
                            clusterSpec.getZookeeper(),
                            clusterSpec.getGlobal(),
                            null);
            factory.createMetadataInitializationJobIfNeeded();
            return server;
        }
    }
}