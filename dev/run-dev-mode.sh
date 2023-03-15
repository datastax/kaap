#!/bin/bash
kubectl apply -f helm/pulsar-operator/crds --server-side
mvn -pl pulsar-operator quarkus:dev -Dquarkus.operator-sdk.crd.generate=false  -Dquarkus.operator-sdk.crd.apply=false