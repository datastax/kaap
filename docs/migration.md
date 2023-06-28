# Migrate existing cluster to the operator

Migrating from a existing Apache Pulsar cluster to the operator is a complex process. The operator is not able to migrate the cluster automatically. 
However, the migration tool CLI is a great friend to help you to migrate your cluster.


## Migration tool

The migration tool is a CLI application that connects to an existing Apache Pulsar cluster and generates a valid and equivalent `PulsarCluster` CRD.
The tool also simulates what would happen if the generated `PulsarCluster` would be submitted. Then it retrieves the kubernetes resources that would be created and compares them with the existing ones, generating a fancy HTML report.
Once the report is generated, is up to you to decide if you want to proceed with the migration or not.


### Usage
Java 17+ it's required.
Download the jar from the [latest release](https://github.com/datastax/kaap/releases).

Create the input file `input-cluster-specs.yaml` with the following content:
```yaml
context: <context-name>
namespace: <namespace>
clusterName: <cluster-name>
```
To retrieve the `context-name` you will have to run `kubectl config get-contexts` and select the one you want to use.
Note: it's suggested to switch to that context before running the migration tool and ensure connectivity (e.g. `kubectl get pods`).
The `namespace` is the namespace with the Apache Pulsar resources.
The `clusterName` is the prefix of each pod. For example, if the broker pod is `pulsar-prod-cluster-broker-0`, the `clusterName` is `pulsar-prod-cluster`.


Then you can generate the report with:
```
java -jar migration-tool.jar generate -i input-cluster-specs.yaml -o output 
```
In the logs you'll see the link of the generated report. Open it in your browser and check the differences between the existing cluster and the operator.

Sometimes you might need to change the generated CRD and simulate the migration again. To do that, you can run:
```
java -jar migration-tool.jar diff -d output/<context-name>
```



## Migration procedure
Once you're happy with the report, you can proceed with the migration. 
The migration supposes the existing cluster has been created using Helm.

Create a new `values.yaml` file for the operator. Then in the `kaap.cluster` section copy the crd's spec.
```
kaap:
    cluster:
        create: true
        name: <cluster-name>
        spec:
            <copy here the spec from the CRD>
```


1. Install the operator release with the above values. 
    ```
    helm install kaap datastax/kaap-stack --values <values.yaml>
    ```

2. Wait for the operator to take control of the cluster. Check the PulsarCluster status to be ready. Since each resource will match the existing ones, the following behaviours are expected:
  - The operator will not create any new resource.
  - The operator will not delete any existing resource.
  - The operator will restart every statefulset/deployments. This will be done in a safe manner using the staged upgrades feature of the operator.
3. Delete the existing chart release.
    ```
    kubectl delete secret -l name=<old-release-name>,owner=helm
    ```
4. Cleanup helm annotations and labels from the new values.

   You can safely remove the following annotations:
    - meta.helm.sh/release-name
    - meta.helm.sh/release-namespace

   and labels:
    - app.kubernetes.io/managed-by
    - chart
    - release
    - heritage