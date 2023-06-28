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
package com.datastax.oss.kaap.tests;

import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "misc")
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
        Assert.assertTrue(crds.contains("zookeepers.kaap.oss.datastax.com"));
        Assert.assertTrue(crds.contains("bookkeepers.kaap.oss.datastax.com"));
        Assert.assertTrue(crds.contains("brokers.kaap.oss.datastax.com"));
        Assert.assertTrue(crds.contains("proxies.kaap.oss.datastax.com"));
        Assert.assertTrue(crds.contains("autorecoveries.kaap.oss.datastax.com"));
        Assert.assertTrue(crds.contains("functionsworkers.kaap.oss.datastax.com"));
        Assert.assertTrue(crds.contains("bastions.kaap.oss.datastax.com"));
        Assert.assertTrue(crds.contains("pulsarclusters.kaap.oss.datastax.com"));
    }

}
