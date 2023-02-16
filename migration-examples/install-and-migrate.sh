#!/bin/bash
#
# Copyright DataStax, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


set -e -o pipefail


yes_or_no() {
	read -p "$1 (y/n)? " choice
	case "$choice" in
		y|Y ) ;;
		n|N ) exit 1;;
		* ) yes_or_no "$1";;
	esac
}

this_dir=$( dirname -- "${BASH_SOURCE[0]}" )

current_context=$(kubectl config current-context)
if [[ -z "$current_context" ]]; then
  echo "No kubectl context is set. Please set a context and try again."
  exit 1
fi
current_namespace=$(kubectl config get-contexts "$current_context" | tail -n1 | awk '{print $5}')
yes_or_no "The pulsar cluster will be installed in the context $current_context, namespace $current_namespace."

helm repo add datastax-pulsar https://datastax.github.io/pulsar-helm-chart && helm repo update
kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.8.0/cert-manager.crds.yaml
helm install pulsar -f $this_dir/pulsar-chart-values.yaml --wait datastax-pulsar/pulsar --debug

tempdir=$(mktemp -d)
echo "context: $current_context
namespace: $current_namespace
clusterName: pulsar" > $tempdir/input-specs.yaml

$this_dir/../bin/migration-tool generate -i $tempdir/input-specs.yaml -o $tempdir/outputs

# Install the pulsar operator
helm install pulsar-operator $this_dir/../helm/pulsar-operator -f $this_dir/pulsar-operator-values.yaml  --debug

kubectl apply -f $tempdir/outputs/$current_context/crd-generated-pulsar-cluster-*.json

kubectl delete secret -l name=pulsar,owner=helm