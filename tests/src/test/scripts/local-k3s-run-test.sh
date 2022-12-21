#!/bin/bash
set -e

mvn_or_mvnd() {
  if command -v mvnd &> /dev/null; then
    mvnd $mvnmod "$@"
  else
    mvn $mvnmod "$@"
  fi
}

wait_image() {
  local image_name=$1
  docker_output=""
  while $(echo $docker_output | grep -q -v "$image_name"); do
    docker_output=$(docker exec -it pulsaroperator-local-k3s ctr -a /run/k3s/containerd/containerd.sock image ls 2>&1)
    echo "waiting for the local k3s server to load image $image_name";
    sleep 2
  done
}
this_dir=$( dirname -- "${BASH_SOURCE[0]}" )

wait_image pulsar-operator
wait_image lunastreaming

export KUBECONFIG=/tmp/pulsaroperator-local-k3s-kube-config
mvn_or_mvnd -f $this_dir/../../../pom.xml test -Dpulsaroperator.tests.env.existing \
  -Dpulsaroperator.tests.existingenv.kubeconfig.context=default \
  -Dpulsaroperator.tests.existingenv.storageclass=local-path "$@"
