package com.datastax.oss.pulsaroperator.crd.cluster;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("com.datastax.oss")
@Kind("PulsarCluster")
@Singular("pulsarcluster")
@Plural("pulsarclusters")
@ShortNames({"pulsar"})
public class PulsarCluster extends CustomResource<PulsarClusterSpec, PulsarClusterStatus> implements Namespaced {
    @Override
    protected PulsarClusterStatus initStatus() {
        return new PulsarClusterStatus();
    }
}
