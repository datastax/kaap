# Pulsar Operator

Pulsar Operator manages Apache Pulsar clusters guided by a single CRD, called `PulsarCluster`.

## Installation
The Chart is private so you need to [download](https://github.com/riptano/pulsar-operator/releases/download/pulsar-operator-0.0.1/pulsar-operator-0.0.1.tgz) it first.

Then install the chart using the tarball:
```
helm install -n stack --create-namespace pulsar-operator pulsar-operator-0.0.1.tgz
```


## Usage

Install a Pulsar cluster Custom Resource
```
kubectl -n stack apply -f helm/examples/cluster.yaml
```

Wait for the cluster to be up and running
```
kubectl wait pulsar -n stack pulsar-cluster --for condition=Ready=True --timeout=240s
```

Uninstall the cluster
```
kubectl -n stack delete PulsarCluster pulsar-cluster
```

Uninstall the operator and the CRDs
```
helm delete pulsar -n stack
```