#!/bin/bash
set -e
echo "Generating CRDs docs"
mvn package -Pupdate-crds -pl pulsar-operator
docker run -u $(id -u):$(id -g) --rm -v ${PWD}:/workdir ghcr.io/fybrik/crdoc:latest \
  --resources /workdir/helm/pulsar-operator/crds/pulsarclusters.pulsar.oss.datastax.com-v1.yml \
  --template /workdir/src/pulsaroperator-markdown-template.tmpl \
  --output /workdir/docs/api-reference.md
echo "Generated docs at docs/api-reference.md"