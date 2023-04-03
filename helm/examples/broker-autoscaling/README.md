# Broker autoscaling

Broker autoscaling is a feature that allows to scale up/down the number of broker in a cluster based on the current resource usage.

```
helm install pulsar-operator helm/pulsar-stack \
    --values helm/examples/broker-autoscaling/values.yaml 
```


## Generate traffic

```
kubectl exec -it deployment/pulsar-bastion -- bin/pulsar-admin namespaces create public/perf --bundles 16 
kubectl exec -it deployment/pulsar-bastion -- bin/pulsar-admin topics create-partitioned-topic -p 4 public/perf/topic
kubectl exec -it deployment/pulsar-bastion -- bin/pulsar-perf produce persistent://public/perf/topic -r 1000 -s 100
```
