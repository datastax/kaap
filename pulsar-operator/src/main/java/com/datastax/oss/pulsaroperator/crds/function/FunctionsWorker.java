package com.datastax.oss.pulsaroperator.crds.function;

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
@Kind("FunctionsWorker")
@Singular("functionsworker")
@Plural("functionsworkers")
@ShortNames({"fn", "fnw", "fw"})
public class FunctionsWorker extends CustomResource<FunctionsWorkerFullSpec, BaseComponentStatus> implements Namespaced {
    @Override
    protected BaseComponentStatus initStatus() {
        return new BaseComponentStatus();
    }
}

