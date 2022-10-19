# Pulsar Operator

Kubernetes operator PoC for Apache Pulsar.

The application is written in Java 11+ and Quarkus. 
The operator is built upon [`quarkus-operator-sdk`](#Quarkus Operator SDK).

## Quarkus Operator SDK
This library puts together several technologies:
* Quarkus based application
* [Quarkus Kubernets](https://quarkus.io/guides/deploying-to-kubernetes)
* [Quarkus JIB](https://quarkus.io/guides/container-image#container-image-options) for docker image lifecycle
* [Kubernetes Java Operator SDK](https://javaoperatorsdk.io/)
* Fabric8 as Kubernetes client

 - [Docs](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html) 
 - Github: https://github.com/quarkiverse/quarkus-operator-sdk

## Pulsar configurations
The operator will ask for the config-map `pulsar-proxy` in order to get all the info to connect to the brokers.
Currently the authentication is not implemented.


## Dev deployment in a K8S cluster
### Env setup
1. Set current kubectl context: `kubectl config set-context <cluster>`
2. Set K8S namespace where to deploy the operator
```
export QUARKUS_KUBERNETES_NAMESPACE=<pulsar_cluster_namespace>
```
3. Set credentials for the docker image: 
```
export QUARKUS_CONTAINER_IMAGE_GROUP=<your_dockerhub_username>
export QUARKUS_CONTAINER_IMAGE_USERNAME=<your_dockerhub_username>
export QUARKUS_CONTAINER_IMAGE_PASSWORD=<your_dockerhub_token>
```

### Deployment

1. Compile the project with maven. `mvn package`
2. Build and push the docker image `make docker-build-push`
3. Deploy the generated CRDs `make install`
4. Deploy the operator `make deploy`

`make all-deploy` executes all the above steps.

Inside the `target/kubernetes` you can find all the generated k8s definitions.


## Prod deployment
TODO helm chart