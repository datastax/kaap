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
tmp_dir=$(mktemp -d)
mvn_or_mvnd -f $this_dir/../../../../pulsar-operator/pom.xml package -Dcheckstyle.skip -Dspotbugs.skip -DskipTests
mvn_or_mvnd -f $this_dir/../../../pom.xml test-compile exec:java -Dexec.classpathScope=test  -Dexec.mainClass="com.datastax.oss.pulsaroperator.tests.LocalK8sEnvironment\$GenerateImageDigest" -Dexec.args="$tmp_dir/pulsar-operator.bin"

container="pulsaroperator-local-k3s"
docker cp $tmp_dir/pulsar-operator.bin $container:/tmp/pulsar-operator.bin
echo "image digest copied into container $container"
docker exec -it $container ctr -a /run/k3s/containerd/containerd.sock image rm docker.io/datastax/pulsar-operator:latest
docker exec -it $container ctr -a /run/k3s/containerd/containerd.sock image import /tmp/pulsar-operator.bin
echo "image imported in $container"