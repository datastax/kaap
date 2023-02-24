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
package com.datastax.oss.pulsaroperator.controllers;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
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

    public static void assertVolumeFromSecret(Collection<Volume> volumes, String volumeName, String secretName) {
        final Volume vol = KubeTestUtil.getVolumeByName(volumes, volumeName);
        Assert.assertNotNull(vol, "volume %s not found".formatted(volumeName));
        Assert.assertEquals(vol.getSecret().getSecretName(), secretName);
    }

    public static void assertVolumeFromSecret(Collection<Volume> volumes, String volumeAndSecretName) {
        assertVolumeFromSecret(volumes, volumeAndSecretName, volumeAndSecretName);
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

    public static void assertTlsVolumesMounted(Deployment deployment,
                                               String secretName) {
        assertTlsVolumesMounted(
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts(),
                deployment.getSpec().getTemplate().getSpec().getVolumes(),
                secretName
        );
    }

    public static void assertTlsVolumesMounted(StatefulSet sts,
                                               String secretName) {
        assertTlsVolumesMounted(
                sts.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts(),
                sts.getSpec().getTemplate().getSpec().getVolumes(),
                secretName
        );
    }

    public static void assertTlsVolumesMounted(Collection<VolumeMount> volumeMounts,
                                               Collection<Volume> volumes,
                                               String secretName) {
        assertVolumeMount(volumeMounts, "certs", "/pulsar/certs", true);
        assertVolumeFromSecret(volumes, "certs", secretName);
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

    public static Container getContainerByName(Collection<Container> containers, String name) {
        for (Container container : containers) {
            if (container.getName().equals(name)) {
                return container;
            }
        }
        return null;
    }
}
