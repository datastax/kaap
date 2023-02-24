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

import com.datastax.oss.pulsaroperator.controllers.autorecovery.AutorecoveryResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.bastion.BastionResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.function.FunctionsWorkerResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.proxy.ProxyResourcesFactory;
import com.datastax.oss.pulsaroperator.controllers.zookeeper.ZooKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.mocks.MockKubernetesClient;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    public static final String ORIGINAL_PREFIX = "original";
    public static final String GENERATED_PREFIX = "generated";
    public static final String CRD_GENERATED_FIX = "crd-generated-pulsar-cluster";

    private final String outputDirectory;
    private final InputClusterSpecs inputSpecs;

    @SneakyThrows
    public static List<Path> getGeneratedResourcesFileFromDir(File dir) {
        return Files.list(dir.toPath())
                .filter(p -> p.toFile().getName().startsWith(GENERATED_PREFIX + "-") && p.toFile().getName()
                        .endsWith(".json"))
                .collect(Collectors.toList());
    }

    @SneakyThrows
    public static List<Path> getOriginalResourcesFileFromDir(File dir) {
        return Files.list(dir.toPath())
                .filter(p -> p.toFile().getName().startsWith(ORIGINAL_PREFIX + "-") && p.toFile().getName()
                        .endsWith(".json"))
                .collect(Collectors.toList());
    }

    @SneakyThrows
    public static Path getGeneratedPulsarClusterFileFromDir(File dir) {
        return Files.list(dir.toPath())
                .filter(p -> p.toFile().getName().startsWith(CRD_GENERATED_FIX + "-") && p.toFile().getName()
                        .endsWith(".yaml"))
                .findFirst()
                .get();
    }

    @SneakyThrows
    public static Path getGeneratedPulsarClusterJSONFileFromDir(File dir) {
        return Files.list(dir.toPath())
                .filter(p -> p.toFile().getName().startsWith(CRD_GENERATED_FIX + "-") && p.toFile().getName()
                        .endsWith(".json"))
                .findFirst()
                .get();
    }


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
        generate(client);
    }

    public void generate(KubernetesClient client) {
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
        generateAutorecoveryResources(pulsarClusterSpec, local);
        generateBastionResources(pulsarClusterSpec, local);
        generateProxyResources(pulsarClusterSpec, local);
        generateFunctionsWorkerResources(pulsarClusterSpec, local);

        for (MockKubernetesClient.ResourceInteraction createdResource : local.getCreatedResources()) {
            dumpToFile(fullOut.getAbsolutePath(), GENERATED_PREFIX, createdResource.getResource());
        }
    }

    private PulsarCluster generatePulsarClusterSpec(KubernetesClient client, File fullOut) {
        final PulsarClusterResourceGenerator
                pulsarClusterSpecGenerator = new PulsarClusterResourceGenerator(inputSpecs, client);
        final PulsarCluster pulsarCluster = pulsarClusterSpecGenerator.generatePulsarClusterCustomResource();
        dumpToFile(fullOut.getAbsolutePath(), pulsarCluster);
        for (HasMetadata resource : pulsarClusterSpecGenerator.getAllResources()) {
            dumpToFile(fullOut.getAbsolutePath(), ORIGINAL_PREFIX, resource);
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
                        getNamespace(), BrokerResourcesFactory.BROKER_DEFAULT_SET, pulsarCluster.getSpec().getBroker(),
                        pulsarCluster.getSpec().getGlobal(), null);

        brokerResourcesFactory.patchPodDisruptionBudget();
        brokerResourcesFactory.patchConfigMap();
        brokerResourcesFactory.patchService();
        brokerResourcesFactory.patchStatefulSet();
    }

    private void generateAutorecoveryResources(PulsarCluster pulsarCluster, MockKubernetesClient local) {
        final AutorecoveryResourcesFactory autorecoveryResourcesFactory =
                new AutorecoveryResourcesFactory(local.getClient(), inputSpecs.
                        getNamespace(), pulsarCluster.getSpec().getAutorecovery(),
                        pulsarCluster.getSpec().getGlobal(), null);

        autorecoveryResourcesFactory.patchConfigMap();
        autorecoveryResourcesFactory.patchDeployment();
    }

    private void generateBastionResources(PulsarCluster pulsarCluster, MockKubernetesClient local) {
        final BastionResourcesFactory bastionResourcesFactory =
                new BastionResourcesFactory(local.getClient(), inputSpecs.
                        getNamespace(), pulsarCluster.getSpec().getBastion(),
                        pulsarCluster.getSpec().getGlobal(), null);

        bastionResourcesFactory.patchConfigMap();
        bastionResourcesFactory.patchDeployment();
    }

    private void generateProxyResources(PulsarCluster pulsarCluster, MockKubernetesClient local) {
        final ProxyResourcesFactory proxyResourcesFactory =
                new ProxyResourcesFactory(local.getClient(), inputSpecs.
                        getNamespace(), ProxyResourcesFactory.PROXY_DEFAULT_SET, pulsarCluster.getSpec().getProxy(),
                        pulsarCluster.getSpec().getGlobal(), null);
        proxyResourcesFactory.patchPodDisruptionBudget();
        proxyResourcesFactory.patchConfigMap();
        proxyResourcesFactory.patchConfigMapWsConfig();
        proxyResourcesFactory.patchService();
        proxyResourcesFactory.patchDeployment();
    }

    private void generateFunctionsWorkerResources(PulsarCluster pulsarCluster, MockKubernetesClient local) {
        final FunctionsWorkerResourcesFactory functionsWorkerResourcesFactory =
                new FunctionsWorkerResourcesFactory(local.getClient(), inputSpecs.
                        getNamespace(), pulsarCluster.getSpec().getFunctionsWorker(),
                        pulsarCluster.getSpec().getGlobal(), null);
        functionsWorkerResourcesFactory.patchPodDisruptionBudget();
        functionsWorkerResourcesFactory.patchConfigMap();
        functionsWorkerResourcesFactory.patchExtraConfigMap();
        functionsWorkerResourcesFactory.patchCaService();
        functionsWorkerResourcesFactory.patchService();
        functionsWorkerResourcesFactory.patchStatefulSet();
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
                "%s-%s.json".formatted(CRD_GENERATED_FIX, spec.getMetadata().getName())
        );

        MAPPER.writeValue(resultFileJson, asJson);
        log.info("Resource PulsarClusterSpec exported to {}", resultFileJson.getAbsolutePath());

        final File resultFileYaml = new File(directory,
                "%s-%s.yaml".formatted(CRD_GENERATED_FIX, spec.getMetadata().getName()));
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
        try (final FileWriter fileWriter = new FileWriter(resultFileYaml, StandardCharsets.UTF_8);) {
            yaml.dump(asJson, fileWriter);
        }
    }


}
