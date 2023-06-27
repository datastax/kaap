# Development

The operator is built upon the [quarkus-operator-sdk](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html) library.
## Requirements
- JDK17+
- Maven
- Docker 

## Creates the operator docker image
```
mvn package -DskipTests -pl k8saap -am -Pskip-crds
```

If you want to use a public k8s cluster, you would need to deploy the operator image to DockerHub or another public registry.

```
$dockerhub_repo=<your dockerhub repo>
docker tag datastax/k8saap:latest-dev
$dockerhub_repo/k8saap:latest
docker push $dockerhub_repo/k8saap:latest
```

## Local deployment
In order to quickly test your changes, it's possible to create a local k8s cluster.
This repository contains several scripts to work with a [K3s](https://k3s.io/) cluster.
The only requirements is Docker.

### Startup K3s

```
./tests/src/test/scripts/local-k3s.sh
```
It creates a namespace `ns` for convenience.
Now the `~/.kube/config` has been modified to point to the local k3s cluster.
```
kubectl config get-contexts
```

The cluster is deployed in a docker container. You can kill and recreate it whenever you want. No mounts are configured. 
```
docker ps
```

After testing, do remember to remove unused volumes from docker.
```
docker volume prune -f
```


If you did some changes to the operator code and wants to redeploy it, you can use the following script.
```
./tests/src/test/scripts/local-k3s-update-operator-no-crds.sh
```
This will build and push the new docker image to the container. 
**You'll need to manually restart the operator pod**.





### Use the Quarkus dev mode

In this mode, the `k8saap` code is hot reloaded at every change in the source code.

```
mvn quarkus:dev -pl k8saap -am -Pskip-crds
```

### Deploy the operator and the Pulsar cluster
Check at `helm/examples` to get some ideas about how to deploy a Pulsar cluster.
Note that the k3s registry DOES NOT use your host docker registry, so every image will be downloaded from upstream.
The k3s cluster already contains the dev version of the operator, named `datastax/k8saap:latest-dev`.
The k3s cluster also contains a Pulsar/LunaStreaming image to use.

### Integration tests
Integration tests under `tests` spin up a K3s cluster in docker.
In order to troubleshoot a failure test, sometimes it's useful to use your own cluster to run the tests. 
All the tests create a new temporary namespace but some cluster-wide resources are created and deleted, so be careful.

In order to run a test targeting the current kube context: 
```
mvn -pl tests test -Dtest='CRDsTest' -Prun-tests-current-context
```

If you're using a public cluster (e.g. GCP, EKS) you have to deploy the operator image to a public Docker registry.
You can also use a specific StorageClass for the test by setting ´k8saap.tests.existingenv.storageclass`.

To run a test in GCP:

```
docker push <operator-image>
gcloud container clusters get-credentials gcp-cluster # or set it as current context with kubectl

mvn -pl tests test -Dtest='CRDsTest' -Prun-tests-current-context \
    -Dk8saap.tests.operator.image=<operator-image> 
```


## Links and resources
* [Quarkus Kubernetes](https://quarkus.io/guides/deploying-to-kubernetes)
* [Quarkus JIB](https://quarkus.io/guides/container-image#container-image-options)
* [Kubernetes Java Operator SDK](https://javaoperatorsdk.io/)