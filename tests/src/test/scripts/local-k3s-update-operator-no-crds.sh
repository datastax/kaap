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
mvn_or_mvnd -f $this_dir/../../../../operator/pom.xml package -am -Dcheckstyle.skip -Dspotbugs.skip -DskipTests -Pskip-crds
GENERATE_IMAGE_DIGEST_TARGET=$tmp_dir/operator.bin mvn_or_mvnd -f $this_dir/../../../pom.xml test -Dtest="LocalK8sEnvironment#updateImage"
echo "image digest generated: $tmp_dir/operator.bin"
echo "copying image into container $container"

docker inspect k8saap-local-k3s-network  | jq -r '.[0].Containers[].Name' | while read container; do
  docker cp $tmp_dir/operator.bin $container:/tmp/operator.bin
  echo "image digest copied into container $container"
  docker exec -t $container sh -c "(ctr image rm docker.io/datastax/lunastreaming-operator:latest-dev) || (docker image rm -f docker.io/datastax/lunastreaming-operator:latest-dev) || echo 'not able to remove'"
  echo "importing image in $container"
  docker exec -t $container sh -c "(ctr image import /tmp/operator.bin) || (docker image load /tmp/operator.bin) || echo 'not able to import'"
  echo "image imported in $container"
done

