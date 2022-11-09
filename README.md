# Pulsar Operator

Kubernetes operator for Apache Pulsar.

The operator is built upon the [quarkus-operator-sdk](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html) library.
It requires JDK17+.

## Links and resources
* [Quarkus Kubernetes](https://quarkus.io/guides/deploying-to-kubernetes)
* [Quarkus JIB](https://quarkus.io/guides/container-image#container-image-options) for docker image lifecycle
* [Kubernetes Java Operator SDK](https://javaoperatorsdk.io/)

### Creates the operator docker image
Set credentials for the docker image and push it to dockerhub: 
```
export QUARKUS_CONTAINER_IMAGE_GROUP=<your_dockerhub_username>
export QUARKUS_CONTAINER_IMAGE_USERNAME=<your_dockerhub_username>
export QUARKUS_CONTAINER_IMAGE_PASSWORD=<your_dockerhub_token>

mvn package -DskipTests -Dquarkus.container-image.push=true -pl pulsar-operator
```

### Helm deployment
Install the operator and the CRDs
```
helm install pos helm/pulsar-operator -n mypulsar
```

Install a cluster 
```
kubectl -n mypulsar apply -f helm/examples/cluster.yaml
```

Uninstall the cluster
```
kubectl -n mypulsar delete PulsarCluster pulsar-cluster
```

Uninstall the operator and the CRDs
```
helm delete pos -n mypulsar
```

## Configuration
The configuration for the operator and the Pulsar Cluster CRD is in the [docs](https://github.com/riptano/pulsar-operator/blob/main/docs/crds.md) directory.
