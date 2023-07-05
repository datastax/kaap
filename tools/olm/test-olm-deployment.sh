#!/bin/bash
################################################################################
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
# limitations under the License.
################################################################################
set +x

source ./env.sh
yes_or_no() {
	read -p "$1 (y/n)? " choice
	case "$choice" in
		y|Y ) ;;
		n|N ) exit 1;;
		* ) yes_or_no "$1";;
	esac
}
./generate-olm-bundle.sh

current_context=$(kubectl config current-context)
if [[ -z "$current_context" ]]; then
  echo "No kubectl context is set. Please set a context and try again."
  exit 1
fi
current_namespace=$(kubectl config get-contexts "$current_context" | tail -n1 | awk '{print $5}')
yes_or_no "The pulsar cluster will be installed in the context $current_context, namespace $current_namespace."
curl -sL https://github.com/operator-framework/operator-lifecycle-manager/releases/download/v0.22.0/install.sh | bash -s v0.22.0

# Deploy the catalog src
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: olm-kaap-catalog
  namespace: default
spec:
  sourceType: grpc
  image: "${DOCKER_REGISTRY}/${DOCKER_ORG}/kaap-op-catalog:${IMAGE_TAG}"
EOF

# Deploy the subscription in the default namespace:
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha2
kind: OperatorGroup
metadata:
  name: default-og
  namespace: default
spec:
  # if not set, default to watch all namespaces
  targetNamespaces:
  - default
---
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: kaap
  namespace: default
spec:
  channel: alpha
  name: kaap
  source: olm-kaap-catalog
  sourceNamespace: default
EOF

cat <<EOF | kubectl apply -f -
global:
    name: pulsar
    image: apachepulsar/pulsar:3.0.0
    restartOnConfigMapChange: true
zookeeper:
  replicas: 1
  dataVolume:
    name: data
    size: 100M
  resources:
    requests:
      cpu: "0.2"
      memory: "128Mi"
bookkeeper:
  replicas: 1
  volumes:
    journal:
      size: 1Gi
    ledgers:
      size: 1Gi
  resources:
    requests:
      cpu: "0.2"
      memory: "128Mi"
broker:
  replicas: 1
  config:
    managedLedgerDefaultAckQuorum: "1"
    managedLedgerDefaultEnsembleSize: "1"
    managedLedgerDefaultWriteQuorum: "1"
  resources:
    requests:
      cpu: "0.2"
      memory: "128Mi"
proxy:
  replicas: 1
  resources:
    requests:
      cpu: "0.2"
      memory: "128Mi"
  webSocket:
    resources:
      requests:
        cpu: "0.2"
        memory: "128Mi"
autorecovery:
  replicas: 1
  resources:
    requests:
      cpu: "0.2"
      memory: "128Mi"
bastion:
  replicas: 1
  resources:
    requests:
      cpu: "0.2"
      memory: "128Mi"
functionsWorker:
  replicas: 1
  runtime: kubernetes
  config:
    numFunctionPackageReplicas: 1
    functionInstanceMaxResources:
      disk: 10000000000
      ram: 500000000
      cpu: 0.3
  resources:
    requests:
      cpu: "0.3"
EOF

