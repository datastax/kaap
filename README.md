# Kubernetes Autoscaling for Apache Pulsar (KAAP)

Kubernetes Autoscaling for Apache Pulsar (KAAP) simplifies running [Apache Pulsar](https://pulsar.apache.org) on Kubernetes by applying the familiar [Operator pattern](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/) to Pulsar's components.

Operating and maintaining Apache Pulsar clusters traditionally involves complex manual configurations, making it challenging for developers and operators to effectively manage the system's lifecycle. However, with the KAAP operator, these complexities are abstracted away, enabling developers to focus on their applications rather than the underlying infrastructure.

This operator acts as an extension to the Kubernetes API, introducing custom resource definitions (CRDs) specific to Apache Pulsar. With these CRDs, users can define Pulsar clusters, topics, subscriptions, and other resources as native Kubernetes objects. The operator then reconciles the desired state defined by these objects with the actual state of the Pulsar cluster, ensuring that the cluster is always in the desired configuration.

Some of the key features and benefits of the KAAP operator include:

- **Easy Deployment**: Deploying an Apache Pulsar cluster on Kubernetes is simplified through declarative configurations and automation provided by the operator.

- **Scalability**: The operator enables effortless scaling of Pulsar clusters by automatically handling the creation and configuration of new Pulsar brokers and bookies as per defined rules.

- **High Availability**: The operator implements best practices for high availability, ensuring that Pulsar clusters are fault-tolerant and can sustain failures without service disruptions.

- **Monitoring and Metrics**: Integrated monitoring and metrics collection allow users to gain insights into the health and performance of Pulsar clusters, making it easier to detect and resolve issues.

- **Lifecycle Management**: The operator takes care of common Pulsar cluster lifecycle tasks, such as cluster creation, upgrade, configuration updates, and graceful shutdowns.

- **Integration with Kubernetes Ecosystem**: The Apache Pulsar Kubernetes Operator seamlessly integrates with other Kubernetes-native tools and features, such as Kubernetes RBAC, Prometheus, and Grafana, enabling a cohesive and comprehensive ecosystem for managing Pulsar clusters.

Whether you are a developer looking to leverage the power of Apache Pulsar in your Kubernetes environment or an operator seeking to streamline the management of Pulsar clusters, the KAAP Operator provides a robust and user-friendly solution. In this documentation, we will explore the installation, configuration, and usage of the operator, empowering you to harness the full potential of Apache Pulsar within your Kubernetes deployments.

## Documentation

Full documentation is available in the [DataStax Streaming Documentation](https://docs.datastax.com/en/streaming/kaap-operator/index.html) or at [this repo's GitHub Pages site](https://datastax.github.io/kaap/docs/).

## Getting started

1. Install the DataStax Helm repository:
```
helm repo add kaap https://datastax.github.io/kaap
helm repo update
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
3. Ensure pulsar-operator is up and running:
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
This setting is defined in the Pulsar operator values.yaml file as `crd: create: true`.

5. To see available CRDs:
```
kubectl get crds | grep datastax
```
Result:
```
autorecoveries.pulsar.oss.datastax.com           2023-05-12T16:35:59Z
bastions.pulsar.oss.datastax.com                 2023-05-12T16:36:00Z
bookkeepers.pulsar.oss.datastax.com              2023-05-12T16:36:00Z
brokers.pulsar.oss.datastax.com                  2023-05-12T16:36:01Z
functionsworkers.pulsar.oss.datastax.com         2023-05-12T16:36:01Z
proxies.pulsar.oss.datastax.com                  2023-05-12T16:36:02Z
pulsarclusters.pulsar.oss.datastax.com           2023-05-12T16:36:04Z
zookeepers.pulsar.oss.datastax.com               2023-05-12T16:36:06Z
```

## Uninstall KAAP

To uninstall KAAP:
```
helm delete kaap
```
Result:
```
release "kaap" uninstalled
```

For more, see the [DataStax Streaming Documentation](https://docs.datastax.com/en/streaming/kaap-operator/index.html) or [this repo's GitHub Pages site](https://datastax.github.io/kaap/docs/).

## Build KAAP from source

* JDK17+
* Maven
* Docker

To build operator from source:

1. Clone this repo and change directory to root.
```
cd kaap
mvn package -DskipTests -pl pulsar-operator -am -Pskip-crds
```

2. To use a public Kubernetes cluster, you must deploy the operator image to DockerHub or another public registry:
```
$dockerhub_repo=<your dockerhub repo>
docker tag datastax/lunastreaming-operator:latest-dev
$dockerhub_repo/lunastreaming-operator:latest
docker push $dockerhub_repo/lunastreaming-operator:latest
```

## Resources
For more, see the [DataStax Streaming Documentation](https://docs.datastax.com/en/streaming/kaap-operator/index.html) or [this repo's GitHub Pages site](https://datastax.github.io/kaap/docs/).
