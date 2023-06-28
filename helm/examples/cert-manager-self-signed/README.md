# Secure the cluster with TLS and cert-manager (Self Signed certificates)

Install a Pulsar cluster with cert manager enabled and self signed cert priv enabled.
```
helm install kaap --create-namespace kaap/kaap-stack \
    --values helm/examples/cert-manager-self-signed/values.yaml \
    --set cert-manager.global.leaderElection.namespace=default 
```
