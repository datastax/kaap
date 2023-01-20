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
