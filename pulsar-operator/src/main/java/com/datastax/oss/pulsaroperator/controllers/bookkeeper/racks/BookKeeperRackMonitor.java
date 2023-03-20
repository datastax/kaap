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
package com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks;

import com.datastax.oss.pulsaroperator.autoscaler.BookKeeperSetAutoscaler;
import com.datastax.oss.pulsaroperator.common.json.JSONComparator;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.BookKeeperController;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks.client.BkRackClient;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.SpecDiffer;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSetSpec;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.exception.ExceptionUtils;

@JBossLog
public class BookKeeperRackMonitor implements Runnable {

    private final KubernetesClient client;
    private final String namespace;
    private final BookKeeperFullSpec bkFullSpec;
    private final BkRackClient bkRackClient;

    public BookKeeperRackMonitor(KubernetesClient client, String namespace,
                                 BookKeeperFullSpec clusterSpec,
                                 BkRackClient bkRackClient) {
        this.client = client;
        this.namespace = namespace;
        this.bkFullSpec = clusterSpec;
        this.bkRackClient = bkRackClient;
    }


    @Override
    public void run() {
        try {
            internalRun();
        } catch (Throwable t) {
            if (ExceptionUtils.indexOfThrowable(t, InterruptedException.class) >= 0) {
                Thread.currentThread().interrupt();
            } else if (ExceptionUtils.indexOfThrowable(t, RejectedExecutionException.class) >= 0) {
                log.debugf(t, "Task rejected, probably the executor is going to be shut down");
            } else {
                log.errorf(t, "Error in BookKeeperRackMonitor: %s", t.getMessage());
            }
        }
    }

    @SneakyThrows
    void internalRun() {
        final BkRackClient.BookiesRackOp bookiesRackOp = bkRackClient.newBookiesRackOp();
        final BkRackClient.BookiesRackConfiguration expectedBookiesRackConfig = getExpectedBookiesRackConfig();
        log.infof("Getting current bookies rack configuration, expected is %s", expectedBookiesRackConfig);
        final BkRackClient.BookiesRackConfiguration currentConfig = bookiesRackOp.get();

        final JSONComparator.Result diff = SpecDiffer.generateDiff(currentConfig, expectedBookiesRackConfig);
        if (diff.areEquals()) {
            return;
        }
        log.infof("One or more bookies rack configurations need to be changed");
        SpecDiffer.logDetailedSpecDiff(diff, null, null);
        bookiesRackOp.update(expectedBookiesRackConfig);
    }

    private BkRackClient.BookiesRackConfiguration getExpectedBookiesRackConfig() {
        final GlobalSpec global = bkFullSpec.getGlobal();

        Map<String, BkRackClient.BookieRackInfo> bookiesRackInfo = new LinkedHashMap<>();

        final LinkedHashMap<String, BookKeeperSetSpec> bkSets =
                BookKeeperController.getBookKeeperSetSpecs(bkFullSpec.getBookkeeper());

        final GlobalSpec globalSpec = bkFullSpec.getGlobalSpec();
        for (Map.Entry<String, BookKeeperSetSpec> bkSet : bkSets.entrySet()) {
            final String resourceSet = bkSet.getKey();
            final String rack = BookKeeperResourcesFactory.getRack(global, resourceSet);
            if (rack == null) {
                continue;
            }
            final BookKeeperSetSpec spec = bkSet.getValue();
            final int replicas = spec.getReplicas();


            final String stsName = BookKeeperResourcesFactory.getResourceName(globalSpec.getName(),
                    globalSpec.getComponents().getBookkeeperBaseName(), resourceSet, spec.getOverrideResourceName());

            for (int i = 0; i < replicas; i++) {

                final String podName = "%s-%d".formatted(stsName, i);
                final String bookieId =
                        BookKeeperSetAutoscaler.getBookieId(podName,
                                resourceSet, spec, globalSpec, namespace);

                final Pod pod = getPod(podName);
                String nodeName;
                if (pod == null) {
                    // it always happens when scaling up
                    nodeName = "unknown-node";
                } else {
                    nodeName = pod.getSpec().getNodeName();
                    if (nodeName == null) {
                        nodeName = "unknown-node";
                    }
                }

                final String bkRack = "%s/%s".formatted(rack, nodeName);

                bookiesRackInfo.put(bookieId,
                        BkRackClient.BookieRackInfo.builder()
                                .hostname(bookieId)
                                .rack(bkRack)
                                .build());
            }
        }

        final BkRackClient.BookiesRackConfiguration bookiesRackConfiguration =
                new BkRackClient.BookiesRackConfiguration();
        bookiesRackConfiguration.put("defaultgroup", bookiesRackInfo);
        return bookiesRackConfiguration;
    }


    private Pod getPod(String name) {
        return client
                .pods()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }
}
