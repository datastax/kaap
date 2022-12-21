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
        Assert.assertTrue(crds.contains("zookeepers.com.datastax.oss"));
        Assert.assertTrue(crds.contains("bookkeepers.com.datastax.oss"));
        Assert.assertTrue(crds.contains("brokers.com.datastax.oss"));
        Assert.assertTrue(crds.contains("proxies.com.datastax.oss"));
        Assert.assertTrue(crds.contains("autorecoveries.com.datastax.oss"));
        Assert.assertTrue(crds.contains("functionsworkers.com.datastax.oss"));
        Assert.assertTrue(crds.contains("bastions.com.datastax.oss"));
        Assert.assertTrue(crds.contains("pulsarclusters.com.datastax.oss"));
    }

}
