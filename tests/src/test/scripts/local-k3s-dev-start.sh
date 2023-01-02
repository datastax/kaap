#!/bin/bash
this_dir=$( dirname -- "${BASH_SOURCE[0]}" )
export KUBECONFIG=/tmp/pulsaroperator-local-k3s-kube-config
kubectl config set-context --current --namespace=ns
mvn -f $this_dir/../../../../pulsar-operator/pom.xml quarkus:dev