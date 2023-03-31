# Usage

The `PulsarCluster` custom resource definition represents the desired state of your Apache Pulsar cluster.
The main structure is:
- `global` section, containing configurations shared by each component
- a section for each component

## Global section
The global section contains the main characteristics of the cluster.

### Cluster name
This option is required and it's the base name of each resource created in the k8s cluster.
```
global:
    name: my-pulsar-cluster
```

### Pulsar image
This option is required and it's the Apache Pulsar image used by all the components.
```
global:
    image: apachepulsar/pulsar:2.11.0
```

Each component can override this value and use a different image. For example, the functions worker might requires an image with more builtin Pulsar IO connectors.
```
global:
    image: apachepulsar/pulsar:2.11.0
functionsWorker:
    image: apachepulsar/pulsar-all:2.11.0
```


### Authentication
Apache Pulsar supports different authentication ways. 
The operator comes with options to facilitate the authentication.
In order to setup the [JWT Authentication](https://pulsar.apache.org/docs/2.11.x/security-jwt/), you can simply set authentication to `true`.

```
global:
    auth:
        enabled: true
```

Since the only supported authentication mechanism by the operator is JWT, it's not required to specify JWT related options.
If you want to use a different authentication mechanism, you can disable authentication and manually configure the authentication options in the config section.

When setting up authentication, the operator will automatically generate the secret containing the private key and the public key.
If you wish to use your own key pairs, you can disable the secret generation by setting `initialize` to false.
```
global:
    auth:
        enabled: true
        token:
            initialize: false
```
Note that the operator will expect secrets with the same name to be already present in the namespace.
Secrets must be named `token-private-key` and `token-public-key`.
```
apiVersion: v1
kind: Secret
metadata:
  name: token-private-key
type: Opaque
data:
  my-private.key: <base64 encoded private key>
---
apiVersion: v1
kind: Secret
metadata:
  name: token-public-key
type: Opaque
data:
  my-public.key: <base64 encoded private key>
```


Note: Symmetric secret key is not supported.


The operator will also generate tokens for super user roles.
By default, the super users are `superuser`, `admin`, `websocket` and `proxy`.
If you wish to use another set of super users, you can specify them in the `superUserRoles` option, along with the `proxyRoles`.
```
global:
    auth:
        enabled: true
        token:
            superUserRoles:
                - superuser
                - admin
                - websocket
                - proxy
                - my-custom-user
                - my-custom-proxy-user
            proxyRoles:
                - proxy
                - my-custom-proxy-user
```


To generate a token for a given subject, you can then login to the bastion and perform Pulsar Admin operations since the Bastion pod already mount the super user token:

```
PULSAR_TOKEN=$(kubectl exec deployment/pulsar-bastion -- bin/pulsar tokens create --private-key token-private-key/my-private.key --subject myuser)
echo $PULSAR_TOKEN
kubectl exec deployment/pulsar-bastion -- bin/pulsar-shell -e 'admin namespaces grant-permission --role myuser --actions produce,consume public/default'
kubectl exec deployment/pulsar-bastion -- bin/pulsar-shell -e "client --auth-params \"token:$PULSAR_TOKEN\" produce -m hello public/default/topic"
```


### Secure your cluster with TLS
You can setup TLS for each component in the Pulsar cluster or you can only enable it for the specific components.
Each component has its own dedicated configuration section but they're all under the `global.tls` section.

Once the TLS setup is done, the operator will take care of updating the components configuration to use TLS.


To setup a zero-trust cluster with TLS, you need to set `enabled` to true for each component

```
global:
    tls:
      enabled: true
      zookeeper:
        enabled: true
        secretName: zk-tls
      bookkeeper:
        enabled: true
        secretName: bk-tls
      autorecovery:
        enabled: true
        secretName: autorecovery-tls
      proxy:
        enabled: true
        enabledWithBroker: true
        secretName: proxy-tls
      broker:
        enabled: true
        secretName: broker-tls
      functionsWorker:
        enabled: true
        enabledWithBroker: true
        secretName: fnw-tls
```
Note that each component has its own secret name.
The secret name can be auto provisioned by the operator using `cert-manager`. You can share the same certificate across multiple components, although it's not a recommended practice for security reasons.

The secret must follow the [cert-manager Certificate](https://cert-manager.io/docs/concepts/certificate/) structure:
- `tls.crt`: the certificate
- `tls.key`: the private key

```
apiVersion: v1
kind: Secret
metadata:
  name: pulsar-tls
data:
  tls.crt: <base64 encoded certificate>
  tls.key: <base64 encoded key>
```

The operator only needs to know the secret name and it will automatically use it as is.
In some cases, like test clusters, you might wish to generate self signed certificates.
The operator integrates well with cert-manager to generate a self-signed CA and all the needed certificates.

In order to enable the certificate provisioning you have to configure it in the `global.tls` section.
```
global:
    tls:
        certProvisioner:
            selfSigned:
              enabled: true
              perComponent: true
              zookeeper:
                generate: true
              broker:
                generate: true
              bookkeeper:
                generate: true
              autorecovery:
                generate: true
              proxy:
                generate: true
              functionsWorker:
                generate: true
```
This will generate all the secrets needed by the components. The name of each secret is gathered from the `secretName` configured.


The repository contains examples for [real TLS example with acme](https://github.com/riptano/pulsar-operator/tree/main/helm/examples/cert-manager-acme) 
and [self-signed certificates](https://github.com/riptano/pulsar-operator/tree/main/helm/examples/cert-manager-self-signed). 


### Affinity, antiaffinity and high availability

See [Resource Sets and Racks](resource-sets.md).


## Common configuration for each components
All the components are based on the same configuration structure.

### Application configuration
Under `<component>.config` you can specify the runtime options for the application.
Those properties will be stored in the appropriate `ConfigMap`.

For example, if you wish to change the replication factor for topics, you can set it in the `broker.config` section.
```
broker:
    config:
        managedLedgerDefaultAckQuorum: 1
        managedLedgerDefaultEnsembleSize: 1
        managedLedgerDefaultWriteQuorum: 1
```

or if you want to enable debug logging: 
```
broker:
    config:
        PULSAR_LOG_LEVEL: debug
        PULSAR_LOG_ROOT_LEVEL: debug
```
or if you want to change the JVM options:
```
broker:
    config:
        PULSAR_MEM: "-Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g"
```

Note that this will override the default setting provided by the operator.

### Pod resources
You can specify the resources for each component using the `resources` section.
```
broker:
    resources:
        requests:
            cpu: 1000m
            memory: 2Gi
        limits:
            cpu: 2000m
            memory: 4Gi
```


### Replicas
You can specify the resources for each component using the `replicas` section.
```
broker:
    replicas: 7
```

### Full list
To get a full list of the spec you can check the [API reference](api-reference.md).


## Examples

See some examples in the [docs](https://github.com/riptano/pulsar-operator/tree/main/helm/examples).
