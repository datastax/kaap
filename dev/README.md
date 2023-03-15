# Run the operator in dev mode

It's possible to run the operator with code hot reloading.

## Setup the kubernetes cluster as current in kubectl

You can use whatever k8s cluster you want. If you don't have one ready, you can use k3s:
```
./tests/src/test/scripts/local-k3s.sh
```


## Run the operator
Run the operator locally but targeting the remote cluster:
```
./dev/run-dev-mode.sh
```

Note that CRDs are not re-applied when they change. If you change the CRDs, you have to stop the process, build the `pulsar-operator` module (`mvn package`) and restart the dev mode.


## Deploy a pulsar cluster

You can apply a PulsarCluster CRD or you can use the Helm examples at `helm/examples`.
You must disable the operator deployment in the chart because it's served by the local process in dev mode.

For example, you can install the bookie racks example:
```
helm install pulsar-operator helm/pulsar-operator --values helm/examples/bookie-racks/values.yaml --set operator.enabled=false
```