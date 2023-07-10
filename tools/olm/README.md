# OLM Integration

The document describes the automated kaap OLM bundle generation process for each release. For every release, we publish new bundle to two online catalogs. 
Only the [community-operator catalog](https://github.com/k8s-operatorhub/community-operators) is supported and it can be optionally installed on kubernetes clusters.

## Prerequisite

- [kubectl](https://kubernetes.io/docs/tasks/tools/)(Kubernetes) or [oc](https://docs.openshift.com/container-platform/4.11/cli_reference/openshift_cli/getting-started-cli.html#installing-openshift-cli)(OpenShift)
- [Docker](https://www.docker.com/)

## Generate the bundle locally

Change the variables in `env.sh` if needed:
```sh
cd /tools/olm
vi env.sh
```

You will need to create a temporary docker registry to handle the bundle and catalog images. 

Generate bundle and catalog images and push them to repos specified in env.sh.
```sh
./generate-olm-bundle.sh
```

Verify the bundle is generated in the target folder. We only need the bundle folder which has the following files:
```
0.1.0/
├── bundle.Dockerfile
├── manifests
│   ├── <crds>
│   ├── kaap.clusterserviceversion.yaml
└── metadata
    └── annotations.yaml
```

## Deploy bundle using Subscription
With the bundle and catalog images pushed to a image registry, we can now deploy the operator by
creating a Subscription.

Kubernetes does not come with OLM. To deploy the OLM operator on a cluster, run the following:
```sh
curl -sL https://github.com/operator-framework/operator-lifecycle-manager/releases/download/v0.22.0/install.sh | bash -s v0.22.0
```
Deploy private catalog to serves the bundle on a cluster:
```sh
# Deploy the catalog src
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: CatalogSource
metadata:
  name: olm-kaap-catalog
  namespace: default
spec:
  sourceType: grpc
  image: "${DOCKER_REGISTRY}/${DOCKER_ORG}/kaap-op-catalog:${BUNDLE_VERSION}"
EOF
# wait for the catalog server pod to be ready
kubectl get po -w
```
Create subscription to actually deploy the kaap operator:
```sh
# Create a OperatorGroup and subscription in the default namespace:
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha2
kind: OperatorGroup
metadata:
  name: default-og
  namespace: default
spec:
  # if not set, default to watch all namespaces
  targetNamespaces:
  - default
---
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: kaap
  namespace: default
spec:
  channel: alpha
  name: kaap
  source: olm-kaap-catalog
  sourceNamespace: default
EOF
```


After verifying, the bundle image and catalog image are no longer needed. We only need the bundle folder to run the CI test suits.


## Run CI test suits before creating PR

Clone your forked community-operators repo
```sh
git clone https://github.com/k8s-operatorhub/community-operators.git
```
The bundle folder is all we need to commit into the community-operators repo. Copy the new bundle into the forked community-operators repo
```sh
cp -r tools/olm/target/0.1.0 community-operators/operators/kaap/
```

Run test suites
```sh
cd community-operators
OPP_PRODUCTION_TYPE=k8s  OPP_AUTO_PACKAGEMANIFEST_CLUSTER_VERSION_LABEL=1 bash <(curl -sL https://raw.githubusercontent.com/redhat-openshift-ecosystem/community-operators-pipeline/ci/latest/ci/scripts/opp.sh) all operators/kaap-pulsar-operator/0.1.0
```
The expected output:
```
...
Test 'kiwi' : [ OK ]
Test 'lemon_latest' : [ OK ]
Test 'orange_latest' : [ OK ]
```

After the tests pass, commit and push the new bundle to a branch and then create a PR in the community-operator repo.

See detail for running CI test suits [here](https://k8s-operatorhub.github.io/community-operators/operator-test-suite/).
