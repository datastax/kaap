# Resource Sets

Resource Sets are a way to create different sets of the same components. This is useful when you want to create different configurations of the same components. For example, you can dedicate a set of broker to a single customer.

## Example

```
helm install k8saap helm/k8saap \
    --values helm/examples/resource-sets/values.yaml 
```