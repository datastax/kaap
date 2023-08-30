- [Kaap Stack Operator](kaap-stack.md)
- [Kaap Operator](kaap.md)
- [Usage](usage.md)
- Features
  - [Staged upgrades](staged-upgrades.md)
  - [Resource Sets and Racks](resource-sets.md)
  - [API Reference](api-reference.md)
- [Migrate existing cluster to the operator](migration.md)
- [Development](development.md)


## Migrate from 0.1.0 to 0.2.0

KAAP 0.2.0 is a major release that includes a new version of the CRDs. This means that you will need to migrate your existing CRDs to the new version. This process is described below.

The CRDs specs are fully compatible, however it's required to change the existing custom resource definition version to v1beta1.

This process ensures no downtime. One full cluster upgrade is required to complete the migration, and it's done automatically.


1. Apply new CRD versions.

This process will update the CRD adding a new version v1beta1.

```
kubectl apply -f https://github.com/datastax/kaap/releases/download/operator-0.2.0/autorecoveries.kaap.oss.datastax.com-v1-migration.yml --server-side --force-conflicts 
kubectl apply -f https://github.com/datastax/kaap/releases/download/operator-0.2.0/bastions.kaap.oss.datastax.com-v1-migration.yml --server-side --force-conflicts
kubectl apply -f https://github.com/datastax/kaap/releases/download/operator-0.2.0/bookkeepers.kaap.oss.datastax.com-v1-migration.yml --server-side --force-conflicts
kubectl apply -f https://github.com/datastax/kaap/releases/download/operator-0.2.0/brokers.kaap.oss.datastax.com-v1-migration.yml --server-side --force-conflicts
kubectl apply -f https://github.com/datastax/kaap/releases/download/operator-0.2.0/functionsworkers.kaap.oss.datastax.com-v1-migration.yml --server-side --force-conflicts
kubectl apply -f https://github.com/datastax/kaap/releases/download/operator-0.2.0/proxies.kaap.oss.datastax.com-v1-migration.yml --server-side --force-conflicts
kubectl apply -f https://github.com/datastax/kaap/releases/download/operator-0.2.0/zookeepers.kaap.oss.datastax.com-v1-migration.yml --server-side --force-conflicts
kubectl apply -f https://github.com/datastax/kaap/releases/download/operator-0.2.0/pulsarclusters.kaap.oss.datastax.com-v1-migration.yml --server-side --force-conflicts
```

2. Copy the KAAP custom resources and move them to v1beta1

```
namespace=kaap # change this according to your kaap namespace
name=pulsar # change this according to your pulsar cluster name
kubectl get -n $namespace autorecoveries $name-autorecovery -o yaml | sed 's/apiVersion: kaap.oss.datastax.com\/v1alpha1/apiVersion: kaap.oss.datastax.com\/v1beta1/' | kubectl apply -f -
kubectl get -n $namespace bastions $name-bastion -o yaml | sed 's/apiVersion: kaap.oss.datastax.com\/v1alpha1/apiVersion: kaap.oss.datastax.com\/v1beta1/' | kubectl apply -f -
kubectl get -n $namespace bookkeepers $name-bookkeeper -o yaml | sed 's/apiVersion: kaap.oss.datastax.com\/v1alpha1/apiVersion: kaap.oss.datastax.com\/v1beta1/' | kubectl apply -f -
kubectl get -n $namespace brokers $name-broker -o yaml | sed 's/apiVersion: kaap.oss.datastax.com\/v1alpha1/apiVersion: kaap.oss.datastax.com\/v1beta1/' | kubectl apply -f -
kubectl get -n $namespace functionsworkers $name-functionsworker -o yaml | sed 's/apiVersion: kaap.oss.datastax.com\/v1alpha1/apiVersion: kaap.oss.datastax.com\/v1beta1/' | kubectl apply -f -
kubectl get -n $namespace proxies $name-proxy -o yaml | sed 's/apiVersion: kaap.oss.datastax.com\/v1alpha1/apiVersion: kaap.oss.datastax.com\/v1beta1/' | kubectl apply -f -
kubectl get -n $namespace zookeepers $name-zookeeper -o yaml | sed 's/apiVersion: kaap.oss.datastax.com\/v1alpha1/apiVersion: kaap.oss.datastax.com\/v1beta1/' | kubectl apply -f -
kubectl get -n $namespace pulsarclusters $name -o yaml | sed 's/apiVersion: kaap.oss.datastax.com\/v1alpha1/apiVersion: kaap.oss.datastax.com\/v1beta1/' | kubectl apply -f -
```

Note: 
`Warning: resource zookeepers/pulsar-zookeeper is missing the kubectl.kubernetes.io/last-applied-configuration annotation which is required by kubectl apply. kubectl apply should only be used on resources created declaratively by either kubectl create --save-config or kubectl apply. The missing annotation will be patched automatically.
`
can be ignored.


Ensure the new resources exist:

```
kubectl get -n $namespace pulsarclusters.v1beta1.kaap.oss.datastax.com $name 
```


3. Now upgrade the chart to 0.2.0

```
helm repo update
helm pull https://datastax.github.io/kaap
helm upgrade kaap --version 0.2.0 -n $namespace kaap/kaap-stack -f <your-values.yaml>
```


4. Ensure everything is working fine.

Now the KAAP pod is watching the v1beta1 resources.

You should expect each component to be restarted.

Run this command until it returns only one match:

```
kubectl get -n $namespace all -o yaml | grep kaap.oss.datastax.com/v1alpha1
```

The one match should be the ZooKeeper job.
In case the broker has transactions enabled, you could see two matches.
