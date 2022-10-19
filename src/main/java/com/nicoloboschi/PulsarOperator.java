package com.nicoloboschi;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("com.nicoloboschi")
public class PulsarOperator extends CustomResource<PulsarOperatorSpec, PulsarOperatorStatus> implements Namespaced {}

