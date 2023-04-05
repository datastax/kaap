# Secure the cluster with TLS and cert-manager

Install a Pulsar cluster with cert manager enabled.
```
helm install pulsar helm/pulsar-stack --values helm/examples/admin-console/values.yaml --set cert-manager.global.leaderElection.namespace=default
```
