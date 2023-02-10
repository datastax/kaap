package com.datastax.oss.pulsaroperator.migrationtool;

import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.controllers.zookeeper.ZooKeeperResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.File;
import java.util.Map;
import lombok.Cleanup;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

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
        fullOut.mkdirs();

        final PulsarClusterSpec pulsarClusterSpec =
                generatePulsarClusterSpec(client, fullOut);
        dumpGeneratedResources(fullOut, pulsarClusterSpec);

    }

    private void dumpGeneratedResources(File fullOut, PulsarClusterSpec pulsarClusterSpec) {
        final MockKubernetesClient local = new MockKubernetesClient(inputSpecs.getNamespace());
        generateZkResources(pulsarClusterSpec, local);

        for (MockKubernetesClient.ResourceInteraction createdResource : local.getCreatedResources()) {
            dumpToFile(fullOut.getAbsolutePath(), "generated", createdResource.getResource());
        }
    }

    private PulsarClusterSpec generatePulsarClusterSpec(KubernetesClient client, File fullOut) {
        final PulsarClusterSpecGenerator pulsarClusterSpecGenerator = new PulsarClusterSpecGenerator(inputSpecs, client);
        final PulsarClusterSpec pulsarClusterSpec = pulsarClusterSpecGenerator.generateSpec();
        dumpToFile(fullOut.getAbsolutePath(), pulsarClusterSpec);
        for (HasMetadata resource : pulsarClusterSpecGenerator.getAllResources()) {
            dumpToFile(fullOut.getAbsolutePath(), "original", resource);
        }
        return pulsarClusterSpec;
    }

    private void generateZkResources(PulsarClusterSpec pulsarClusterSpec, MockKubernetesClient local) {
        final ZooKeeperResourcesFactory zooKeeperResourcesFactory =
                new ZooKeeperResourcesFactory(local.getClient(), inputSpecs.
                        getNamespace(), pulsarClusterSpec.getZookeeper(),
                        pulsarClusterSpec.getGlobal(), null);

        zooKeeperResourcesFactory.patchPodDisruptionBudget();
        zooKeeperResourcesFactory.patchConfigMap();
        zooKeeperResourcesFactory.patchService();
        zooKeeperResourcesFactory.patchCaService();
        zooKeeperResourcesFactory.patchStatefulSet();
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
    private static void dumpToFile(String directory, PulsarClusterSpec spec) {
        final Map<String, Object> asJson = MAPPER.convertValue(spec, Map.class);
        final File resultFile = new File(directory,
                "crd-generated-pulsar-cluster-%s.json".formatted(spec.getGlobal().getName())
        );
        MAPPER.writeValue(resultFile, asJson);
        log.info("Resource PulsarClusterSpec exported to {}", resultFile.getAbsolutePath());
    }

}
