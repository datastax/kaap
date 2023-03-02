#!/bin/bash
#
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

set -e

mvn_or_mvnd() {
  if command -v mvnd &> /dev/null; then
    mvnd $mvnmod "$@"
  else
    mvn $mvnmod "$@"
  fi
}

wait_container() {
  local container_name=$1
  echo "check for the container $container_name to be up and running"
  while true; do
    if docker inspect -f '{{.State.Running}}' $container_name 2>&1 | grep -q true; then
      break
    fi
    echo "waiting for the container $container_name to be up and running"
    sleep 1
  done
}

wait_image() {
  local image_name=$1
  echo "check for the local k3s server to load image $image_name"
  while true; do
    docker_output=$(docker exec -it pulsaroperator-local-k3s ctr -a /run/k3s/containerd/containerd.sock image ls 2>&1)
    if (echo "$docker_output" | grep -q $image_name); then
        break
    fi
    echo "waiting for the local k3s server to load image $image_name"
    sleep 1
  done
}
this_dir=$( dirname -- "${BASH_SOURCE[0]}" )

docker inspect pulsaroperator-local-k3s-network  | jq -r '.[0].Containers[].Name' | while read container; do
  wait_container $container
done

wait_image lunastreaming-operator
wait_image lunastreaming

mvn_or_mvnd -f $this_dir/../../../pom.xml test -Dpulsaroperator.tests.env.existing \
  -Dpulsaroperator.tests.existingenv.kubeconfig.context=pulsaroperator-local-k3s \
  -Dpulsaroperator.tests.existingenv.helmcontainer.network=pulsaroperator-local-k3s-network \
  -Dpulsaroperator.tests.existingenv.kubeconfig.overrideserver="https://pulsaroperator-local-k3s:6443" \
  -Dpulsaroperator.tests.existingenv.storageclass=local-path "$@"
