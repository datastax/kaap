# Pulsar Operator

Kubernetes operator PoC for Apache Pulsar.

The application is written in Java 11+ and Quarkus. 
The operator is built upon the [quarkus-operator-sdk](#Quarkus Operator SDK) library.

The operator watches for the autoscaler CRDs and manage their lifecycle.
See how to install the autoscaler in the [examples](/helm/examples).

The autoscaler will scale up or down the brokers. It's just a demonstration and it is not intended to be used in any real cluster.

## Quarkus Operator SDK
This library puts together several technologies:
* Quarkus based application
* [Quarkus Kubernets](https://quarkus.io/guides/deploying-to-kubernetes)
* [Quarkus JIB](https://quarkus.io/guides/container-image#container-image-options) for docker image lifecycle
* [Kubernetes Java Operator SDK](https://javaoperatorsdk.io/)
* Fabric8 as Kubernetes client

 - [Docs](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html) 
 - [Github](https://github.com/quarkiverse/quarkus-operator-sdk)


## Autoscaler deployment
The autoscaler requires an up and running Pulsar cluster.

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
Install the autoscaler resource
```
kubectl -n mypulsar apply -f helm/examples/deploy.yaml 
```