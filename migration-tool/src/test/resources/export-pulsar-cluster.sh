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

export_dir=${OUTPUT_DIR:-/tmp/exported-pulsar-cluster}
namespace=${NAMESPACE:-pulsar}
mkdir -p $export_dir

kinds=( "configmap" "service" "statefulset" "deployment" "pdb" )
names=( "zookeeper" "broker" "bookkeeper" "bastion" "proxy" "function" "autorecovery" )
grep_arg=""
for name in ${names[@]}; do
  if [[ -z "$grep_arg" ]]; then
    grep_arg="$name"
  else
    grep_arg="$grep_arg\|$name"
  fi
done

for kind in ${kinds[@]}; do
  echo "searching resource type:" $kind

  kubectl get --ignore-not-found ${kind} -n $namespace | grep pulsar- | grep $grep_arg | grep -v 'prome' | grep -v 'grafana'|awk '{print $1}' | \
    while read resource_name; do
      export_to=$export_dir/${kind}-${resource_name}.yaml
      echo "FOUND $resource_name: exporting to $export_to"
      kubectl get $kind $resource_name -o yaml -n $namespace > $export_to
    done
done
echo "exported to $export_dir"