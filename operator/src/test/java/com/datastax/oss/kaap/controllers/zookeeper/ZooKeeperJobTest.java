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
package com.datastax.oss.kaap.controllers.zookeeper;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@JBossLog
@EnableKubernetesMockClient(https = false)
public class ZooKeeperJobTest {
    private static final String NAMESPACE = "ns";

    KubernetesMockServer server;
    KubernetesClient client;

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

    private final List<Op> ops = new ArrayList<>();

    @SneakyThrows
    private void setupMocks(
            PulsarClusterSpec pulsarClusterSpec,
            CurrentJobState currentJobState) {

        ops.clear();
        pulsarClusterSpec.getGlobal().applyDefaults(null);
        pulsarClusterSpec.getZookeeper().applyDefaults(pulsarClusterSpec.getGlobalSpec());


        final String clusterSpecName = pulsarClusterSpec.getGlobal().getName();

        server.expect()
                .post()
                .withPath("/apis/batch/v1/namespaces/ns/jobs")
                .andReply(HttpURLConnection.HTTP_OK, recordedRequest -> {
                    ZooKeeperJobTest.this.ops.add(new Op("POST"));
                    return null;
                })
                .once();

        server.expect()
                .delete()
                .withPath("/apis/batch/v1/namespaces/ns/jobs/%s-zookeeper-metadata".formatted(clusterSpecName))
                .andReply(HttpURLConnection.HTTP_OK, recordedRequest -> {
                    ZooKeeperJobTest.this.ops.add(new Op("DELETE"));
                    return null;
                })
                .once();


        final Job currentJob;
        if (currentJobState == CurrentJobState.NotExists) {
            currentJob = null;
        } else {
            currentJob = new JobBuilder()
                    .withNewMetadata()
                    .withName("%s-zookeeper-metadata".formatted(clusterSpecName))
                    .endMetadata()
                    .withNewStatus()
                    .withSucceeded(currentJobState == CurrentJobState.Running ? 0 : 1)
                    .endStatus()
                    .build();
        }

        server.expect()
                .get()
                .withPath("/apis/batch/v1/namespaces/ns/jobs/%s-zookeeper-metadata".formatted(clusterSpecName))
                .andReturn(currentJob == null ? HttpURLConnection.HTTP_NOT_FOUND : HttpURLConnection.HTTP_OK, currentJob)
                .always();
    }


    @Test
    public void testCreate() throws Exception {
        final String spec = """
                global:
                   name: pul
                zookeeper:
                    image: apachepulsar/pulsar:global
                """;

        invokeJobCreate(spec, CurrentJobState.NotExists);
        Assertions.assertEquals(ops.size(), 1);
        Assertions.assertEquals(ops.get(0).method, "POST");
    }


    @Test
    public void testRecreateIfRunning() throws Exception {
        final String spec = """
                global:
                   name: pul
                zookeeper:
                    image: apachepulsar/pulsar:global
                """;

        invokeJobCreate(spec, CurrentJobState.Running);
        Assertions.assertEquals(ops.size(), 2);
        Assertions.assertEquals(ops.get(0).method, "DELETE");
        Assertions.assertEquals(ops.get(1).method, "POST");
    }


    @Test
    public void testDoNotRecreateIfCompleted() throws Exception {
        final String spec = """
                global:
                   name: pul
                zookeeper:
                    image: apachepulsar/pulsar:global
                """;

        invokeJobCreate(spec, CurrentJobState.Completed);
        Assertions.assertEquals(ops.size(), 0);
    }

    private void invokeJobCreate(String spec, CurrentJobState state) {

        final PulsarClusterSpec clusterSpec = SerializationUtil.readYaml(spec, PulsarClusterSpec.class);

        setupMocks(clusterSpec, state);

        final ZooKeeperResourcesFactory factory =
                new ZooKeeperResourcesFactory(client, NAMESPACE,
                        clusterSpec.getZookeeper(),
                        clusterSpec.getGlobal(),
                        null);
        factory.createMetadataInitializationJobIfNeeded();
    }
}