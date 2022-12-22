package com.datastax.oss.pulsaroperator.tests;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
public class CRDsTest extends BasePulsarClusterTest {

    @Test
    public void testCRDs() throws Exception {
        applyRBACManifests();
        applyOperatorDeploymentAndCRDs();
        final CustomResourceDefinitionList list = client.apiextensions().v1()
                .customResourceDefinitions()
                .list();
        final List<String> crds = list.getItems()
                .stream()
                .map(crd -> crd.getMetadata().getName())
                .collect(Collectors.toList());
        Assert.assertTrue(crds.contains("zookeepers.pulsar.oss.datastax.com"));
        Assert.assertTrue(crds.contains("bookkeepers.pulsar.oss.datastax.com"));
        Assert.assertTrue(crds.contains("brokers.pulsar.oss.datastax.com"));
        Assert.assertTrue(crds.contains("proxies.pulsar.oss.datastax.com"));
        Assert.assertTrue(crds.contains("autorecoveries.pulsar.oss.datastax.com"));
        Assert.assertTrue(crds.contains("functionsworkers.pulsar.oss.datastax.com"));
        Assert.assertTrue(crds.contains("bastions.pulsar.oss.datastax.com"));
        Assert.assertTrue(crds.contains("pulsarclusters.pulsar.oss.datastax.com"));
    }

}
