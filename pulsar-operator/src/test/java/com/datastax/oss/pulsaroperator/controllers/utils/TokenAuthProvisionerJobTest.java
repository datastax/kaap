package com.datastax.oss.pulsaroperator.controllers.utils;

import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import com.datastax.oss.pulsaroperator.crds.SerializationUtil;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TokenAuthProvisionerJobTest {
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
            String yamlBody;
        }

        List<MockServer.Op> ops;

        private PulsarClusterSpec pulsarClusterSpec;
        private MockServer.CurrentJobState currentJobState;
        private String existingJobChecksum;
        KubernetesServer server;

        @SneakyThrows
        void start() {
            ops = new ArrayList<>();
            Objects.requireNonNull(currentJobState);
            pulsarClusterSpec.getGlobal().applyDefaults(null);


            final String clusterName = pulsarClusterSpec.getGlobal().getName();

            server = new KubernetesServer(false);
            server.before();

            server.expect()
                    .post()
                    .withPath("/apis/batch/v1/namespaces/ns/jobs")
                    .andReply(HttpURLConnection.HTTP_OK, recordedRequest -> {
                        final String body = recordedRequest.getBody().readUtf8();
                        MockServer.this.ops.add(new Op("POST", SerializationUtil.writeAsYaml(
                                SerializationUtil.readJson(body, Map.class))));
                        return null;
                    })
                    .once();

            server.expect()
                    .delete()
                    .withPath("/apis/batch/v1/namespaces/ns/jobs/%s-token-auth-provisioner".formatted(clusterName))
                    .andReply(HttpURLConnection.HTTP_OK, recordedRequest -> {
                        MockServer.this.ops.add(new MockServer.Op("DELETE", null));
                        return null;
                    })
                    .once();


            final Job currentJob;
            if (currentJobState == MockServer.CurrentJobState.NotExists) {
                currentJob = null;
            } else {
                currentJob = new JobBuilder()
                        .withNewMetadata()
                        .withName("%s-token-auth-provisioner".formatted(clusterName))
                        .withAnnotations(Map.of(
                                "%s/last-applied".formatted(CRDConstants.GROUP),
                                existingJobChecksum == null ? "" : existingJobChecksum
                        ))
                        .endMetadata()
                        .withNewStatus()
                        .withSucceeded(currentJobState == MockServer.CurrentJobState.Running ? 0 : 1)
                        .endStatus()
                        .build();
            }

            server.expect()
                    .get()
                    .withPath("/apis/batch/v1/namespaces/ns/jobs/%s-token-auth-provisioner".formatted(clusterName))
                    .andReturn(HttpURLConnection.HTTP_OK, currentJob)
                    .always();
        }

        @Override
        public void close() {
            server.after();
        }
    }

    @Test
    public void testDisabled() throws Exception {
        final String spec = """
                global:
                   name: pul
                   image: apachepulsar/pulsar:global
                   auth:
                     enabled: true
                     token:
                        provisioner:
                            initialize: false
                """;

        final MockServer server = invokeJobCreate(spec, MockServer.CurrentJobState.NotExists);
        Assert.assertEquals(server.ops.size(), 0);
    }

    @Test
    public void testCreate() throws Exception {
        final String spec = """
                global:
                   name: pul
                   image: apachepulsar/pulsar:global
                   auth:
                     enabled: true
                     token:
                        provisioner:
                            rbac:
                                create: false
                            initialize: true
                """;

        final MockServer server = invokeJobCreate(spec, MockServer.CurrentJobState.NotExists);
        Assert.assertEquals(server.ops.size(), 1);
        Assert.assertEquals(server.ops.get(0).method, "POST");
        Assert.assertEquals(server.ops.get(0).getYamlBody(),
                """
                        ---
                        apiVersion: batch/v1
                        kind: Job
                        metadata:
                          annotations:
                            com.datastax.oss/last-applied: 784a03cd964e0ff1d96e0111b90f22aa786c3aa62ed26dbe14b096269db730e3
                          labels:
                            app: pul
                            cluster: pul
                            component: token-auth-provisioner
                          name: pul-token-auth-provisioner
                          namespace: ns
                        spec:
                          template:
                            spec:
                              containers:
                              - env:
                                - name: ClusterName
                                  value: pul
                                - name: SuperRoles
                                  value: "superuser,admin,websocket,proxy"
                                - name: ProcessMode
                                  value: init
                                - name: PulsarNamespace
                                  value: ns
                                - name: PrivateKeySecretName
                                  value: my-private.key
                                - name: PublicKeySecretName
                                  value: my-public.key
                                image: datastax/burnell:latest
                                imagePullPolicy: IfNotPresent
                                name: pul-token-auth-provisioner
                              restartPolicy: OnFailure
                              serviceAccountName: pul-burnell
                        """);
    }


    @Test
    public void testKeepIfSpecNotChanged() throws Exception {
        final String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                    auth:
                      enabled: true
                      token:
                         provisioner:
                             rbac:
                                 create: false
                             initialize: true
                 """;

        final MockServer server = invokeJobCreate(spec,
                MockServer.CurrentJobState.Running,
                "784a03cd964e0ff1d96e0111b90f22aa786c3aa62ed26dbe14b096269db730e3");
        Assert.assertEquals(server.ops.size(), 0);
    }

    @Test
    public void testRecreateIfSpecChanged() throws Exception {
        final String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                    auth:
                      enabled: true
                      token:
                         provisioner:
                             image: fixed-image:latest
                             rbac:
                                 create: false
                             initialize: true
                 """;

        final MockServer server = invokeJobCreate(spec,
                MockServer.CurrentJobState.Running,
                "784a03cd964e0ff1d96e0111b90f22aa786c3aa62ed26dbe14b096269db730e3");
        Assert.assertEquals(server.ops.size(), 2);
        Assert.assertEquals(server.ops.get(0).method, "DELETE");
        Assert.assertEquals(server.ops.get(1).method, "POST");
    }


    @Test
    public void testDoNotRecreateIfCompleted() throws Exception {
        final String spec = """
                global:
                    name: pul
                    image: apachepulsar/pulsar:global
                    auth:
                      enabled: true
                      token:
                         provisioner:
                             rbac:
                                 create: false
                             initialize: true
                 """;

        final MockServer server = invokeJobCreate(spec, MockServer.CurrentJobState.Completed);
        Assert.assertEquals(server.ops.size(), 0);
    }

    private MockServer invokeJobCreate(String spec, MockServer.CurrentJobState state) {
        return invokeJobCreate(spec, state, null);
    }

    private MockServer invokeJobCreate(String spec, MockServer.CurrentJobState state, String jobChecksum) {

        final PulsarClusterSpec clusterSpec = MockKubernetesClient.readYaml(spec, PulsarClusterSpec.class);

        try (final MockServer server = MockServer.builder()
                .withPulsarClusterSpec(clusterSpec)
                .withCurrentJobState(state)
                .withExistingJobChecksum(jobChecksum)
                .build();) {
            server.start();

            final TokenAuthProvisionerResourcesFactory factory =
                    new TokenAuthProvisionerResourcesFactory(server.server.getClient(), NAMESPACE,
                            clusterSpec.getGlobal().getAuth().getToken(),
                            clusterSpec.getGlobal(),
                            null);
            factory.patchJobAndCheckCompleted();
            return server;
        }
    }

}