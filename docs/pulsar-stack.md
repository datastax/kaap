# Pulsar Stack

Pulsar stack is the all-in-one solution to provide easy to operate end-to-end Apache Pulsar installation with the Pulsar Operator, Grafana Dashboards, Prometheus Metrics and a series of useful tools for monitoring your Apache Pulsar cluster.
The chart is able to install:
- Pulsar Operator
- Prometheus Stack (Grafana)
- Pulsar Grafana dashboards
- Cert Manager
- Keycloak


## Installation
The Chart is private so you need to [download](https://github.com/riptano/pulsar-operator/releases/download/pulsar-stack-0.0.1/pulsar-stack-0.0.1.tgz) it first.

Then install the chart using the tarball:
```
helm install -n stack --create-namespace pulsar-stack pulsar-stack-0.0.1.tgz
```
