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
package com.datastax.oss.k8saap.controllers.bookkeeper.racks;

import com.datastax.oss.k8saap.common.SerializationUtil;
import com.datastax.oss.k8saap.controllers.bookkeeper.racks.client.ZkClientRackClientFactory;
import com.datastax.oss.k8saap.crds.CRDConstants;
import com.datastax.oss.k8saap.crds.GlobalSpec;
import com.datastax.oss.k8saap.crds.bookkeeper.BookKeeperAutoRackConfig;
import com.datastax.oss.k8saap.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.k8saap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.k8saap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.k8saap.crds.configs.RackConfig;
import com.datastax.oss.k8saap.crds.configs.ResourceSetConfig;
import com.datastax.oss.k8saap.mocks.MockKubernetesClient;
import com.datastax.oss.k8saap.mocks.MockResourcesResolver;
import io.fabric8.kubernetes.api.model.PodBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.awaitility.Awaitility;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class BookKeeperRackMonitorZkTest {

    TestingServer zkServerMain;
    CuratorFramework zkClient;

    @BeforeMethod
    @SneakyThrows
    public void before() {

        zkServerMain = new TestingServer(-1, new File("target", "curator"), true);
        zkClient =
                CuratorFrameworkFactory.newClient(zkServerMain.getConnectString(), new RetryOneTime(1000));
        zkClient.start();
        zkClient.create().forPath("/bookies", "{}".getBytes(StandardCharsets.UTF_8));
    }

    @AfterMethod
    @SneakyThrows
    public void after() {
        if (zkClient != null) {
            zkClient.close();
        }
        if (zkServerMain != null) {
            zkServerMain.close();
        }

    }

    @Test
    public void testRunDefaultRack() throws Exception {

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
        clusterSpec.getBookkeeper().setAutoRackConfig(BookKeeperAutoRackConfig.builder()
                .enabled(true)
                .periodMs(100L)
                .build());

        final BookKeeperRackDaemon bookKeeperRackDaemon = new BookKeeperRackDaemon(client.getClient(),
                new ZkClientRackClientFactory(client.getClient()) {
                    @Override
                    protected String getZkServers(String namespace, BookKeeperFullSpec newSpec) {
                        return zkServerMain.getConnectString();
                    }
                });
        bookKeeperRackDaemon.onSpecChange(clusterSpec, namespace);


        Awaitility.await().untilAsserted(() -> {
            Assert.assertNotNull(zkClient.checkExists().forPath("/bookies"));
            final byte[] bytes = zkClient.getData().forPath("/bookies");
            String s = new String(bytes, StandardCharsets.UTF_8);
            s = SerializationUtil.writeAsYaml(SerializationUtil.readJson(s, Map.class));
            Assert.assertEquals(s, """
                    ---
                    defaultgroup:
                      pulsar-bookkeeper-0.pulsar-bookkeeper.ns.svc.cluster.local:3181:
                        hostname: pulsar-bookkeeper-0.pulsar-bookkeeper.ns.svc.cluster.local:3181
                        rack: rack1/unknown-node
                      pulsar-bookkeeper-1.pulsar-bookkeeper.ns.svc.cluster.local:3181:
                        hostname: pulsar-bookkeeper-1.pulsar-bookkeeper.ns.svc.cluster.local:3181
                        rack: rack1/unknown-node
                    """);
        });

        clusterSpec.getBookkeeper().setReplicas(3);
        bookKeeperRackDaemon
                .triggerSync(namespace, new BookKeeperFullSpec(clusterSpec.getGlobal(), clusterSpec.getBookkeeper()));

        String s = new String(zkClient.getData().forPath("/bookies"), StandardCharsets.UTF_8);
        s = SerializationUtil.writeAsYaml(SerializationUtil.readJson(s, Map.class));
        Assert.assertEquals(s, """
                ---
                defaultgroup:
                  pulsar-bookkeeper-0.pulsar-bookkeeper.ns.svc.cluster.local:3181:
                    hostname: pulsar-bookkeeper-0.pulsar-bookkeeper.ns.svc.cluster.local:3181
                    rack: rack1/unknown-node
                  pulsar-bookkeeper-1.pulsar-bookkeeper.ns.svc.cluster.local:3181:
                    hostname: pulsar-bookkeeper-1.pulsar-bookkeeper.ns.svc.cluster.local:3181
                    rack: rack1/unknown-node
                  pulsar-bookkeeper-2.pulsar-bookkeeper.ns.svc.cluster.local:3181:
                    hostname: pulsar-bookkeeper-2.pulsar-bookkeeper.ns.svc.cluster.local:3181
                    rack: rack1/unknown-node
                """);
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

        clusterSpec.getBookkeeper().setAutoRackConfig(BookKeeperAutoRackConfig.builder()
                .enabled(true)
                .periodMs(100L)
                .build());
        final BookKeeperRackDaemon bookKeeperRackDaemon = new BookKeeperRackDaemon(client.getClient(),
                new ZkClientRackClientFactory(client.getClient()) {
                    @Override
                    protected String getZkServers(String namespace, BookKeeperFullSpec newSpec) {
                        return zkServerMain.getConnectString();
                    }
                });
        bookKeeperRackDaemon.onSpecChange(clusterSpec, namespace);

        Awaitility.await().untilAsserted(() -> {
            Assert.assertNotNull(zkClient.checkExists().forPath("/bookies"));
            final byte[] bytes = zkClient.getData().forPath("/bookies");
            String s = new String(bytes, StandardCharsets.UTF_8);
            s = SerializationUtil.writeAsYaml(SerializationUtil.readJson(s, Map.class));
            Assert.assertEquals(s, """
                    ---
                    defaultgroup:
                      pulsar-bookkeeper-bk1-0.pulsar-bookkeeper-bk1.ns.svc.cluster.local:3181:
                        hostname: pulsar-bookkeeper-bk1-0.pulsar-bookkeeper-bk1.ns.svc.cluster.local:3181
                        rack: rack1/node1
                      pulsar-bookkeeper-bk1-1.pulsar-bookkeeper-bk1.ns.svc.cluster.local:3181:
                        hostname: pulsar-bookkeeper-bk1-1.pulsar-bookkeeper-bk1.ns.svc.cluster.local:3181
                        rack: rack1/unknown-node
                      pulsar-bookkeeper-bk2-0.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181:
                        hostname: pulsar-bookkeeper-bk2-0.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181
                        rack: rack2/unknown-node
                      pulsar-bookkeeper-bk2-1.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181:
                        hostname: pulsar-bookkeeper-bk2-1.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181
                        rack: rack2/unknown-node
                      pulsar-bookkeeper-bk2-2.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181:
                        hostname: pulsar-bookkeeper-bk2-2.pulsar-bookkeeper-bk2.ns.svc.cluster.local:3181
                        rack: rack2/unknown-node
                      """);
        });
    }

}