# Pulsar Operator

Kubernetes operator for Apache Pulsar.

The operator is built upon the [quarkus-operator-sdk](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html) library.
It requires JDK17+.

## Usage
### Creates the operator docker image
Set credentials for the docker image and push it to dockerhub: 
```
dockerhub_repo=<your_dockerhub_repo>
tag=latest
export QUARKUS_CONTAINER_IMAGE_IMAGE=$dockerhub_repo/pulsar-operator:$tag
mvn package -DskipTests -pl pulsar-operator
docker push $dockerhub_repo/pulsar-operator:$tag
```

### Helm deployment
### Note: tested with K8s 1.25
Install the operator and the CRDs with monitoring
```
helm dependency build helm/pulsar-operator
helm install -n mypulsar --create-namespace pos helm/pulsar-operator \
    --set kube-prometheus-stack.enabled=true \
    --set grafanaDashboards.enabled=true \
    --set kube-prometheus-stack.grafana.adminPassword=grafana
```

Install a Pulsar cluster Custom Resource
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

## Local deployment
### Use the Quarkus dev mode
Run the class `LocalK8sEnvironment` in `tests` submodule. A K3s will be created inside a docker container.
The program will copy the kube-config file to a temporary director and then will output a command to set the current context and start Quarkus in dev mode.
In this mode, the `pulsar-operator` code is hot reloaded at every change in the source code.

```
export KUBECONFIG=/var/folders/0z/h11c9jxx76g640q6pc1984480000gp/T/test-kubeconfig1657715965916679034.yaml && kubectl config set-context --current --namespace=ns && mvn quarkus:dev -pl pulsar-operator
```

To install a Pulsar cluster, the program will output a command to apply the `local-k3s` cluster under `helm/examples`.
```
export KUBECONFIG=/var/folders/0z/h11c9jxx76g640q6pc1984480000gp/T/test-kubeconfig3358562246577739029.yaml && kubectl config set-context --current --namespace=ns && kubectl apply -f helm/examples/local-k3s.yaml
```
In this terminal you can now monitor the cluster with the most common tools such `kubectl` and `k9s`.

Note: since k3s doesn't support sharing the docker registry of the host, a Pulsar image is copied into the k3s environment.


### Integration tests
Integration tests under `tests` spin up a K3s cluster in docker.
In order to troubleshoot a failure test, sometimes it's useful to reuse a k8s environment.
You can do that by running:
```
mvn test -Dpulsar.operator.tests.env.existing=true \
    -Dpulsaroperator.tests.existingenv.kubeconfig.context=default \
    -pl tests \
    -Dtest='PulsarClusterTest#testBaseInstall'
```
If you're using a public cluster (e.g. GCP, EKS) you have to deploy the operator image to a Docker registry.
When running the test you can set the operator image with the `pulsaroperator.tests.operator.image` system property.
You can also use a specific StorageClass for the test by setting ´pulsaroperator.tests.existingenv.storageclass`. 


## Links and resources
* [Quarkus Kubernetes](https://quarkus.io/guides/deploying-to-kubernetes)
* [Quarkus JIB](https://quarkus.io/guides/container-image#container-image-options) for docker image lifecycle
* [Kubernetes Java Operator SDK](https://javaoperatorsdk.io/)