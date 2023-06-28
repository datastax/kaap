# Pulsar Stack

Pulsar stack is the all-in-one solution to provide easy to operate end-to-end Apache Pulsar installation with the K8s Autoscaling for Apache Pulsar, Grafana Dashboards, Prometheus Metrics and a series of useful tools for monitoring your Apache Pulsar cluster.
The chart is able to install:
- K8s Autoscaling for Apache Pulsar
- Prometheus Stack (Grafana)
- Pulsar Grafana dashboards
- Cert Manager
- Keycloak


## Installation
You need to [download](https://github.com/datastax/kaap/releases/download/kaap-stack-0.1.0/kaap-stack-0.1.0.tgz) it first.

Then install the chart using the tarball.
```
helm install kaap-stack-0.1.0.tgz
```

## Usage

Install the Pulsar stack with a PulsarCluster up and running
```
helm install pulsar kaap-stack-0.1.0.tgz --set kaap.cluster.create=true
```

Wait for the cluster to be up and running
```
kubectl wait pulsar pulsar --for condition=Ready=True --timeout=240s
```

Uninstall the operator and the cluster
```
helm delete pulsar
```
