# Kubernetes Autoscaling for Apache Pulsar (KAAP)

Kubernetes Autoscaling for Apache Pulsar (KAAP) simplifies running [Apache Pulsar](https://pulsar.apache.org) on Kubernetes by applying the familiar [Operator pattern](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/) to Pulsar's components, and horizonally scaling resources up or down based on CPU and memory workloads.

KAAP operator's broker autoscaling integrates with the Pulsar broker's load balancer, which has insight into all other brokers' workloads. With this information, KAAP can make smarter resource management decisions than the Kubernetes HorizontalPodAutoscaler.

KAAP's Bookkeeper autoscaling solution is similarly Pulsar-native. Bookkeeper nodes are scaled up in response to running low on storage, and because of Bookkeeper's segment-based design, the new storage is available immediately for use by the cluster, with no log stream rebalancing required.

When KAAP sees low storage usage on a Bookkeeper node, the node is automatically scaled down (decommissioned) to free up volume usage and reduce storage costs. This scale-down is done in a safe, controlled manner which ensures no data loss and guarantees the configured replication factor for all messages. For example, if your replication factor is 3 (write and ack quorum of 3), 3 replicas are maintained at all times during the scale down to ensure data can be recovered, even if there is a failure during the scale-down phase. Scaling down bookies has been a consistent pain point in Pulsar, and KAAP automates this without sacrifing Pulsar's data guarantees.

Operating and maintaining Apache Pulsar clusters traditionally involves complex manual configurations, making it challenging for developers and operators to effectively manage the system's lifecycle. However, with the KAAP operator, these complexities are abstracted away, enabling developers to focus on their applications rather than the underlying infrastructure.

Some of the key features and benefits of the KAAP operator include:

- **Easy Deployment**: Deploying an Apache Pulsar cluster on Kubernetes is simplified through declarative configurations and automation provided by the operator.

- **Scalability**: The KAAP operator enables effortless scaling of Pulsar clusters by automatically handling the creation and configuration of new Pulsar brokers and bookies as per defined rules. The broker autoscaling is integrated with the Pulsar broker load balancer to make smart resource management decisions, and bookkeepers are scaled up and down based on storage usage in a safe, controlled manner.

- **High Availability**: The operator implements best practices for high availability, ensuring that Pulsar clusters are fault-tolerant and can sustain failures without service disruptions.

- **Lifecycle Management**: The operator takes care of common Pulsar cluster lifecycle tasks, such as cluster creation, upgrade, configuration updates, and graceful shutdowns.

We also offer the KAAP Stack if you're looking for more Kubernetes-native tooling deployed with your Pulsar cluster. Along with the PulsarCluster CRDs, KAAP stack also includes:

* Pulsar Operator
* Prometheus Stack (Grafana)
* Pulsar Grafana dashboards
* Cert Manager
* Keycloak

Whether you are a developer looking to leverage the power of Apache Pulsar in your Kubernetes environment or an operator seeking to streamline the management of Pulsar clusters, the KAAP Operator provides a robust and user-friendly solution.

## Documentation

Full documentation is available in the [DataStax Streaming Documentation](https://docs.datastax.com/en/streaming/kaap-operator/index.html) or at [this repo's GitHub Pages site](https://datastax.github.io/kaap/docs/).

## Install KAAP Operator with a Pulsar Cluster

1. Install the DataStax Helm repository:
```
helm repo add kaap https://datastax.github.io/kaap
helm repo update
curl -LOs https://raw.githubusercontent.com/datastax/kaap/main/helm/examples/dev-cluster/values.yaml
helm install pulsar -f values.yaml 
kubectl wait pulsar pulsar --for condition=Ready=True --timeout=240s
```
2. Install the Pulsar operator Helm chart:
```
helm install kaap kaap/kaap
```
Result:
```
NAME: kaap
LAST DEPLOYED: Wed Jun 28 11:37:45 2023
NAMESPACE: pulsar-cluster
STATUS: deployed
REVISION: 1
TEST SUITE: None
```
3. Ensure the operator is up and running:
```
kubectl get deployment
```
Result:
```
NAME              READY   UP-TO-DATE   AVAILABLE   AGE
kaap   1/1     1            1           13m
```

4. You've now installed KAAP.
By default, when KAAP is installed, the PulsarCluster CRDs are also created.
This setting is defined in the KAAP values.yaml file as `crd: {create: true}`.

5. To see available CRDs:
```
kubectl get crds | grep kaap
```
Result:
```
autorecoveries.kaap.oss.datastax.com             2023-06-28T15:37:39Z
bastions.kaap.oss.datastax.com                   2023-06-28T15:37:39Z
bookkeepers.kaap.oss.datastax.com                2023-06-28T15:37:39Z
brokers.kaap.oss.datastax.com                    2023-06-28T15:37:40Z
functionsworkers.kaap.oss.datastax.com           2023-06-28T15:37:40Z
proxies.kaap.oss.datastax.com                    2023-06-28T15:37:40Z
pulsarclusters.kaap.oss.datastax.com             2023-06-28T15:37:41Z
zookeepers.kaap.oss.datastax.com                 2023-06-28T15:37:41Z
```

For more, see the [DataStax Streaming Documentation](https://docs.datastax.com/en/streaming/kaap-operator/index.html) or [this repo's GitHub Pages site](https://datastax.github.io/kaap/docs/).

## Uninstall KAAP

To uninstall KAAP:
```
helm delete kaap
```
Result:
```
release "kaap" uninstalled
```

## Install KAAP-Stack Operator

## Uninstall KAAP-Stack Operator

To uninstall KAAP:
```
helm delete kaap
```
Result:
```
release "kaap" uninstalled
```
For more, see the [DataStax Streaming Documentation](https://docs.datastax.com/en/streaming/kaap-operator/index.html) or [this repo's GitHub Pages site](https://datastax.github.io/kaap/docs/).

## Resources
For more, see the [DataStax Streaming Documentation](https://docs.datastax.com/en/streaming/kaap-operator/index.html) or [this repo's GitHub Pages site](https://datastax.github.io/kaap/docs/).
