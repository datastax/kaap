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
package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.testng.Assert;
import org.testng.annotations.Test;

public class GlobalSpecTest {

    @Test
    public void testComponentsDefaults() {
        GlobalSpec globalSpec = new GlobalSpec();
        globalSpec.applyDefaults(null);
        GlobalSpec.Components components = globalSpec.getComponents();
        Assert.assertEquals(components.getAutorecoveryBaseName(), "autorecovery");
        Assert.assertEquals(components.getBastionBaseName(), "bastion");
        Assert.assertEquals(components.getBookkeeperBaseName(), "bookkeeper");
        Assert.assertEquals(components.getBrokerBaseName(), "broker");
        Assert.assertEquals(components.getFunctionsWorkerBaseName(), "function");
        Assert.assertEquals(components.getProxyBaseName(), "proxy");
        Assert.assertEquals(components.getZookeeperBaseName(), "zookeeper");

        globalSpec = new GlobalSpec();
        globalSpec.setComponents(GlobalSpec.Components.builder()
                .autorecoveryBaseName("autorecovery-custom")
                .build()
        );
        globalSpec.applyDefaults(null);
        components = globalSpec.getComponents();
        Assert.assertEquals(components.getAutorecoveryBaseName(), "autorecovery-custom");
        Assert.assertEquals(components.getBastionBaseName(), "bastion");
        Assert.assertEquals(components.getBookkeeperBaseName(), "bookkeeper");
        Assert.assertEquals(components.getBrokerBaseName(), "broker");
        Assert.assertEquals(components.getFunctionsWorkerBaseName(), "function");
        Assert.assertEquals(components.getProxyBaseName(), "proxy");
        Assert.assertEquals(components.getZookeeperBaseName(), "zookeeper");
    }

    @Test
    public void testKubernetesClusterDomainDefault() {
        GlobalSpec globalSpec = new GlobalSpec();
        globalSpec.applyDefaults(null);
        Assert.assertEquals(globalSpec.getKubernetesClusterDomain(), "cluster.local");

        globalSpec = new GlobalSpec();
        globalSpec.setKubernetesClusterDomain("k8sprovider.specific.name");
        globalSpec.applyDefaults(null);
        Assert.assertEquals(globalSpec.getKubernetesClusterDomain(), "k8sprovider.specific.name");
    }

    @Test
    public void testPersistenceDefault() {
        GlobalSpec globalSpec = new GlobalSpec();
        globalSpec.applyDefaults(null);
        Assert.assertTrue(globalSpec.getPersistence());

        globalSpec = new GlobalSpec();
        globalSpec.setPersistence(false);
        globalSpec.applyDefaults(null);
        Assert.assertFalse(globalSpec.getPersistence());
    }

    @Test
    public void testRestartOnConfigMapChangeDefault() {
        GlobalSpec globalSpec = new GlobalSpec();
        globalSpec.applyDefaults(null);
        Assert.assertFalse(globalSpec.getRestartOnConfigMapChange());

        globalSpec = new GlobalSpec();
        globalSpec.setRestartOnConfigMapChange(true);
        globalSpec.applyDefaults(null);
        Assert.assertTrue(globalSpec.getRestartOnConfigMapChange());
    }

    @Test
    public void testStorageExistingClassDefaults() {
        GlobalSpec globalSpec = new GlobalSpec();
        globalSpec.applyDefaults(null);
        GlobalSpec.GlobalStorageConfig storage = globalSpec.getStorage();

        Assert.assertEquals("default", storage.getExistingStorageClassName());
        Assert.assertNull(storage.getStorageClass());

        globalSpec = new GlobalSpec();
        globalSpec.setStorage(GlobalSpec.GlobalStorageConfig.builder()
                .existingStorageClassName("k8s-provider-class")
                .build());
        globalSpec.applyDefaults(null);
        storage = globalSpec.getStorage();

        Assert.assertEquals("k8s-provider-class", storage.getExistingStorageClassName());
        Assert.assertNull(storage.getStorageClass());

    }

    @Test
    public void testStorageClassDefaults() {

        GlobalSpec globalSpec = new GlobalSpec();
        globalSpec.setStorage(GlobalSpec.GlobalStorageConfig.builder()
                .storageClass(StorageClassConfig.builder()
                        .provisioner("kubernetes.io/aws-ebs")
                        .type("gp2")
                        .fsType("ext4")
                        .extraParams(Map.of("iopsPerGB", "10"))
                        .build())
                .build());
        globalSpec.applyDefaults(null);
        GlobalSpec.GlobalStorageConfig storage = globalSpec.getStorage();

        Assert.assertNull(storage.getExistingStorageClassName());
        Assert.assertEquals(storage.getStorageClass().getExtraParams().get("iopsPerGB"), "10");
        Assert.assertEquals(storage.getStorageClass().getProvisioner(), "kubernetes.io/aws-ebs");
        Assert.assertEquals(storage.getStorageClass().getType(), "gp2");
        Assert.assertEquals(storage.getStorageClass().getFsType(), "ext4");
        Assert.assertEquals(storage.getStorageClass().getReclaimPolicy(), "Retain");


        globalSpec = new GlobalSpec();
        globalSpec.setStorage(GlobalSpec.GlobalStorageConfig.builder()
                .storageClass(StorageClassConfig.builder()
                        .provisioner("kubernetes.io/aws-ebs")
                        .type("gp2")
                        .fsType("ext4")
                        .reclaimPolicy("Delete")
                        .extraParams(Map.of("iopsPerGB", "10"))
                        .build())
                .build());
        globalSpec.applyDefaults(null);
        storage = globalSpec.getStorage();

        Assert.assertNull(storage.getExistingStorageClassName());
        Assert.assertEquals(storage.getStorageClass().getExtraParams().get("iopsPerGB"), "10");
        Assert.assertEquals(storage.getStorageClass().getProvisioner(), "kubernetes.io/aws-ebs");
        Assert.assertEquals(storage.getStorageClass().getType(), "gp2");
        Assert.assertEquals(storage.getStorageClass().getFsType(), "ext4");
        Assert.assertEquals(storage.getStorageClass().getReclaimPolicy(), "Delete");

    }

    @Test
    public void testAuthDefaults() {
        GlobalSpec globalSpec = new GlobalSpec();
        globalSpec.applyDefaults(null);
        final AuthConfig auth = globalSpec.getAuth();
        Assert.assertFalse(auth.getEnabled());
        final AuthConfig.TokenAuthenticationConfig token = auth.getToken();
        Assert.assertEquals(token.getPrivateKeyFile(), "my-private.key");
        Assert.assertEquals(token.getPublicKeyFile(), "my-public.key");
        Assert.assertEquals(token.getProxyRoles(), Set.of("proxy"));
        Assert.assertEquals(token.getSuperUserRoles(), new TreeSet<>(Set.of("admin", "proxy", "superuser", "websocket")));
        Assert.assertTrue(token.getInitialize());
    }

    @Test
    public void testAuthTokenDefaults() {
        GlobalSpec globalSpec = new GlobalSpec();
        globalSpec.setAuth(AuthConfig.builder()
                .enabled(true)
                .token(AuthConfig.TokenAuthenticationConfig.builder()
                        .superUserRoles(new TreeSet<>(Set.of("superuser", "admin", "websocket", "proxy", "superuser2")))
                        .proxyRoles(new TreeSet<>(Set.of("proxy", "superuser2")))
                        .build())
                .build());
        globalSpec.applyDefaults(null);
        final AuthConfig auth = globalSpec.getAuth();
        Assert.assertTrue(auth.getEnabled());
        final AuthConfig.TokenAuthenticationConfig token = auth.getToken();
        Assert.assertEquals(token.getPrivateKeyFile(), "my-private.key");
        Assert.assertEquals(token.getPublicKeyFile(), "my-public.key");
        Assert.assertEquals(token.getProxyRoles(), Set.of("proxy", "superuser2"));
        Assert.assertEquals(token.getSuperUserRoles(),
                new TreeSet<>(Set.of("admin", "proxy", "superuser", "superuser2", "websocket")));
        Assert.assertTrue(token.getInitialize());
    }
}