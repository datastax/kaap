# Bookie autoscaling

Bookie autoscaling is a feature that allows to scale up/down the number of bookies in a cluster based on the current disk usage.

```
helm install pulsar-operator helm/pulsar-operator \
    --values helm/examples/bookie-autoscaling/values.yaml 
```