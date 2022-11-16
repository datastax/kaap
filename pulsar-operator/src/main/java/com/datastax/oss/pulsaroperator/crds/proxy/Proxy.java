package com.datastax.oss.pulsaroperator.crds.proxy;

import com.datastax.oss.pulsaroperator.crds.BaseComponentStatus;
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
@Kind("Proxy")
@Singular("proxy")
@Plural("proxies")
@ShortNames({"pr"})
public class Proxy extends CustomResource<ProxyFullSpec, BaseComponentStatus> implements Namespaced {
    @Override
    protected BaseComponentStatus initStatus() {
        return new BaseComponentStatus();
    }
}

