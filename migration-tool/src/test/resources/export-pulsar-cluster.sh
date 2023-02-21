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