# Migration tool for migrating from an existing chart to the operator

## How to migrate (!EXPERIMENTAL!)

1. Run the CLI to generate the `PulsarCluster` CRD and to observe the possible differences once the operator takes control of the cluster.
   - Edit the file `bin/input-cluster-specs.yaml` with the coordinates of the cluster you want to migrate.
   - Run the CLI with the following command:
    ```bash
    bin/migration-tool.sh generate
    ```
   - Check the output for the comparisons between the existing chart and the operator.
   - If OK, proceed with the migration.

2. Apply the `PulsarCluster` CRD to the Kubernetes cluster. The CRD has been generated at step 1. under `target/<context>/crd-generated-pulsar-cluster-<cluster-name>.json`.
    ```
    kubectl apply -f target/<context>/crd-generated-pulsar-cluster-<cluster-name>.json
    ```

3. Install the Operator without any cluster pre-configured.
4. Wait for the operator to take control of the cluster. Check the PulsarCluster status to be ready.
5. Delete the existing chart release.
    ```
    kubectl delete secret -l name=<release-name>,owner=helm
    ```
6. Cleanup helm annotations and labels from the Pulsar CRD. 

    You can safely remove the following annotations:
   - meta.helm.sh/release-name
   - meta.helm.sh/release-namespace

    and labels:
   - app.kubernetes.io/managed-by
   - chart
   - release
   - heritage



## Examples
Run a simple migration example with `install-and-migrate.sh` script.

