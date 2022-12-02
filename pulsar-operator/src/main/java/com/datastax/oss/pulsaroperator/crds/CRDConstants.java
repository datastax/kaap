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


    private CRDConstants() {
    }


}
