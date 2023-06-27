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
package com.datastax.oss.k8saap.mocks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import com.datastax.oss.k8saap.common.SerializationUtil;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetList;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.V1NetworkAPIGroupDSL;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.BatchAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.Gettable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.kubernetes.client.dsl.NetworkAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.PolicyAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.RbacAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.fabric8.kubernetes.client.dsl.StorageAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.V1BatchAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.V1PolicyAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.V1StorageAPIGroupDSL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@Getter
public class MockKubernetesClient {

    @Data
    @AllArgsConstructor
    public static class ResourceInteraction<T extends HasMetadata> {
        T resource;

        @SneakyThrows
        public String getResourceYaml() {
            return SerializationUtil.writeAsYaml(resource);
        }
    }

    final KubernetesClient client;
    final MockResourcesResolver resourcesResolver;
    final List<ResourceInteraction> createdResources = new ArrayList<>();
    final List<ResourceInteraction> deletedResources = new ArrayList<>();

    public MockKubernetesClient(String namespace) {
        this(namespace, null);
    }

    public MockKubernetesClient(String namespace, MockResourcesResolver resourcesResolver) {
        client = Mockito.mock(KubernetesClient.class);
        if (resourcesResolver == null) {
            resourcesResolver = new MockResourcesResolver();
        }
        this.resourcesResolver = resourcesResolver;
        mockExistingResources(namespace);
        mockAndInterceptResourceCreation(namespace);
        mockK8sVersion();

    }

    private void mockK8sVersion() {
        when(client.getKubernetesVersion()).thenReturn(new VersionInfo.Builder()
                .withMajor("1")
                .withMinor("25").build());
    }

    private void mockExistingResources(String namespace) {
        final V1BatchAPIGroupDSL v1BatchAPIGroupDSL = mockBatchV1();
        when(v1BatchAPIGroupDSL.jobs()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, Job.class, name -> resourcesResolver.jobWithName(name))
        );


        when(client.secrets()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, Secret.class, name -> resourcesResolver.secretWithName(name))
        );
        when(client.configMaps()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, ConfigMap.class,
                        name -> resourcesResolver.configMapWithName(name))
        );
        when(client.services()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, Service.class, name -> resourcesResolver.serviceWithName(name))
        );

        when(client.pods()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, Pod.class, name -> resourcesResolver.podWithName(name))
        );
        when(client.persistentVolumeClaims()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, PersistentVolumeClaim.class,
                        name -> resourcesResolver.pvcWithName(name))
        );

        final V1StorageAPIGroupDSL storage = mockStorage();
        when(storage.storageClasses()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, StorageClass.class,
                        name -> resourcesResolver.storageClassByName(name))
        );

        final AppsAPIGroupDSL apps = mockApps();
        when(apps.statefulSets()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, StatefulSet.class,
                        name -> resourcesResolver.statefulSetWithName(name))
        );
        when(apps.deployments()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, Deployment.class,
                        name -> resourcesResolver.deploymentWithName(name))
        );

        when(apps.replicaSets()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, ReplicaSet.class,
                        name -> resourcesResolver.replicaSetWithName(name))
        );
        final V1PolicyAPIGroupDSL v1PolicyAPIGroupDSL = mockPolicy();
        when(v1PolicyAPIGroupDSL.podDisruptionBudget()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, PodDisruptionBudget.class,
                        name -> resourcesResolver.podDisruptionBudgetWithName(name)));


        final V1NetworkAPIGroupDSL v1NetworkAPIGroupDSL = mockNetwork();
        when(v1NetworkAPIGroupDSL.ingresses()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, Ingress.class,
                        name -> null));

        when(client.serviceAccounts()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, ServiceAccount.class,
                        name -> null));

        final RbacAPIGroupDSL rbacAPIGroupDSL = mockRbac();
        when(rbacAPIGroupDSL.roles()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, Role.class,
                        name -> null));

        when(rbacAPIGroupDSL.roleBindings()).thenAnswer(__ ->
                mockExistingResourceByName(namespace, RoleBinding.class,
                        name -> null));

    }

    private void mockAndInterceptResourceCreation(String namespace) {
        when(client.resource(any(HasMetadata.class))).thenAnswer(ic -> {
            final NamespaceableResource interaction =
                    Mockito.mock(NamespaceableResource.class);
            when(interaction.inNamespace(eq(namespace))).thenReturn(interaction);
            when(interaction.create()).thenAnswer(ic1 -> {
                addCreatedResource(ic);
                return null;
            });
            when(interaction.createOrReplace()).thenAnswer(ic1 -> {
                addCreatedResource(ic);
                return null;
            });
            when(interaction.delete()).thenAnswer(ic1 -> {
                addDeletedResource(ic);
                return null;
            });
            return interaction;
        });


        when(client.resources(any(HasMetadata.class.getClass()))).thenAnswer(ic -> {
            final MixedOperation interaction = Mockito.mock(MixedOperation.class);

            final Resource resourceMock = Mockito.mock(Resource.class);
            when(interaction.resource(any(HasMetadata.class))).thenAnswer(ic2 -> {
                final Resource mockedResource = resourceMock;
                when(mockedResource.create()).thenAnswer(ic3 -> {
                    addCreatedResource(ic2);
                    return null;
                });

                when(mockedResource.createOrReplace()).thenAnswer(ic3 -> {
                    addCreatedResource(ic2);
                    return null;
                });
                return mockedResource;
            });
            when(interaction.inNamespace(eq(namespace))).thenReturn(interaction);
            when(interaction.withName(any())).thenReturn(resourceMock);
            return interaction;
        });
    }

    private void addCreatedResource(InvocationOnMock ic) {
        final HasMetadata argument = ic.getArgument(0);
        createdResources.add(new ResourceInteraction(argument));
    }

    private void addDeletedResource(InvocationOnMock ic) {
        final HasMetadata argument = ic.getArgument(0);
        deletedResources.add(new ResourceInteraction(argument));
    }

    @SneakyThrows
    private void addDeletedResource(String name, Class<? extends HasMetadata> resourceClass) {
        final HasMetadata deletedResource = resourceClass.getConstructor().newInstance();
        deletedResource.setMetadata(new ObjectMetaBuilder()
                .withName(name)
                .build()
        );
        deletedResources.add(new ResourceInteraction(deletedResource));
    }

    private V1BatchAPIGroupDSL mockBatchV1() {
        final V1BatchAPIGroupDSL v1BatchAPIGroupDSL = Mockito.mock(V1BatchAPIGroupDSL.class);
        final BatchAPIGroupDSL batch = Mockito.mock(BatchAPIGroupDSL.class);
        when(batch.v1()).thenReturn(v1BatchAPIGroupDSL);
        when(client.batch()).thenReturn(batch);
        return v1BatchAPIGroupDSL;
    }

    private AppsAPIGroupDSL mockApps() {
        final AppsAPIGroupDSL apps = Mockito.mock(AppsAPIGroupDSL.class);
        when(client.apps()).thenReturn(apps);
        return apps;
    }

    private V1PolicyAPIGroupDSL mockPolicy() {
        final PolicyAPIGroupDSL policies = Mockito.mock(PolicyAPIGroupDSL.class);
        final V1PolicyAPIGroupDSL v1 = Mockito.mock(V1PolicyAPIGroupDSL.class);
        when(policies.v1()).thenReturn(v1);
        when(client.policy()).thenReturn(policies);
        return v1;
    }

    private V1NetworkAPIGroupDSL mockNetwork() {
        final NetworkAPIGroupDSL network = Mockito.mock(NetworkAPIGroupDSL.class);
        final V1NetworkAPIGroupDSL v1 = Mockito.mock(V1NetworkAPIGroupDSL.class);
        when(network.v1()).thenReturn(v1);
        when(client.network()).thenReturn(network);
        return v1;
    }

    private RbacAPIGroupDSL mockRbac() {
        final RbacAPIGroupDSL rbac = Mockito.mock(RbacAPIGroupDSL.class);
        when(client.rbac()).thenReturn(rbac);
        return rbac;
    }

    private V1StorageAPIGroupDSL mockStorage() {
        final StorageAPIGroupDSL storage = Mockito.mock(StorageAPIGroupDSL.class);
        final V1StorageAPIGroupDSL v1 = Mockito.mock(V1StorageAPIGroupDSL.class);
        when(storage.v1()).thenReturn(v1);
        when(client.storage()).thenReturn(storage);
        return v1;
    }

    private <T extends HasMetadata> MixedOperation mockExistingResourceByName(String namespace, Class<T> resourceClass,
                                                                              Function<String, T> resolver) {


        final MixedOperation resourceOp = Mockito.mock(MixedOperation.class);
        final NonNamespaceOperation nonNamespaceOperation = Mockito.mock(NonNamespaceOperation.class);
        when(resourceOp.inNamespace(eq(namespace))).thenReturn(nonNamespaceOperation);

        AtomicReference<Map<String, String>> labels = new AtomicReference<>();
        when(nonNamespaceOperation.withLabels(any(Map.class))).thenAnswer(
                invocation -> {
                    labels.set((Map<String, String>) invocation.getArguments()[0]);
                    return nonNamespaceOperation;
                });

        final Answer withNameAnswer = get -> {
            final Class<? extends Gettable> rClass;
            if (resourceClass == Service.class) {
                rClass = ServiceResource.class;
            } else if (resourceClass == StatefulSet.class || resourceClass == Deployment.class
                    || resourceClass == ReplicaSet.class) {
                rClass = RollableScalableResource.class;
            } else if (resourceClass == Job.class) {
                rClass = ScalableResource.class;
            } else if (resourceClass == Pod.class) {
                rClass = PodResource.class;
            } else {
                rClass = Resource.class;
            }

            final Resource<HasMetadata> resource = (Resource<HasMetadata>) Mockito.mock(rClass);
            doAnswer(ignore -> {
                final String name = get.getArgument(0);

                final T resolved = resolver.apply(name);
                if (resolved != null) {
                    resolved.getMetadata().setNamespace(namespace);
                }
                return resolved;
            }).when(resource).get();
            when(resource.delete()).thenAnswer(ic -> {
                final String name = get.getArgument(0);
                addDeletedResource(name, resourceClass);
                return null;
            });
            return resource;
        };
        doAnswer(withNameAnswer).when(nonNamespaceOperation).withName(any());
        doAnswer(withNameAnswer).when(resourceOp).withName(any());

        class ListImpl implements KubernetesResourceList, KubernetesResource {
            @Override
            public ListMeta getMetadata() {
                return null;
            }

            @Override
            public List getItems() {
                return resourcesResolver.getResources(resourceClass, labels.get());
            }
        }
        if (resourceClass == ReplicaSet.class) {
            doAnswer(
                    get -> new ReplicaSetList(null, resourcesResolver.getResources(ReplicaSet.class, labels.get()),
                            null, null)).when(nonNamespaceOperation).list();
        } else if (resourceClass == Pod.class) {
            doAnswer(
                    get -> new PodList(null, resourcesResolver.getResources(Pod.class, labels.get()),
                            null, null)).when(nonNamespaceOperation).list();
        } else if (resourceClass == PersistentVolumeClaim.class) {
            doAnswer(
                    get -> new PersistentVolumeClaimList(null,
                            resourcesResolver.getResources(PersistentVolumeClaim.class, labels.get()),
                            null, null)).when(nonNamespaceOperation).list();
        } else {
            doAnswer(get -> new ListImpl()).when(nonNamespaceOperation).list();
        }


        return resourceOp;
    }

    public int countCreatedResources() {
        return createdResources.size();
    }

    public <T extends HasMetadata> ResourceInteraction<T> getCreatedResource(Class<T> castTo) {
        final List<ResourceInteraction<T>> res = getCreatedResources(castTo);
        return res.isEmpty() ? null : res.get(0);
    }

    public <T extends HasMetadata> ResourceInteraction<T> getCreatedResource(Class<T> castTo, String name) {
        final List<ResourceInteraction<T>> res = getCreatedResources(castTo);
        return res
                .stream()
                .filter(r -> r.getResource().getMetadata().getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    public <T extends HasMetadata> List<ResourceInteraction<T>> getCreatedResources(Class<T> castTo) {
        List<ResourceInteraction<T>> res = new ArrayList<>();
        for (ResourceInteraction createdResource : createdResources) {
            if (createdResource.getResource().getClass().isAssignableFrom(castTo)) {
                res.add(createdResource);
            }
        }
        return res;
    }

    public <T extends HasMetadata> List<ResourceInteraction<T>> getDeletedResources(Class<T> castTo) {
        List<ResourceInteraction<T>> res = new ArrayList<>();
        for (ResourceInteraction createdResource : deletedResources) {
            if (createdResource.getResource().getClass().isAssignableFrom(castTo)) {
                res.add(createdResource);
            }
        }
        return res;
    }

    public <T extends HasMetadata> ResourceInteraction<T> getDeletedResource(Class<T> castTo, String name) {
        final List<ResourceInteraction<T>> res = getDeletedResources(castTo);
        return res
                .stream()
                .filter(r -> r.getResource().getMetadata().getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @SneakyThrows
    public static <T> T readYaml(String yaml, Class<T> toClass) {
        return SerializationUtil.readYaml(yaml, toClass);
    }
}
