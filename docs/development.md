# Development

The operator is built upon the [quarkus-operator-sdk](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html) library.
It requires JDK17+.

## Creates the operator docker image
```
mvn package -DskipTests -pl pulsar-operator
docker tag datastax/lunastreaming-operator:latest $dockerhub_repo/lunastreaming-operator:latest
docker push $dockerhub_repo/lunastreaming-operator:latest
```
## Local deployment

### Startup K3s
Run `tests/src/test/scripts/local-k3s.sh` to start a K3S server in a docker container.
It creates a namespace `ns` for convenience.
The kube config file is copied to: `/tmp/pulsaroperator-local-k3s-kube-config`.

### Use the Quarkus dev mode

In this mode, the `pulsar-operator` code is hot reloaded at every change in the source code.

```
export KUBECONFIG=/tmp/pulsaroperator-local-k3s-kube-config
kubectl config set-context --current --namespace=ns
mvn quarkus:dev -pl pulsar-operator
```
### Install a Pulsar cluster
```
export KUBECONFIG=/tmp/pulsaroperator-local-k3s-kube-config
kubectl config set-context --current --namespace=ns
kubectl apply -f helm/examples/local-k3s.yaml
```
In this terminal you can now monitor the cluster with the most common tools such `kubectl` and `k9s`.

Note: since k3s doesn't support sharing the docker registry of the host, a Pulsar image is copied into the k3s environment.

### Integration tests
Integration tests under `tests` spin up a K3s cluster in docker.
In order to troubleshoot a failure test, sometimes it's useful to use your own cluster to run the tests. 
All the tests create a new temporary namespace but some cluster-wide resources are created and deleted, so be careful.

```
tests/src/test/scripts/local-k3s.sh 
```

or for a single test:

```
tests/src/test/scripts/local-k3s-run-test.sh -Dtest='MyTestClass' 
```


If you're using a public cluster (e.g. GCP, EKS) you have to deploy the operator image to a public Docker registry.
When running the test you can set the operator image with the `pulsaroperator.tests.operator.image` system property.
You can also use a specific StorageClass for the test by setting ´pulsaroperator.tests.existingenv.storageclass`.

To run a test in GCP:

```
docker push <operator-image>
gcloud container clusters get-credentials gcp-cluster # or set it as current context with kubectl
mvn -pl tests test -Dpulsaroperator.tests.env.existing \
  -Dpulsaroperator.tests.existingenv.kubeconfig.context=<gcp-cluster-context> \
  -Dpulsaroperator.tests.existingenv.storageclass=default \
  -Dtest='MyTestClass'
```


## Links and resources
* [Quarkus Kubernetes](https://quarkus.io/guides/deploying-to-kubernetes)
* [Quarkus JIB](https://quarkus.io/guides/container-image#container-image-options)
* [Kubernetes Java Operator SDK](https://javaoperatorsdk.io/)