package com.datastax.oss.pulsaroperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.datastax.oss.pulsaroperator.crds.SerializationUtil;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.BatchAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.fabric8.kubernetes.client.dsl.V1BatchAPIGroupDSL;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
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
    final List<ResourceInteraction> createdResources = new ArrayList<>();

    public MockKubernetesClient(String namespace) {
        client = mock(KubernetesClient.class);

        final BatchAPIGroupDSL batch = mock(BatchAPIGroupDSL.class);
        final V1BatchAPIGroupDSL v1BatchAPIGroupDSL = mock(V1BatchAPIGroupDSL.class);
        final MixedOperation jobs = mock(MixedOperation.class);

        when(v1BatchAPIGroupDSL.jobs()).thenReturn(jobs);
        when(batch.v1()).thenReturn(v1BatchAPIGroupDSL);
        when(client.batch()).thenReturn(batch);
        when(jobs.inNamespace(eq(namespace))).thenReturn(jobs);
        when(jobs.withName(any())).thenReturn(mock(ScalableResource.class));


        final AppsAPIGroupDSL apps = mock(AppsAPIGroupDSL.class);
        final MixedOperation statefulset = mock(MixedOperation.class);

        when(apps.statefulSets()).thenReturn(statefulset);
        when(client.apps()).thenReturn(apps);
        when(statefulset.inNamespace(eq(namespace))).thenReturn(statefulset);
        when(statefulset.withName(any())).thenReturn(mock(RollableScalableResource.class));



        when(client.resource(any(HasMetadata.class))).thenAnswer((ic) -> {
            final NamespaceableResource interaction =
                    mock(NamespaceableResource.class);
            when(interaction.inNamespace(eq(namespace))).thenReturn(interaction);
            when(interaction.create()).thenAnswer((ic1) -> {
                createdResources.add(new ResourceInteraction(ic.getArgument(0)));
                return null;
            });
            return interaction;
        });


        when(client.resources(any(HasMetadata.class.getClass()))).thenAnswer((ic) -> {
            final MixedOperation interaction = mock(MixedOperation.class);

            final Resource resourceMock = mock(Resource.class);
            when(interaction.resource(any(HasMetadata.class))).thenAnswer(ic2 -> {
                final Resource mockedResource = resourceMock;
                when(mockedResource.create()).thenAnswer((ic3) -> {
                    createdResources.add(new ResourceInteraction(ic2.getArgument(0)));
                    return null;
                });

                when(mockedResource.createOrReplace()).thenAnswer((ic3) -> {
                    createdResources.add(new ResourceInteraction(ic2.getArgument(0)));
                    return null;
                });
                return mockedResource;
            });
            when(interaction.inNamespace(eq(namespace))).thenReturn(interaction);
            when(interaction.withName(any())).thenReturn(resourceMock);
            return interaction;
        });
        when(client.getKubernetesVersion()).thenReturn(new VersionInfo.Builder()
                .withMajor("1")
                .withMinor("25").build());

    }

    public int countCreatedResources() {
        return createdResources.size();
    }
    public <T extends HasMetadata> ResourceInteraction<T> getCreatedResource(Class<T> castTo) {
        final List<ResourceInteraction<T>> res = getCreatedResources(castTo);
        return res.isEmpty() ? null : res.get(0);
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

    @SneakyThrows
    public static <T> T readYaml(String yaml, Class<T> toClass) {
        return SerializationUtil.readYaml(yaml, toClass);
    }
}
