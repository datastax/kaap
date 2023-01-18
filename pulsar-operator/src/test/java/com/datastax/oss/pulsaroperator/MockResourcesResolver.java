package com.datastax.oss.pulsaroperator;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;

public class MockResourcesResolver {

    public Secret secretWithName(String name) {
        return null;
    }

    public StatefulSet statefulSetWithName(String name) {
        return null;
    }

    protected StatefulSetBuilder baseStatefulSetBuilder(String name, boolean ready) {
        return new StatefulSetBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .withNewStatus()
                .withReplicas(1)
                .withReadyReplicas(ready ? 1 : 0)
                .endStatus();
    }

}
