#!/bin/bash
set -e

mvn_or_mvnd() {
  if command -v mvnd &> /dev/null; then
    mvnd $mvnmod "$@"
  else
    mvn $mvnmod "$@"
  fi
}
this_dir=$( dirname -- "${BASH_SOURCE[0]}" )
export KUBECONFIG=/tmp/pulsaroperator-local-k3s-kube-config
mvn_or_mvnd -f $this_dir/../../../pom.xml test -Dpulsaroperator.tests.env.existing \
  -Dpulsaroperator.tests.existingenv.kubeconfig.context=default \
  -Dpulsaroperator.tests.existingenv.storageclass=local-path "$@"
