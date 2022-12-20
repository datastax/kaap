#!/bin/bash
mvn_or_mvnd() {
  if command -v mvnd &> /dev/null; then
    mvnd $mvnmod "$@"
  else
    mvn $mvnmod "$@"
  fi
}
this_dir=$( dirname -- "${BASH_SOURCE[0]}" )
mvn_or_mvnd -f $this_dir/../../../pom.xml test-compile exec:java -Dexec.classpathScope=test  -Dexec.mainClass="com.datastax.oss.pulsaroperator.tests.LocalK8sEnvironment" "$@"