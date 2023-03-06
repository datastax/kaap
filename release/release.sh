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

set -e

check_clean() {
	if [ ! -z "$(git status --porcelain)" ]; then
		echo "Git directory is not clean, aborting\n$pwd"
		git status
		exit 1
	fi
}
validate_new_version() {
  local v=$1
  [[ "${v}" =~ ^[0-9].[0-9].[0-9]$ ]] || (echo "new_version format is incorrect. The format must be in format MAJOR.MINOR.PATCH"; exit 1)
}
validate_artifact() {
  local v=$1
  [[ "$v" == "pulsar-operator" || "$v" == "pulsar-stack" || "$v" == "operator-image" ]] || (echo "artifact must be one of pulsar-operator,pulsar-stack,operator-image. got $v"; exit 1)
}
check_current_version_in_chart() {
  local v=$1
  if [[ -z $(grep -r "version: ${v}" helm/pulsar-operator/Chart.yaml) ]]; then
    echo "Version $v is not set in helm/pulsar-operator/Chart.yaml"
    exit 1
  fi
  if [[ -z $(grep -r "version: ${v}" helm/pulsar-stack/Chart.yaml) ]]; then
      echo "Version $v is not set in helm/pulsar-stack/Chart.yaml"
      exit 1
  fi
}
replace_version_in_chart() {
  local file=$1
  local new_version=$2
  line_number=$(grep -n 'version:' $file | cut -d ':' -f1 | head -n 1)
  sed -i '' -- "${line_number}s/.*/version: $new_version/" $file
}

artifact=$1
new_version=$2

usage="./release.sh <artifact> <new-version>
  artifact:
      - operator-image (docker image)
      - pulsar-operator (operator Helm chart)
      - pulsar-stack (stack Helm chart)
  new-version: new version in format MAJOR.MINOR.PATCH"
if [[ -z $artifact ]]; then
    echo "Required argument artifact is missing"
    echo "$usage"
    exit 1
fi
if [[ -z $new_version ]]; then
    echo "Required argument with new project version"
    echo "$usage"
    exit 1
fi
# Validate inputs and current state
check_clean
validate_new_version $new_version
validate_artifact $artifact

if [[ "$artifact" == "operator-image" ]]; then
  current_version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

  mvn release:prepare -DreleaseVersion=$new_version -Dresume=false
  echo "$new_version released."
elif [[ "$artifact" == "pulsar-operator" ]]; then
  replace_version_in_chart helm/pulsar-operator/Chart.yaml $new_version
  git commit -am "Release pulsar-operator chart $new_version"
  git tag pulsar-operator-$new_version
  git push
  git push --tags
elif [[ "$artifact" == "pulsar-stack" ]]; then
  replace_version_in_chart helm/pulsar-stack/Chart.yaml $new_version
  git commit -am "Release pulsar-stack chart $new_version"
  git tag pulsar-stack-$new_version
  git push
  git push --tags
fi
