package com.datastax.oss.pulsaroperator.crds.autorecovery;

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
@Kind("Autorecovery")
@Singular("autorecovery")
@Plural("autorecoveries")
@ShortNames({"au", "aur"})
public class Autorecovery extends CustomResource<AutorecoveryFullSpec, BaseComponentStatus> implements Namespaced {
    @Override
    protected BaseComponentStatus initStatus() {
        return new BaseComponentStatus();
    }
}

