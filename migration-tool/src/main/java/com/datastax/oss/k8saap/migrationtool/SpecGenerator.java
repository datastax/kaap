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
package com.datastax.oss.k8saap.migrationtool;

import com.datastax.oss.k8saap.controllers.autorecovery.AutorecoveryResourcesFactory;
import com.datastax.oss.k8saap.controllers.bastion.BastionResourcesFactory;
import com.datastax.oss.k8saap.controllers.bookkeeper.BookKeeperController;
import com.datastax.oss.k8saap.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.k8saap.controllers.broker.BrokerController;
import com.datastax.oss.k8saap.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.k8saap.controllers.function.FunctionsWorkerResourcesFactory;
import com.datastax.oss.k8saap.controllers.proxy.ProxyController;
import com.datastax.oss.k8saap.controllers.proxy.ProxyResourcesFactory;
import com.datastax.oss.k8saap.controllers.zookeeper.ZooKeeperResourcesFactory;
import com.datastax.oss.k8saap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.k8saap.crds.broker.BrokerSetSpec;
import com.datastax.oss.k8saap.crds.cluster.PulsarCluster;
import com.datastax.oss.k8saap.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.k8saap.crds.proxy.ProxySetSpec;
import com.datastax.oss.k8saap.mocks.MockKubernetesClient;
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
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
    public static final String CRD_GENERATED_PREFIX = "crd-generated-pulsar-cluster";

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
                .filter(p -> p.toFile().getName().startsWith(CRD_GENERATED_PREFIX + "-") && p.toFile().getName()
                        .endsWith(".yaml"))
                .findFirst()
                .get();
    }

    @SneakyThrows
    public static Path getGeneratedPulsarClusterJSONFileFromDir(File dir) {
        return Files.list(dir.toPath())
                .filter(p -> p.toFile().getName().startsWith(CRD_GENERATED_PREFIX + "-") && p.toFile().getName()
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
        dumpOriginalResources(client, fullOut);
        return pulsarCluster;
    }

    private void dumpOriginalResources(KubernetesClient client, File fullOut) {
        List.of(
                        client.policy().v1().podDisruptionBudget(),
                        client.apps().statefulSets(),
                        client.apps().deployments(),
                        client.configMaps(),
                        client.services(),
                        client.secrets(),
                        client.batch().v1().jobs(),
                        client.network().v1().ingresses(),
                        client.serviceAccounts(),
                        client.rbac().roles(),
                        client.rbac().roleBindings()
                ).stream()
                .map(p -> p.inNamespace(inputSpecs.getNamespace())
                        .list()
                        .getItems())
                .flatMap(List::stream)
                .forEach(r -> dumpToFile(fullOut.getAbsolutePath(), ORIGINAL_PREFIX, r));
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
        final LinkedHashMap<String, BookKeeperSetSpec> sets = BookKeeperController.getBookKeeperSetSpecs(
                pulsarCluster.getSpec().getBookkeeper());
        for (Map.Entry<String, BookKeeperSetSpec> set : sets.entrySet()) {
            System.out.println("create bkResourceFactory" + pulsarCluster);
            System.out.println("create bkResourceFactory-global" + pulsarCluster.getSpec().getGlobal());
            final BookKeeperResourcesFactory bkResourceFactory =
                    new BookKeeperResourcesFactory(local.getClient(), inputSpecs.
                            getNamespace(), set.getKey(), set.getValue(),
                            pulsarCluster.getSpec().getGlobal(), null);

            bkResourceFactory.patchPodDisruptionBudget();
            bkResourceFactory.patchConfigMap();
            bkResourceFactory.patchService();
            bkResourceFactory.patchStorageClasses();
            bkResourceFactory.patchStatefulSet();
        }
    }

    private void generateBrokerResources(PulsarCluster pulsarCluster, MockKubernetesClient local) {
        final LinkedHashMap<String, BrokerSetSpec> sets = BrokerController.getBrokerSetSpecs(
                pulsarCluster.getSpec().getBroker());
        for (Map.Entry<String, BrokerSetSpec> brokerSet : sets.entrySet()) {

            final BrokerResourcesFactory brokerResourcesFactory =
                    new BrokerResourcesFactory(local.getClient(), inputSpecs.
                            getNamespace(), brokerSet.getKey(),
                            brokerSet.getValue(),
                            pulsarCluster.getSpec().getGlobal(), null);

            brokerResourcesFactory.patchPodDisruptionBudget();
            brokerResourcesFactory.patchConfigMap();
            brokerResourcesFactory.patchService();
            brokerResourcesFactory.patchStatefulSet();
        }
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
        final LinkedHashMap<String, ProxySetSpec> proxySetSpecs = ProxyController.getProxySetSpecs(
                pulsarCluster.getSpec().getProxy());
        for (Map.Entry<String, ProxySetSpec> proxySet : proxySetSpecs.entrySet()) {
            final ProxyResourcesFactory proxyResourcesFactory =
                    new ProxyResourcesFactory(local.getClient(), inputSpecs.
                            getNamespace(), proxySet.getKey(), proxySet.getValue(),
                            pulsarCluster.getSpec().getGlobal(), null);
            proxyResourcesFactory.patchPodDisruptionBudget();
            proxyResourcesFactory.patchConfigMap();
            proxyResourcesFactory.patchConfigMapWsConfig();
            proxyResourcesFactory.patchService();
            proxyResourcesFactory.patchDeployment();
        }
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
                "%s-%s.json".formatted(CRD_GENERATED_PREFIX, spec.getMetadata().getName())
        );

        MAPPER.writeValue(resultFileJson, asJson);
        log.info("Resource PulsarClusterSpec exported to {}", resultFileJson.getAbsolutePath());

        final File resultFileYaml = new File(directory,
                "%s-%s.yaml".formatted(CRD_GENERATED_PREFIX, spec.getMetadata().getName()));
        prettyPrintYaml(asJson, resultFileYaml);
        log.info("Resource PulsarClusterSpec exported to {}", resultFileYaml.getAbsolutePath());
        dumpValuesToFile(directory, spec.getSpec());
    }

    private static void dumpValuesToFile(String directory, PulsarClusterSpec spec) throws IOException {
        final Map specMap = MAPPER.convertValue(spec, Map.class);
        final Map<String, Object> valuesMap = Map.of("k8saap",
                Map.of("cluster", Map.of(
                        "create", true,
                        "spec", specMap
                )));


        final String yaml = prettyPrintYaml(valuesMap);
        final Path path = Path.of(directory, "values.yaml");
        Files.write(path, yaml.getBytes(StandardCharsets.UTF_8));
        log.info("Operator values exported to {}", path.toFile().getAbsolutePath());
    }

    private static String prettyPrintYaml(Map<String, Object> asJson) throws IOException {
        try (final StringWriter stringWriter = new StringWriter();) {
            prettyPrintYaml(asJson, stringWriter);
            return stringWriter.toString();
        }

    }

    private static void prettyPrintYaml(Map<String, Object> asJson, File resultFileYaml) throws IOException {
        try (final FileWriter fileWriter = new FileWriter(resultFileYaml, StandardCharsets.UTF_8);) {
            prettyPrintYaml(asJson, fileWriter);
        }
    }

    private static void prettyPrintYaml(Map<String, Object> asJson, Writer writer) {
        DumperOptions options = new DumperOptions();
        options.setAllowReadOnlyProperties(true);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        yaml.dump(asJson, writer);
    }

}
