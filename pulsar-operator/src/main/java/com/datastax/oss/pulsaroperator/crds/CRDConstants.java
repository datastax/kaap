package com.datastax.oss.pulsaroperator.crds;

public final class CRDConstants {
    public static final String VERSION = "v1alpha1";
    public static final String GROUP = "com.datastax.oss";

    public static final String LABEL_APP = "app";
    public static final String LABEL_COMPONENT = "component";
    public static final String LABEL_CLUSTER = "cluster";

    public static final String CONDITIONS_STATUS_TRUE = "True";
    public static final String CONDITIONS_STATUS_FALSE = "False";
    public static final String CONDITIONS_STATUS_UNKNOWN = "Unknown";

    public static final String CONDITIONS_TYPE_READY = "Ready";
    public static final String CONDITIONS_TYPE_READY_REASON_GENERIC_ERROR = "ReconciliationError";
    public static final String CONDITIONS_TYPE_READY_REASON_INVALID_SPEC = "InvalidSpec";
    public static final String CONDITIONS_TYPE_READY_REASON_INITIALIZING = "Initializing";
    public static final String CONDITIONS_TYPE_READY_REASON_DISABLED = "Disabled";

    public static final String DOC_IMAGE = "Override Pulsar image.";
    public static final String DOC_IMAGE_PULL_POLICY = "Override image pull policy.";
    public static final String DOC_NODE_SELECTORS = "Additional node selectors.";
    public static final String DOC_ANNOTATIONS = "Annotations to add to each resource.";
    public static final String DOC_CONFIG = "Configuration.";
    public static final String DOC_REPLICAS = "Number of desired replicas.";
    public static final String DOC_GRACE_PERIOD = "Termination grace period in seconds.";
    public static final String DOC_RESOURCES = "Resources requirements.";

    public static final String DOC_SERVICE_ANNOTATIONS = "Additional annotations to add to the Service.";
    public static final String DOC_SERVICE_PORTS = "Additional ports to add to the Service.";



    private CRDConstants() {
    }


}
