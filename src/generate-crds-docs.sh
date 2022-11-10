#!/bin/bash
set -e
echo "Generating CRDs docs"
mvn package -Pupdate-crds -pl pulsar-operator
docker run -u $(id -u):$(id -g) --rm -v ${PWD}:/workdir ghcr.io/fybrik/crdoc:latest --resources /workdir/helm/pulsar-operator/crds --output /workdir/docs/crds.md
echo "Generated docs at docs/crds.md"