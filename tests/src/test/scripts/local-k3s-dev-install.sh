#!/bin/bash
this_dir=$( dirname -- "${BASH_SOURCE[0]}" )
export KUBECONFIG=/tmp/pulsaroperator-local-k3s-kube-config
kubectl apply -f $this_dir/../../../../helm/examples/local-k3s.yaml