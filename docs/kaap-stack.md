# Pulsar Stack

Pulsar stack is the all-in-one solution to provide easy to operate end-to-end Apache Pulsar installation with the K8s Autoscaling for Apache Pulsar, Grafana Dashboards, Prometheus Metrics and a series of useful tools for monitoring your Apache Pulsar cluster.
The chart is able to install:
- K8s Autoscaling for Apache Pulsar
- Prometheus Stack (Grafana)
- Pulsar Grafana dashboards
- Cert Manager
- Keycloak


## Installation

Import the repository and install the stack operator:
```
helm repo add kaap https://datastax.github.io/kaap
helm repo update
helm install kaap kaap/kaap-stack 
```

## Usage

Install the Pulsar stack with a PulsarCluster up and running
```
helm install pulsar kaap/kaap-stack --set kaap.cluster.create=true
```

Wait for the cluster to be up and running
```
kubectl wait pulsar pulsar --for condition=Ready=True --timeout=240s
```

Uninstall the operator and the cluster
```
helm delete pulsar
```
