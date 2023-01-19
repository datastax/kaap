# Secure the cluster with TLS and cert-manager

Install Pulsar Stack with cert manager enabled.
```
helm install pcert -n pcert --create-namespace helm/pulsar-stack \
    --values helm/examples/cert-manager/values.yaml \
    --set cert-manager.global.leaderElection.namespace=pcert 
```

Create a simple cluster:
```
kubectl apply -f helm/examples/cert-manager/cluster.yaml -n pcert
```