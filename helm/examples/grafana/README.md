# Grafana integration

Install Pulsar Stack with prometheus stack enabled and Pulsar grafana dashboards.
```
helm install pstack -n pstack --create-namespace helm/pulsar-stack --values helm/examples/grafana/values.yaml 
```

Create a simple cluster:
```
kubectl apply -f helm/examples/grafana/cluster.yaml -n pstack
```

Login to grafana UI:
```
kubectl port-forward deployment/pstack-grafana 3000:3000
open localhost:8080
```
Login using the credentials configured in the values.yaml. (`admin` and `grafana1`).