package com.datastax.oss.pulsaroperator.controllers;

import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.Collection;
import java.util.List;
import org.testng.Assert;

public class KubeTestUtil {
    private KubeTestUtil() {
    }


    public static VolumeMount getVolumeMountByName(Collection<VolumeMount> mounts, String name) {
        for (VolumeMount mount : mounts) {
            if (mount.getName().equals(name)) {
                return mount;
            }
        }
        return null;
    }

    public static Volume getVolumeByName(Collection<Volume> volumes, String name) {
        for (Volume volume : volumes) {
            if (volume.getName().equals(name)) {
                return volume;
            }
        }
        return null;
    }

    public static void assertVolumeFromSecret(Collection<Volume> volumes, String volumeAndSecretName) {
        final Volume vol = KubeTestUtil.getVolumeByName(volumes, volumeAndSecretName);
        Assert.assertNotNull(vol, "volume %s not found".formatted(volumeAndSecretName));
        Assert.assertEquals(vol.getSecret().getSecretName(),volumeAndSecretName);
    }

    public static void assertVolumeMount(Collection<VolumeMount> volumeMounts,
                                         String volumeName,
                                         String expectedPath,
                                         boolean expectReadonly) {
        final VolumeMount vol = KubeTestUtil.getVolumeMountByName(volumeMounts, volumeName);
        Assert.assertNotNull(vol);
        Assert.assertEquals(vol.getMountPath(), expectedPath);
        Assert.assertEquals(vol.getReadOnly().booleanValue(), expectReadonly);
    }

    public static void assertRolesMounted(Collection<Volume> volumes,
                                           Collection<VolumeMount> volumeMounts,
                                           String... roles) {
        List.of(roles)
                .forEach(role -> {
                    final String secret = "token-%s".formatted(role);
                    KubeTestUtil.assertVolumeFromSecret(volumes, secret);
                    KubeTestUtil.assertVolumeMount(volumeMounts, secret,
                            "/pulsar/%s".formatted(secret), true);
                });
    }
}