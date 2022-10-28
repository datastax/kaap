package com.datastax.oss.pulsaroperator.crd.zookeeper;

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
@Kind("ZooKeeper")
@Singular("zookeeper")
@Plural("zookeepers")
@ShortNames({"zk"})
public class ZooKeeper extends CustomResource<ZooKeeperSpec, ZooKeeperStatus> implements Namespaced {
    @Override
    protected ZooKeeperStatus initStatus() {
        return new ZooKeeperStatus();
    }
}

