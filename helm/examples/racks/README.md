# Racks

Racks are a way to group resource sets together.
Using racks you can place all the pods of a resource set to a specific k8s node or availability zone.
Having all the pods of a resource set in the same availability zone opens scenarios where cross availability zone communication can be reduced to the minimum.

For example, the Pulsar proxy can redirect traffic to proxy closer to the broker owner of a specific topic.


## Example

```
helm install k8saap helm/k8saap \
    --values helm/examples/racks/values.yaml 
```