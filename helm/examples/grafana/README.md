# Grafana integration

Install a Pulsar cluster with prometheus stack enabled and Pulsar grafana dashboards.
```
helm install kaap-stack kaap/kaap-stack --values helm/examples/grafana/values.yaml
```

Login to grafana UI:
```
kubectl port-forward deployment/pstack-grafana 3000:3000
open localhost:3000
```
Login using the credentials configured in the values.yaml. (`admin` and `grafana1`).

This example includes grafana persistence config to allow new dashboards to be created
and saved across restarts of grafana.

```
kube-prometheus-stack:
  enabled: true
  grafana:
    adminPassword: grafana1
    # Make Grafana persistent (Using Statefulset)
    persistence:
     enabled: true
     type: sts
     # storageClassName: "storageClassName"
     accessModes:
       - ReadWriteOnce
     size: 5Gi
     finalizers:
       - kubernetes.io/pvc-protection
```
