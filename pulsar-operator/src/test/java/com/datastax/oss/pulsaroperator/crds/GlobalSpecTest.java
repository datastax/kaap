package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import com.datastax.oss.pulsaroperator.crds.configs.StorageClassConfig;
import java.util.List;
import java.util.Map;
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
        final AuthConfig.TokenConfig token = auth.getToken();
        Assert.assertEquals(token.getPrivateKeyFile(), "my-private.key");
        Assert.assertEquals(token.getPublicKeyFile(), "my-public.key");
        Assert.assertEquals(token.getProxyRoles(), List.of("proxy"));
        Assert.assertEquals(token.getSuperUserRoles(), List.of("superuser", "admin", "websocket", "proxy"));
        final AuthConfig.TokenAuthProvisionerConfig provisioner = token.getProvisioner();
        Assert.assertEquals(provisioner.getImage(), "datastax/burnell:latest");
        Assert.assertEquals(provisioner.getImagePullPolicy(), "IfNotPresent");
        Assert.assertTrue(provisioner.getRbac().getCreate());
        Assert.assertTrue(provisioner.getRbac().getNamespaced());
        Assert.assertTrue(provisioner.getInitialize());
        Assert.assertNull(provisioner.getResources());
    }

    @Test
    public void testAuthTokenDefaults() {
        GlobalSpec globalSpec = new GlobalSpec();
        globalSpec.setAuth(AuthConfig.builder()
                .enabled(true)
                .token(AuthConfig.TokenConfig.builder()
                        .superUserRoles(List.of("superuser", "admin", "websocket", "proxy", "superuser2"))
                        .proxyRoles(List.of("proxy", "superuser2"))
                        .build())
                .build());
        globalSpec.applyDefaults(null);
        final AuthConfig auth = globalSpec.getAuth();
        Assert.assertTrue(auth.getEnabled());
        final AuthConfig.TokenConfig token = auth.getToken();
        Assert.assertEquals(token.getPrivateKeyFile(), "my-private.key");
        Assert.assertEquals(token.getPublicKeyFile(), "my-public.key");
        Assert.assertEquals(token.getProxyRoles(), List.of("proxy", "superuser2"));
        Assert.assertEquals(token.getSuperUserRoles(), List.of("superuser", "admin", "websocket", "proxy", "superuser2"));
    }

    @Test
    public void testAuthTokenProvisionerDefaults() {
        GlobalSpec globalSpec = new GlobalSpec();
        globalSpec.setAuth(AuthConfig.builder()
                .enabled(true)
                .token(AuthConfig.TokenConfig.builder()
                        .provisioner(AuthConfig.TokenAuthProvisionerConfig.builder()
                                .initialize(false)
                                .image("datastax/burnell:v1")
                                .rbac(AuthConfig.TokenAuthProvisionerConfig.RbacConfig.builder()
                                        .namespaced(false)
                                        .build())
                                .build())
                        .build())
                .build());
        globalSpec.applyDefaults(null);
        final AuthConfig auth = globalSpec.getAuth();
        Assert.assertTrue(auth.getEnabled());
        final AuthConfig.TokenConfig token = auth.getToken();
        Assert.assertEquals(token.getPrivateKeyFile(), "my-private.key");
        Assert.assertEquals(token.getPublicKeyFile(), "my-public.key");
        Assert.assertEquals(token.getProxyRoles(), List.of("proxy"));
        Assert.assertEquals(token.getSuperUserRoles(), List.of("superuser", "admin", "websocket", "proxy"));
        final AuthConfig.TokenAuthProvisionerConfig provisioner = token.getProvisioner();
        Assert.assertEquals(provisioner.getImage(), "datastax/burnell:v1");
        Assert.assertEquals(provisioner.getImagePullPolicy(), "IfNotPresent");
        Assert.assertTrue(provisioner.getRbac().getCreate());
        Assert.assertFalse(provisioner.getRbac().getNamespaced());
        Assert.assertFalse(provisioner.getInitialize());
        Assert.assertNull(provisioner.getResources());
    }


}