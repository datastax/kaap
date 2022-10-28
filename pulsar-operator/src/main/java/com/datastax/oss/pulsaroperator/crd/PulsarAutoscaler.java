package com.datastax.oss.pulsaroperator.crd;

import com.datastax.oss.pulsaroperator.reconcilier.PulsarAutoscalerSpec;
import com.datastax.oss.pulsaroperator.reconcilier.PulsarAutoscalerStatus;
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
@Kind("PulsarAutoscaler")
@Singular("pulsarautoscaler")
@Plural("pulsarautoscalers")
@ShortNames({"pas"})
public class PulsarAutoscaler extends CustomResource<PulsarAutoscalerSpec, PulsarAutoscalerStatus> implements Namespaced {
    @Override
    protected PulsarAutoscalerStatus initStatus() {
        return new PulsarAutoscalerStatus();
    }
}

