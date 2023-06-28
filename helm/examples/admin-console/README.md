# Install the Pulsar Admin Console

Install a Pulsar cluster along with the Pulsar Admin Console. In this example, both TLS and authentication are enabled.
```
helm install pulsar helm/kaap-stack --values helm/examples/admin-console/values.yaml --set cert-manager.global.leaderElection.namespace=default
```
