/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.pulsaroperator.crds;

public final class CRDConstants {
    public static final String VERSION = "v1alpha1";
    public static final String GROUP = "pulsar.oss.datastax.com";

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
    public static final String DOC_TOLERATIONS = "Pod tolerations.";

    public static final String DOC_SERVICE_ANNOTATIONS = "Additional annotations to add to the Service.";
    public static final String DOC_SERVICE_PORTS = "Additional ports to add to the Service.";



    private CRDConstants() {
    }


}
