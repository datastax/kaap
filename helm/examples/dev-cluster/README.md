# Install a Pulsar cluster for dev testing 

Install a Pulsar cluster with minimum resources allocated and that works in mono-machine environments (e.g. Minikube, Kind..)
```
helm install pulsar kaap/kaap-stack --values helm/examples/dev-cluster/values.yaml
```
