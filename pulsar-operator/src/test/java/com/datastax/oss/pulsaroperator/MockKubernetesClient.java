package com.datastax.oss.pulsaroperator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NamespaceVisitFromServerGetWatchDeleteRecreateWaitApplicable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Getter
public class MockKubernetesClient {
    @Data
    @AllArgsConstructor
    public static class ResourceInteraction {
        HasMetadata resource;
        NamespaceVisitFromServerGetWatchDeleteRecreateWaitApplicable interaction;
    }

    final KubernetesClient client;
    final List<ResourceInteraction> createdResources = new ArrayList<>();

    public MockKubernetesClient(String namespace) {
        client = mock(KubernetesClient.class);
        when(client.resource(any(HasMetadata.class))).thenAnswer((ic) -> {
            final NamespaceVisitFromServerGetWatchDeleteRecreateWaitApplicable interaction =
                    mock(NamespaceVisitFromServerGetWatchDeleteRecreateWaitApplicable.class);
            when(interaction.inNamespace(eq(namespace))).thenReturn(interaction);
            createdResources.add(new ResourceInteraction(ic.getArgument(0), interaction));
            return interaction;
        });
    }
}
