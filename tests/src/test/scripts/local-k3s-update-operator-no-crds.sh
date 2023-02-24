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
this_dir=$( dirname -- "${BASH_SOURCE[0]}" )
tmp_dir=$(mktemp -d)
mvn_or_mvnd -f $this_dir/../../../../pulsar-operator/pom.xml package -Dcheckstyle.skip -Dspotbugs.skip -DskipTests -Dquarkus.operator-sdk.crd.generate=false
GENERATE_IMAGE_DIGEST_TARGET=$tmp_dir/pulsar-operator.bin mvn_or_mvnd -f $this_dir/../../../pom.xml test -Dtest="LocalK8sEnvironment#updateImage"
echo "image digest generated: $tmp_dir/pulsar-operator.bin"
echo "copying image into container $container"

container="pulsaroperator-local-k3s"
docker cp $tmp_dir/pulsar-operator.bin $container:/tmp/pulsar-operator.bin
echo "image digest copied into container $container"
docker exec -it $container ctr -a /run/k3s/containerd/containerd.sock image rm docker.io/datastax/lunastreaming-operator:latest-dev
echo "importing image in $container"
docker exec -it $container ctr -a /run/k3s/containerd/containerd.sock image import /tmp/pulsar-operator.bin
echo "image imported in $container"