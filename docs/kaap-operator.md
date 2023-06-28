# Operator

Kaap manages Apache Pulsar clusters guided by a single CRD, called `PulsarCluster`.

## Installation
You need to [download](https://github.com/datastax/kaap/releases/download/kaap-0.1.0/kaap-0.1.0.tgz) it first.

Then install the chart using the tarball:
```
helm install kaap kaap-0.1.0.tgz
```


## Usage

Install a PulsarCluster resource.
```
helm upgrade kaap kaap-0.1.0.tgz -f helm/examples/grafana/values.yaml
```

Wait for the cluster to be up and running
```
kubectl wait pulsar pulsar-cluster --for condition=Ready=True --timeout=240s
```

Uninstall the operator and the PulsarCluster
```
helm delete kaap
```


## Upgrading to newer version
Since now the CRDs are in version `v1alpha1` you need to replace them when they change.
Note this is only needed in the early stages of the project.

```
kubectl replace -f helm/kaap/crds
```
Now you can proceed with the upgrade of the operator.



## Configure the operator
The operator implementation is based on [Quarkus](https://quarkus.io/).
To configure the operator you can modify the `operator.config` section.

```
operator:
    config:
        logLevel: info|debug|trace
        quarkus: {}
        operator: {}
```

- The `logLevel` is referred only to the operator logs.

- The `quarkus` section is used to configure the Quarkus runtime.
For instance, it might be useful to edit the [Quarkus Operator SDK](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html) configuration.
For more info see the [Quarkus Operator SDK](https://quarkiverse.github.io/quarkiverse-docs/quarkus-operator-sdk/dev/index.html) documentation.
The proper format is explained in [Quarkus Configuration Reference](https://quarkus.io/guides/config).

- The `operator` section is used to configure the operator itself.

    ```
    operator:
        config:
            operator:
                reconciliationRescheduleSeconds: 10
    ```

| Configuration property            | Type  | Default | Description                                                                                                 | 
|-----------------------------------|-------|---------|-------------------------------------------------------------------------------------------------------------|
| `reconciliationRescheduleSeconds` | `int` | `5`     | The number of seconds to wait before rescheduling a reconciliation while waiting for resources to be ready. |
    
    
    
    
