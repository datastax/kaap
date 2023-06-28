# Zoned Resource Sets

Starting from Luna Streaming 2.10 3.4 it's possible to redirect clients to use a proxy in the same zone of the target broker.
This helps a lot in reducing effectively the latency between the client and the broker and network costs.

## Example

```
helm install kaap helm/kaap \
    --values helm/examples/zoned-resource-sets/values.yaml 
```

## How it works

### Goals
1. Multi availability zone placement for brokers and proxies
2. Dedicated brokers and proxies for customers (customer1 and customer2)
3. Network optimization by reducing the network traffic between brokers and proxies keeping the communication in the same availability zone.

### Racks
We create a rack for each zone. Each pod assigned to a rack will:
- be scheduled in the same availability zone of others pods in the same rack
- not be scheduled in a node where an equivalent pod is already running (if possible).

Each rack will be assigned to a availability zone, chosen by the kubernetes scheduler.

## Resource sets
We create a resource sets for each entity:
- shared-az1: resources placed in the az1 rack
- shared-az1: resources placed in the az2 rack
- customer1: resources assigned to a tenant called customer1. Each resource will be spread across different availibility zones.
- customer2: resources assigned to a tenant called customer2. Each resource will be spread across different availibility zones.

## Broker url rewriting
Every proxy will redirect the traffic to the broker in the same resource set.
For shared proxies (shared-az1 and shared-az2) the traffic will be redirected to proxy in the same availability zone of the target broker.
For customer proxies (shared-az1 and shared-az2) the traffic will be redirected to proxy of the same customer. (no matter the availability zone).

## Setup broker isolation

Isolate namespace bundles ownership of customer tenants to their own brokers. 

```
tenant=customer1
kubectl exec deployment/pulsar-bastion -- bin/pulsar-shell --fail-on-error -e "admin ns-isolation-policy set --auto-failover-policy-type min_available --auto-failover-policy-params min_limit=1,usage_threshold=80 --namespaces $tenant/default --primary .*-$tenant-.*  pulsar $tenant"

tenant=customer2
kubectl exec deployment/pulsar-bastion -- bin/pulsar-shell --fail-on-error -e "admin ns-isolation-policy set --auto-failover-policy-type min_available --auto-failover-policy-params min_limit=1,usage_threshold=80 --namespaces $tenant/default --primary .*-$tenant-.*  pulsar $tenant"
```

## Test proxy rewriting

For test purpose, set the public tenant to be only owned by brokers on availability zone 1.
```
tenant=public
broker=shared-az1
kubectl exec deployment/pulsar-bastion -- bin/pulsar-shell --fail-on-error -e "admin ns-isolation-policy set --auto-failover-policy-type min_available --auto-failover-policy-params min_limit=1,usage_threshold=80 --namespaces $tenant/default --primary .*-$broker-.*  pulsar $tenant-$broker"
```

Now try to write to a topic in the `public` tenant forcing the url to the "wrong" proxy.

```
kubectl exec deployment/pulsar-bastion -- bin/pulsar-client --url pulsar://pulsar-proxy-shared-az2:6650 produce -m test public/default/test
```

In the logs you will see that the client has been redirected to the correct proxy.





















