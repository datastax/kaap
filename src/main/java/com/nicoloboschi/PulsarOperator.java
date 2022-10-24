package com.nicoloboschi;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.Singular;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("com.nicoloboschi")
@Kind("PulsarOperator")
@Singular("pulsaroperator")
@Plural("pulsaroperators")
public class PulsarOperator extends CustomResource<PulsarOperatorSpec, PulsarOperatorStatus> implements Namespaced {}

