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
package com.datastax.oss.pulsaroperator.migrationtool;

import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.zookeeper.ZooKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@Slf4j
public class SpecGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final String outputDirectory;
    private final InputClusterSpecs inputSpecs;


    public SpecGenerator(String outputDirectory,
                         InputClusterSpecs inputSpecs) {
        this.outputDirectory = outputDirectory;
        this.inputSpecs = inputSpecs;
    }

    public void generate() {
        KubernetesClientBuilder builder = new KubernetesClientBuilder();
        builder.withConfig(Config.autoConfigure(inputSpecs.context));
        @Cleanup
        KubernetesClient client = builder.build();

        final File fullOut = new File(outputDirectory, inputSpecs.context);
        final boolean mkdirs = fullOut.mkdirs();
        if (mkdirs) {
            log.info("Created directory {}", fullOut);
        } else {
            log.info("Using already existing directory {}", fullOut);
        }

        final PulsarCluster pulsarCluster =
                generatePulsarClusterSpec(client, fullOut);
        dumpGeneratedResources(fullOut, pulsarCluster);

    }

    private void dumpGeneratedResources(File fullOut, PulsarCluster pulsarClusterSpec) {
        final MockKubernetesClient local = new MockKubernetesClient(inputSpecs.getNamespace());
        generateZkResources(pulsarClusterSpec, local);
        generateBkResources(pulsarClusterSpec, local);
        generateBrokerResources(pulsarClusterSpec, local);

        for (MockKubernetesClient.ResourceInteraction createdResource : local.getCreatedResources()) {
            dumpToFile(fullOut.getAbsolutePath(), "generated", createdResource.getResource());
        }
    }

    private PulsarCluster generatePulsarClusterSpec(KubernetesClient client, File fullOut) {
        final PulsarClusterResourceGenerator
                pulsarClusterSpecGenerator = new PulsarClusterResourceGenerator(inputSpecs, client);
        final PulsarCluster pulsarCluster = pulsarClusterSpecGenerator.generatePulsarClusterCustomResource();
        dumpToFile(fullOut.getAbsolutePath(), pulsarCluster);
        for (HasMetadata resource : pulsarClusterSpecGenerator.getAllResources()) {
            dumpToFile(fullOut.getAbsolutePath(), "original", resource);
        }
        return pulsarCluster;
    }

    private void generateZkResources(PulsarCluster pulsarCluster, MockKubernetesClient local) {
        final ZooKeeperResourcesFactory zooKeeperResourcesFactory =
                new ZooKeeperResourcesFactory(local.getClient(), inputSpecs.
                        getNamespace(), pulsarCluster.getSpec().getZookeeper(),
                        pulsarCluster.getSpec().getGlobal(), null);

        zooKeeperResourcesFactory.patchPodDisruptionBudget();
        zooKeeperResourcesFactory.patchConfigMap();
        zooKeeperResourcesFactory.patchService();
        zooKeeperResourcesFactory.patchCaService();
        zooKeeperResourcesFactory.patchStatefulSet();
    }

    private void generateBkResources(PulsarCluster pulsarCluster, MockKubernetesClient local) {
        final BookKeeperResourcesFactory bkResourceFactory =
                new BookKeeperResourcesFactory(local.getClient(), inputSpecs.
                        getNamespace(), pulsarCluster.getSpec().getBookkeeper(),
                        pulsarCluster.getSpec().getGlobal(), null);

        bkResourceFactory.patchPodDisruptionBudget();
        bkResourceFactory.patchConfigMap();
        bkResourceFactory.patchService();
        bkResourceFactory.patchStatefulSet();
    }

    private void generateBrokerResources(PulsarCluster pulsarCluster, MockKubernetesClient local) {
        final BrokerResourcesFactory brokerResourcesFactory =
                new BrokerResourcesFactory(local.getClient(), inputSpecs.
                        getNamespace(), pulsarCluster.getSpec().getBroker(),
                        pulsarCluster.getSpec().getGlobal(), null);

        brokerResourcesFactory.patchPodDisruptionBudget();
        brokerResourcesFactory.patchConfigMap();
        brokerResourcesFactory.patchService();
        brokerResourcesFactory.patchStatefulSet();
    }


    @SneakyThrows
    private static void dumpToFile(String directory, String prefix, HasMetadata hasMetadata) {
        final Map<String, Object> asJson = MAPPER.convertValue(hasMetadata, Map.class);
        final File resultFile = new File(directory,
                "%s-%s-%s.json".formatted(
                        prefix,
                        hasMetadata.getFullResourceName().toLowerCase(),
                        hasMetadata.getMetadata().getName()
                )
        );
        MAPPER.writeValue(resultFile, asJson);
        log.info("Resource {} exported to {}", hasMetadata.getFullResourceName(), resultFile.getAbsolutePath());
    }

    @SneakyThrows
    private static void dumpToFile(String directory, PulsarCluster spec) {
        final Map<String, Object> asJson = MAPPER.convertValue(spec, Map.class);
        asJson.remove("status");

        final File resultFileJson = new File(directory,
                "crd-generated-pulsar-cluster-%s.json".formatted(spec.getMetadata().getName())
        );

        MAPPER.writeValue(resultFileJson, asJson);
        log.info("Resource PulsarClusterSpec exported to {}", resultFileJson.getAbsolutePath());

        final File resultFileYaml = new File(directory,
                "crd-generated-pulsar-cluster-%s.yaml".formatted(spec.getMetadata().getName())
        );
        prettyPrintYaml(asJson, resultFileYaml);
        log.info("Resource PulsarClusterSpec exported to {}", resultFileYaml.getAbsolutePath());
    }

    private static void prettyPrintYaml(Map<String, Object> asJson, File resultFileYaml) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setAllowReadOnlyProperties(true);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        @Cleanup
        final FileWriter fileWriter = new FileWriter(resultFileYaml);
        yaml.dump(asJson, fileWriter);
    }


}
