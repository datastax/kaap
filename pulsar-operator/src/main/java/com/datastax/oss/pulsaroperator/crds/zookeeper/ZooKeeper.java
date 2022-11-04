package com.datastax.oss.pulsaroperator.crds.zookeeper;

import com.datastax.oss.pulsaroperator.crds.CRDConstants;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;

@Version(CRDConstants.VERSION)
@Group(CRDConstants.GROUP)
@Kind("ZooKeeper")
@Singular("zookeeper")
@Plural("zookeepers")
@ShortNames({"zk"})
public class ZooKeeper extends CustomResource<ZooKeeperFullSpec, ZooKeeperStatus> implements Namespaced {
    @Override
    protected ZooKeeperStatus initStatus() {
        return new ZooKeeperStatus();
    }
}

