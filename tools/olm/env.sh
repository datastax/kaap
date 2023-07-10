#!/bin/bash
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

#
# Change this file to your need

# Specify the release version for this bundle.
BUNDLE_VERSION=0.1.0

# Specify an image registry repo to push the bundle and catalog temporarily.
# For example:
# docker.io/your_org/kaap-op-bundle:VERSION
# docker.io/your_org/kaap-op-catalog:VERSION
# NOTE: If you specify a public registry such as docker.io, you need to login first.
# i.e. docker login docker.io -u your_docker_org
DOCKER_REGISTRY="docker.io"
DOCKER_ORG=${DOCKER_ORG:-"change-me-to-your-org"}
IMAGE_TAG="olm-gen1"

# Specify the operator image which is used in this bundle.
# ie.: OPERATOR_IMG="docker.io/datastax/kaap:${BUNDLE_VERSION}"
OPERATOR_IMG=docker.io/datastax/kaap:$BUNDLE_VERSION
