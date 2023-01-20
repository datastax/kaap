#!/bin/bash
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
  [[ "$v1" == "operator" || "$v1" == "operator-chart" || "$v1" == "stack-chart" ]] || (echo "artifact must be one of operator,operator-chart,stack-chart."; exit 1)
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

artifact=$2
new_version=$2

usage="./release.sh <artifact> <new-version>
  artifact:
      - operator (docker image)
      - operator-chart (operator Helm chart)
      - stack-chart (stack Helm chart)
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

if [[ "$artifact" == "operator" ]]; then
  current_version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

  mvn release:prepare -DreleaseVersion=$new_version
  echo "$new_version released."
elif [[ "$artifact" == "operator-chart" ]]; then
  replace_version_in_chart helm/pulsar-operator/Chart.yaml $new_version
  git commit -am "Release operator-chart $new_version"
  git tag operator-chart-$new_version
  git push
  git push --tags
elif [[ "$artifact" == "stack-chart" ]]; then
  replace_version_in_chart helm/pulsar-stack/Chart.yaml $new_version
  git commit -am "Release stack-chart $new_version"
  git tag stack-chart-$new_version
  git push
  git push --tags
fi
