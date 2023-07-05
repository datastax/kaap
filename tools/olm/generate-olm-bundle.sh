#!/usr/bin/env bash
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

# Generates OLM bundle from existing helm chart
#
#
set -euo pipefail

. env.sh
BASEDIR=$(dirname "$(realpath "$0")")
TARGET=${BASEDIR}/target
BUNDLE_VERSION=${BUNDLE_VERSION}
DOCKER_ORG=${DOCKER_ORG}
DOCKER_REGISTRY=${DOCKER_REGISTRY}
TAG=${IMAGE_TAG}

BUNDLE_IMG=${DOCKER_REGISTRY}/${DOCKER_ORG}/kaap-op-bundle:${TAG}
BUNDLE=${TARGET}/${BUNDLE_VERSION}
CATALOG_IMG=${DOCKER_REGISTRY}/${DOCKER_ORG}/kaap-op-catalog:${TAG}
CATDIR="${TARGET}/cat"
OLMTOOL_IMG="olm-utils"
CSV_TEMPLATE_DIR="${BASEDIR}/csv-template"

# Generate bundle in a docker container
generate_olm_bundle() {
  uid="$(id -g ${USER})"
  gid="$(id -u ${USER})"
  cp -r ../../helm ./
  docker build -t "${OLMTOOL_IMG}" -f utils.Dockerfile ${BASEDIR}
  docker run --user="${uid}:${gid}" -v ${BASEDIR}:/olm  "${OLMTOOL_IMG}"
  rm -rf helm
}

# Push bundle and catalog images for testing olm subscription
build_push_bundle(){
  DOCKER_BUILDKIT=1 docker build -f ${CSV_TEMPLATE_DIR}/bundle.Dockerfile -t "${BUNDLE_IMG}" ${BUNDLE}
  docker push "${BUNDLE_IMG}"
}
build_push_catalog(){
  docker build -f ${CSV_TEMPLATE_DIR}/catalog.Dockerfile -t ${CATALOG_IMG} ${CATDIR}
  docker push ${CATALOG_IMG}
}

# Simplest way to build local index(catalog) image without any upgrade path
# opm index add --bundles "${BUNDLE_IMG}" --tag "${CATALOG_IMG}" -c docker
# But we also want to test upgrade from the previous version
# Therefor, building local catalog image using latest file-based-catalog format
# https://olm.operatorframework.io/docs/reference/file-based-catalogs/#opm-generate-dockerfile
# We assume someone runinng an older olm bundle.
# This enables testing upgrade from the previous version using startingCSV in Subscription
generate_fbc_config() {
rm -rf "${CATDIR}"
mkdir -p "${CATDIR}"
CATCONF="${CATDIR}/config.yaml"
OPM_IMAGE="quay.io/operator-framework/opm:latest"
cat <<EOF > "${CATCONF}"
---
defaultChannel: alpha
icon:
  base64data: "TODO"
  mediatype: image/svg+xml
name: kaap
schema: olm.package
---
name: alpha
package: kaap
schema: olm.channel
entries:
- name: kaap.v${BUNDLE_VERSION}
EOF
  docker run ${OPM_IMAGE} render ${BUNDLE_IMG} --output=yaml >> "${CATCONF}"
  docker run -v ${CATDIR}:/cat ${OPM_IMAGE} validate /cat
}

generate_olm_bundle
build_push_bundle

generate_fbc_config
build_push_catalog