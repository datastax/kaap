# Pulsar Operator

Kubernetes operator for Apache Pulsar.

The operator is built upon the [quarkus-operator-sdk](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html) library.
It requires JDK17+.

## Links and resources
* [Quarkus Kubernetes](https://quarkus.io/guides/deploying-to-kubernetes)
* [Quarkus JIB](https://quarkus.io/guides/container-image#container-image-options) for docker image lifecycle
* [Kubernetes Java Operator SDK](https://javaoperatorsdk.io/)

### Creates the operator docker image
1. Set credentials for the docker image: 
```
export QUARKUS_CONTAINER_IMAGE_GROUP=<your_dockerhub_username>
export QUARKUS_CONTAINER_IMAGE_USERNAME=<your_dockerhub_username>
export QUARKUS_CONTAINER_IMAGE_PASSWORD=<your_dockerhub_token>

make docker-build-push
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