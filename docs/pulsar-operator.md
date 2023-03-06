# Pulsar Operator

Pulsar Operator manages Apache Pulsar clusters guided by a single CRD, called `PulsarCluster`.

## Installation
The Chart is private so you need to [download](https://github.com/riptano/pulsar-operator/releases/download/pulsar-operator-0.0.2/pulsar-operator-0.0.2.tgz) it first.

Then install the chart using the tarball:
```
helm install pulsar-operator pulsar-operator-0.0.2.tgz
```


## Usage

Install a PulsarCluster resource.
```
helm upgrade pulsar-operator pulsar-operator-0.0.2.tgz -f helm/examples/grafana/values.yaml
```

Wait for the cluster to be up and running
```
kubectl wait pulsar pulsar-cluster --for condition=Ready=True --timeout=240s
```

Uninstall the operator and the PulsarCluster
```
helm delete pulsar-operator
```


## Upgrading to newer version
Since now the CRDs are in version `v1alpha1` you need to replace them when they change.
Note this is only needed in the early stages of the project.

```
kubectl replace -f helm/pulsar-operator/crds
```
Now you can proceed with the upgrade of the operator.
