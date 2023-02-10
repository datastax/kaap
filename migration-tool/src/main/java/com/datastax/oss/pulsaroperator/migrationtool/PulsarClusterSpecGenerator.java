package com.datastax.oss.pulsaroperator.migrationtool;

import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.List;
import java.util.Objects;

public class PulsarClusterSpecGenerator {

    private final InputClusterSpecs inputSpecs;
    private final KubernetesClient client;
    private PulsarClusterSpec generatedSpec;

    private final ZooKeeperSpecGenerator zooKeeperSpecGenerator;
    private final List<BaseSpecGenerator> specGenerators;


    public PulsarClusterSpecGenerator(InputClusterSpecs inputSpecs, KubernetesClient client) {
        this.inputSpecs = inputSpecs;
        this.client = client;
        zooKeeperSpecGenerator = new ZooKeeperSpecGenerator(inputSpecs, client);
        specGenerators = List.of(zooKeeperSpecGenerator);
        internalGenerateSpec();
    }

    public PulsarClusterSpec generateSpec() {
        return generatedSpec;
    }

    public List<HasMetadata> getAllResources() {
        return specGenerators.stream()
                .map(BaseSpecGenerator::getAllResources)
                .flatMap(List::stream)
                .toList();
    }


    private void internalGenerateSpec() {

        final GlobalSpec global = GlobalSpec.builder()
                .name(inputSpecs.getClusterName())
                .components(GlobalSpec.Components.builder()
                        .zookeeperBaseName(inputSpecs.getZookeeper().getBaseName())
                        .build())
                .dnsConfig(getPodDNSConfig())
                .build();

        generatedSpec = PulsarClusterSpec.builder()
                .global(global)
                .zookeeper(zooKeeperSpecGenerator.generateSpec())
                .build();
        generatedSpec.applyDefaults(global);

    }

    private PodDNSConfig getPodDNSConfig() {
        final BaseSpecGenerator first = specGenerators.get(0);
        for (BaseSpecGenerator specGenerator : specGenerators) {
            if (!Objects.equals(first.getPodDnsConfig(), specGenerator.getPodDnsConfig())) {
                throw new IllegalStateException(
                        "PodDNSConfig is not equal for all components, found for " + first.getClass().getName() + " -> "
                                + first.getPodDnsConfig() + " and for  " + specGenerator.getClass().getName() + " -> "
                                + specGenerator.getPodDnsConfig());
            }
        }
        return first.getPodDnsConfig();
    }

}
