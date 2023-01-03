#!/bin/bash
this_dir=$( dirname -- "${BASH_SOURCE[0]}" )
mvn -f $this_dir/../../../../pulsar-operator/pom.xml quarkus:dev