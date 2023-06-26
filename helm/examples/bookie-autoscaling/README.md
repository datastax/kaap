# Bookie autoscaling

Bookie autoscaling is a feature that allows to scale up/down the number of bookies in a cluster based on the current disk usage.

```
helm install k8saap helm/k8saap \
    --values helm/examples/bookie-autoscaling/values.yaml 
```