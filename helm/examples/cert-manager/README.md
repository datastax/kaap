# Grafana integration

Install Pulsar Stack with prometheus stack enabled and Pulsar grafana dashboards.
```
helm install pcert -n pcert --create-namespace helm/pulsar-stack \
    --values helm/examples/cert-manager/values.yaml \
    --set cert-manager.global.leaderElection.namespace=pcert 
```

Create a simple cluster:
```
kubectl apply -f helm/examples/cert-manager/cluster.yaml -n pcert
```

Login to grafana UI:
```
kubectl port-forward deployment/pstack-grafana 3000:3000
open localhost:8080
```
Login using the credentials configured in the values.yaml. (`admin` and `grafana1`).