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
echo "Generating CRDs docs"
mvn package -Pupdate-crds -pl operator
docker run -u $(id -u):$(id -g) --rm -v ${PWD}:/workdir ghcr.io/fybrik/crdoc:latest \
  --resources /workdir/helm/k8saap/crds/pulsarclusters.k8saap.oss.datastax.com-v1.yml \
  --template /workdir/src/reference-markdown-template.tmpl \
  --output /workdir/docs/api-reference.md
echo "Generated docs at docs/api-reference.md"