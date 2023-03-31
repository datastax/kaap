# Resource Sets and Racks

## Resource Sets
The operator allows you to create multiple sets of Pulsar proxies/broker/bookies. Each set is a dedicated deployment/statefulset with its own service and configmap.
When multiple sets are specified, an umbrella service is created as the main entrypoint of the cluster. Other than that, a dedicated service is created for each set. You can customize the service singularly. For example, it’s straightforward to have different dns domains for each set.

Having different endpoints for the cluster allows new deployment strategies, such as canary deployments.


## Racks
A rack defines a fault domain. A resource set can be mapped to a rack.
When a resource set is mapped to a rack, all their replicas will be placed in the same failure domain.

Available failure domains are `zone`, a region’s availability zone and `host`, a cluster node.
In order to guarantee high availability over different availability zones, it’s required to create multiple sets in different racks.

One of the benefits of using racks is that you can know in advance if a proxy and a broker are in the same zone.

To use a rack you must assign it to a resource set:
```
spec:
    global:
      racks:
        rack1: {}
        rack2: {}
        rack3: {}
      resourceSets:
        shared-az1:
            rack: rack1
        shared-az2:
            rack: rack2
        shared-az3:
            rack: rack3
```        

## Proxy Sets
With proxy sets it’s straightforward to have dedicated proxy sets per extensions. Pulsar is able to communicate with different applications clients, such as Apache Kafka and RabbitMQ, through proxy extensions. It’s therefore possible to have a dedicated proxy to only accept specific protocol commands.

```
spec:
    global:
      resourceSets:
        shared: {}
        kafka: {}
    proxy:
        sets:
            shared:
              replicas: 5
              service:
                annotations:
                  external-dns.alpha.kubernetes.io/hostname: proxy.pulsar.local
            kafka:
              replicas: 3
              config:
                <config to enable kafka proxy extension>:
              service:
                annotations:
                  external-dns.alpha.kubernetes.io/hostname: kafka.proxy.pulsar.local
```


## Bookkeeper Sets
Thanks to the racks, the operator is able to set the data placement policy automatically.
Leveraging the rack-awareness concept of Pulsar and BookKeeper clients, every entry will be stored as much as possible in different failure domains.

The auto-configuration of rack-awareness is enabled by default. It’s configurable in the bookkeeper configuration section:
```
bookkeeper:
	autoRackConfig: 
		enabled: true
		periodMs: 60000
```

Note that these features require `bookkeeperClientRegionawarePolicyEnabled=true` in the broker.
The operator will automatically add this configuration property in the broker and autorecovery.
If you wish to disable the region aware policy, you need to explicitly set `bookkeeperClientRegionawarePolicyEnabled=false` in the broker and autorecovery.



## Pod placement affinity and affinity
For a single resource set, it’s possible to specify the antiAffinity.
There are two levels of affinity, zone and host.
The first one will set the failure domain to the region’s availability zone.
The latter one will set the failure domain to the node.

It’s possible to configure if the requirements must be satisfied or it should be only if possible.
This mechanism leverages the K8s `requiredDuringSchedulingIgnoredDuringExecution` and `preferredDuringSchedulingIgnoredDuringExecution` properties.

The default is:
```
global:
    antiAffinity:
        host: 
            enabled: true
            required: true
        zone:
            enabled: false
            required: false 
```
This means each replica of any deployment/statefulset will be forced to be placed on different nodes. There’s no requests for placing the pods in different availability zones, therefore each pod could be in the same node.
In order to achieve multi-zone availability, it’s required to set:
```
global:
    antiAffinity:
        host: 
            enabled: true
            required: true
        zone:
            enabled: true
            required: false
```
In this way each pod will be placed to a different zone, if possible.
If you want to enforce it, you have to set:
```
global:
    antiAffinity:
        host: 
            enabled: true
            required: true
        zone:
            enabled: true
            required: true
```
Note that if an availability zone without any pods of that kind is not available during the upgrades, the pod won’t be scheduled and the upgrade will be blocked until a pod is manually deleted and the zone is then freed.



## Resource sets pods placement affinity and affinity
A rack defines a fault domain. A resource set can be mapped to a rack.
When a resource set is mapped to a rack, all their replicas will be placed in the same failure domain.
There are two levels of affinity, zone and host.
The first one will set the failure domain to the region’s availability zone.
The latter one will set the failure domain to the node.

When a rack is specified, the default configuration is:
```
global:
    racks:
        rack1:
            host:
                enabled: false
                requireRackAffinity: false
                requireRackAntiAffinity: true
            zone:
                enabled: false
                requireRackAffinity: false
                requireRackAntiAffinity: true
                enableHostAntiAffinity: true
                requireRackHostAntiAffinity: true
```

The default configuration won’t enable any placement policy.
If you want to place all the pods in the same node, you have to set
```
global:
    racks:
        rack1:
            host:
                enabled: true
```

With `requireRackAffinity=false`, each pods of the same rack will be placed where a new pod of the same rack exists (if any exists), if possible.
Set `requireRackAffinity=true` to enforce it. Note that if the target node is full (can’t accept new pod with those requirements), the pod will wait until the node is able to accept new pods.

With `requireRackAntiAffinity=false`, each pods of the same rack will be placed in a node where any other pod of any other racks is already scheduled, if possible.
With `requireRackAntiAffinity=true`, this behavior is enforced. Note that if no node is free, the pod will wait until a new node is added.

If you want to place all the pods in the same zone, you have to set:
```
global:
    racks:  
        rack1:
	        zone:
		        enabled: true
```

With `enableHostAntiAffinity=true`, other than placing pods in different availability zones, a different node will be chosen. These requirements can be disabled (`enableHostAntiAffinity=false`), enforced (`requireRackHostAntiAffinity: true`) or done in best-effort (`requireRackHostAntiAffinity: false`)
