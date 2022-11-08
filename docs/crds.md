# API Reference

Packages:

- [com.datastax.oss/v1alpha1](#comdatastaxossv1alpha1)

# com.datastax.oss/v1alpha1

Resource Types:

- [PulsarCluster](#pulsarcluster)

- [ZooKeeper](#zookeeper)




## PulsarCluster
<sup><sup>[↩ Parent](#comdatastaxossv1alpha1 )</sup></sup>








<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
      <td><b>apiVersion</b></td>
      <td>string</td>
      <td>com.datastax.oss/v1alpha1</td>
      <td>true</td>
      </tr>
      <tr>
      <td><b>kind</b></td>
      <td>string</td>
      <td>PulsarCluster</td>
      <td>true</td>
      </tr>
      <tr>
      <td><b><a href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.20/#objectmeta-v1-meta">metadata</a></b></td>
      <td>object</td>
      <td>Refer to the Kubernetes API documentation for the fields of the `metadata` field.</td>
      <td>true</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspec">spec</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatus">status</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec
<sup><sup>[↩ Parent](#pulsarcluster)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#pulsarclusterspecglobal">global</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspeczookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.global
<sup><sup>[↩ Parent](#pulsarclusterspec)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          Pulsar cluster base name.<br/>
        </td>
        <td>true</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecglobaldnsconfig">dnsConfig</a></b></td>
        <td>object</td>
        <td>
          Additional DNS config for each pod created by the operator.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          Default Pulsar image to use. Any components can be configured to use a different image.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          Default Pulsar image pull policy to use. Any components can be configured to use a different image pull policy. Default value is 'IfNotPresent'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>kubernetesClusterDomain</b></td>
        <td>string</td>
        <td>
          The domain name for your kubernetes cluster.
This domain is documented here: https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#a-aaaa-records-1 .
It's used to fully qualify service names when configuring Pulsar.
The default value is 'cluster.local'.
<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodeSelectors</b></td>
        <td>map[string]string</td>
        <td>
          Global node selector. If set, this will apply to all components.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>persistence</b></td>
        <td>boolean</td>
        <td>
          If persistence is enabled, components that has state will be deployed with PersistentVolumeClaims, otherwise, for test purposes, they will be deployed with emptDir
<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecglobalstorage">storage</a></b></td>
        <td>object</td>
        <td>
          Storage configuration.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecglobaltls">tls</a></b></td>
        <td>object</td>
        <td>
          TLS configuration for the cluster.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.global.dnsConfig
<sup><sup>[↩ Parent](#pulsarclusterspecglobal)</sup></sup>



Additional DNS config for each pod created by the operator.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>nameservers</b></td>
        <td>[]string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecglobaldnsconfigoptionsindex">options</a></b></td>
        <td>[]object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>searches</b></td>
        <td>[]string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.global.dnsConfig.options[index]
<sup><sup>[↩ Parent](#pulsarclusterspecglobaldnsconfig)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>value</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.global.storage
<sup><sup>[↩ Parent](#pulsarclusterspecglobal)</sup></sup>



Storage configuration.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>existingStorageClassName</b></td>
        <td>string</td>
        <td>
          Indicates if an already existing storage class should be used.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecglobalstoragestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.global.storage.storageClass
<sup><sup>[↩ Parent](#pulsarclusterspecglobalstorage)</sup></sup>



Indicates if a StorageClass is used. The operator will create the StorageClass if needed.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>extraParams</b></td>
        <td>map[string]string</td>
        <td>
          Adds extra parameters for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>fsType</b></td>
        <td>string</td>
        <td>
          Indicates the 'fsType' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>provisioner</b></td>
        <td>string</td>
        <td>
          Indicates the provisioner property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reclaimPolicy</b></td>
        <td>string</td>
        <td>
          Indicates the reclaimPolicy property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Indicates the 'type' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.global.tls
<sup><sup>[↩ Parent](#pulsarclusterspecglobal)</sup></sup>



TLS configuration for the cluster.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Global switch to turn on or off the TLS configurations.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecglobaltlszookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the ZooKeeper component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.global.tls.zookeeper
<sup><sup>[↩ Parent](#pulsarclusterspecglobaltls)</sup></sup>



TLS configurations related to the ZooKeeper component.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Enable tls for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>tlsSecretName</b></td>
        <td>string</td>
        <td>
          Enable certificates for this component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper
<sup><sup>[↩ Parent](#pulsarclusterspec)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Annotations to add to each ZooKeeper resource.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>component</b></td>
        <td>string</td>
        <td>
          Base name of the ZooKeeper component. Default is 'zookeeper'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to the ZooKeeper server.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspeczookeeperdatavolume">dataVolume</a></b></td>
        <td>object</td>
        <td>
          Volume configuration for ZooKeeper data.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>gracePeriod</b></td>
        <td>integer</td>
        <td>
          Termination grace period in seconds for the ZooKeeper pod. Default value is 60.<br/>
          <br/>
            <i>Minimum</i>: 0<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          Pulsar image to use for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          Pulsar image pull policy to use for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspeczookeepermetadatainitializationjob">metadataInitializationJob</a></b></td>
        <td>object</td>
        <td>
          Configuration about the job that initializes the Pulsar cluster creating the needed ZooKeeper nodes.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodeSelectors</b></td>
        <td>map[string]string</td>
        <td>
          Additional node selectors for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspeczookeeperpdb">pdb</a></b></td>
        <td>object</td>
        <td>
          Pod disruption budget configuration for the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>podManagementPolicy</b></td>
        <td>string</td>
        <td>
          Pod management policy for the ZooKeeper pod. Default value is 'Parallel'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspeczookeeperprobe">probe</a></b></td>
        <td>object</td>
        <td>
          Liveness and readiness probe values.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>replicas</b></td>
        <td>integer</td>
        <td>
          Replicas of ZooKeeper instances.<br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspeczookeeperresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspeczookeeperservice">service</a></b></td>
        <td>object</td>
        <td>
          Configurations for the Service resources associated to the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspeczookeeperupdatestrategy">updateStrategy</a></b></td>
        <td>object</td>
        <td>
          Update strategy for the ZooKeeper pod. Default value is rolling update.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.dataVolume
<sup><sup>[↩ Parent](#pulsarclusterspeczookeeper)</sup></sup>



Volume configuration for ZooKeeper data.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>existingStorageClassName</b></td>
        <td>string</td>
        <td>
          Indicates if an already existing storage class should be used.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          Indicates the suffix for the volume. Default value is 'data'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>size</b></td>
        <td>string</td>
        <td>
          Indicates the requested size for the volume. The format follows the Kubernetes' Quantity. Default value is '5Gi'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspeczookeeperdatavolumestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.dataVolume.storageClass
<sup><sup>[↩ Parent](#pulsarclusterspeczookeeperdatavolume)</sup></sup>



Indicates if a StorageClass is used. The operator will create the StorageClass if needed.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>extraParams</b></td>
        <td>map[string]string</td>
        <td>
          Adds extra parameters for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>fsType</b></td>
        <td>string</td>
        <td>
          Indicates the 'fsType' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>provisioner</b></td>
        <td>string</td>
        <td>
          Indicates the provisioner property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reclaimPolicy</b></td>
        <td>string</td>
        <td>
          Indicates the reclaimPolicy property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Indicates the 'type' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.metadataInitializationJob
<sup><sup>[↩ Parent](#pulsarclusterspeczookeeper)</sup></sup>



Configuration about the job that initializes the Pulsar cluster creating the needed ZooKeeper nodes.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#pulsarclusterspeczookeepermetadatainitializationjobresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the Job's Pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>timeout</b></td>
        <td>integer</td>
        <td>
          Timeout (in seconds) for the metadata initialization execution. Default value is 60.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.metadataInitializationJob.resources
<sup><sup>[↩ Parent](#pulsarclusterspeczookeepermetadatainitializationjob)</sup></sup>



Resource requirements for the Job's Pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>limits</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>requests</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.pdb
<sup><sup>[↩ Parent](#pulsarclusterspeczookeeper)</sup></sup>



Pod disruption budget configuration for the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Indicates if the Pdb policy is enabled for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>maxUnavailable</b></td>
        <td>integer</td>
        <td>
          Indicates the maxUnavailable property for the Pdb.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.probe
<sup><sup>[↩ Parent](#pulsarclusterspeczookeeper)</sup></sup>



Liveness and readiness probe values.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Indicates whether the probe is enabled or not.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>initial</b></td>
        <td>integer</td>
        <td>
          Indicates the initial delay (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>period</b></td>
        <td>integer</td>
        <td>
          Indicates the period (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>timeout</b></td>
        <td>integer</td>
        <td>
          Indicates the timeout (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.resources
<sup><sup>[↩ Parent](#pulsarclusterspeczookeeper)</sup></sup>



Resource requirements for the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>limits</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>requests</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.service
<sup><sup>[↩ Parent](#pulsarclusterspeczookeeper)</sup></sup>



Configurations for the Service resources associated to the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#pulsarclusterspeczookeeperserviceadditionalportsindex">additionalPorts</a></b></td>
        <td>[]object</td>
        <td>
          Additional ports for the ZooKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Additional annotations to add to the ZooKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.service.additionalPorts[index]
<sup><sup>[↩ Parent](#pulsarclusterspeczookeeperservice)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>appProtocol</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodePort</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>port</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>protocol</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>targetPort</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.updateStrategy
<sup><sup>[↩ Parent](#pulsarclusterspeczookeeper)</sup></sup>



Update strategy for the ZooKeeper pod. Default value is rolling update.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#pulsarclusterspeczookeeperupdatestrategyrollingupdate">rollingUpdate</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.zookeeper.updateStrategy.rollingUpdate
<sup><sup>[↩ Parent](#pulsarclusterspeczookeeperupdatestrategy)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>maxUnavailable</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>partition</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status
<sup><sup>[↩ Parent](#pulsarcluster)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspec">currentSpec</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>error</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec
<sup><sup>[↩ Parent](#pulsarclusterstatus)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspecglobal">global</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.global
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspec)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          Pulsar cluster base name.<br/>
        </td>
        <td>true</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspecglobaldnsconfig">dnsConfig</a></b></td>
        <td>object</td>
        <td>
          Additional DNS config for each pod created by the operator.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          Default Pulsar image to use. Any components can be configured to use a different image.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          Default Pulsar image pull policy to use. Any components can be configured to use a different image pull policy. Default value is 'IfNotPresent'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>kubernetesClusterDomain</b></td>
        <td>string</td>
        <td>
          The domain name for your kubernetes cluster.
This domain is documented here: https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#a-aaaa-records-1 .
It's used to fully qualify service names when configuring Pulsar.
The default value is 'cluster.local'.
<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodeSelectors</b></td>
        <td>map[string]string</td>
        <td>
          Global node selector. If set, this will apply to all components.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>persistence</b></td>
        <td>boolean</td>
        <td>
          If persistence is enabled, components that has state will be deployed with PersistentVolumeClaims, otherwise, for test purposes, they will be deployed with emptDir
<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspecglobalstorage">storage</a></b></td>
        <td>object</td>
        <td>
          Storage configuration.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspecglobaltls">tls</a></b></td>
        <td>object</td>
        <td>
          TLS configuration for the cluster.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.global.dnsConfig
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspecglobal)</sup></sup>



Additional DNS config for each pod created by the operator.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>nameservers</b></td>
        <td>[]string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspecglobaldnsconfigoptionsindex">options</a></b></td>
        <td>[]object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>searches</b></td>
        <td>[]string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.global.dnsConfig.options[index]
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspecglobaldnsconfig)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>value</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.global.storage
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspecglobal)</sup></sup>



Storage configuration.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>existingStorageClassName</b></td>
        <td>string</td>
        <td>
          Indicates if an already existing storage class should be used.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspecglobalstoragestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.global.storage.storageClass
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspecglobalstorage)</sup></sup>



Indicates if a StorageClass is used. The operator will create the StorageClass if needed.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>extraParams</b></td>
        <td>map[string]string</td>
        <td>
          Adds extra parameters for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>fsType</b></td>
        <td>string</td>
        <td>
          Indicates the 'fsType' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>provisioner</b></td>
        <td>string</td>
        <td>
          Indicates the provisioner property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reclaimPolicy</b></td>
        <td>string</td>
        <td>
          Indicates the reclaimPolicy property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Indicates the 'type' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.global.tls
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspecglobal)</sup></sup>



TLS configuration for the cluster.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Global switch to turn on or off the TLS configurations.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspecglobaltlszookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the ZooKeeper component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.global.tls.zookeeper
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspecglobaltls)</sup></sup>



TLS configurations related to the ZooKeeper component.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Enable tls for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>tlsSecretName</b></td>
        <td>string</td>
        <td>
          Enable certificates for this component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspec)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Annotations to add to each ZooKeeper resource.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>component</b></td>
        <td>string</td>
        <td>
          Base name of the ZooKeeper component. Default is 'zookeeper'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to the ZooKeeper server.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeeperdatavolume">dataVolume</a></b></td>
        <td>object</td>
        <td>
          Volume configuration for ZooKeeper data.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>gracePeriod</b></td>
        <td>integer</td>
        <td>
          Termination grace period in seconds for the ZooKeeper pod. Default value is 60.<br/>
          <br/>
            <i>Minimum</i>: 0<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          Pulsar image to use for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          Pulsar image pull policy to use for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeepermetadatainitializationjob">metadataInitializationJob</a></b></td>
        <td>object</td>
        <td>
          Configuration about the job that initializes the Pulsar cluster creating the needed ZooKeeper nodes.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodeSelectors</b></td>
        <td>map[string]string</td>
        <td>
          Additional node selectors for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeeperpdb">pdb</a></b></td>
        <td>object</td>
        <td>
          Pod disruption budget configuration for the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>podManagementPolicy</b></td>
        <td>string</td>
        <td>
          Pod management policy for the ZooKeeper pod. Default value is 'Parallel'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeeperprobe">probe</a></b></td>
        <td>object</td>
        <td>
          Liveness and readiness probe values.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>replicas</b></td>
        <td>integer</td>
        <td>
          Replicas of ZooKeeper instances.<br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeeperresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeeperservice">service</a></b></td>
        <td>object</td>
        <td>
          Configurations for the Service resources associated to the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeeperupdatestrategy">updateStrategy</a></b></td>
        <td>object</td>
        <td>
          Update strategy for the ZooKeeper pod. Default value is rolling update.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.dataVolume
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeeper)</sup></sup>



Volume configuration for ZooKeeper data.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>existingStorageClassName</b></td>
        <td>string</td>
        <td>
          Indicates if an already existing storage class should be used.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          Indicates the suffix for the volume. Default value is 'data'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>size</b></td>
        <td>string</td>
        <td>
          Indicates the requested size for the volume. The format follows the Kubernetes' Quantity. Default value is '5Gi'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeeperdatavolumestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.dataVolume.storageClass
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeeperdatavolume)</sup></sup>



Indicates if a StorageClass is used. The operator will create the StorageClass if needed.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>extraParams</b></td>
        <td>map[string]string</td>
        <td>
          Adds extra parameters for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>fsType</b></td>
        <td>string</td>
        <td>
          Indicates the 'fsType' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>provisioner</b></td>
        <td>string</td>
        <td>
          Indicates the provisioner property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reclaimPolicy</b></td>
        <td>string</td>
        <td>
          Indicates the reclaimPolicy property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Indicates the 'type' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.metadataInitializationJob
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeeper)</sup></sup>



Configuration about the job that initializes the Pulsar cluster creating the needed ZooKeeper nodes.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeepermetadatainitializationjobresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the Job's Pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>timeout</b></td>
        <td>integer</td>
        <td>
          Timeout (in seconds) for the metadata initialization execution. Default value is 60.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.metadataInitializationJob.resources
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeepermetadatainitializationjob)</sup></sup>



Resource requirements for the Job's Pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>limits</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>requests</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.pdb
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeeper)</sup></sup>



Pod disruption budget configuration for the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Indicates if the Pdb policy is enabled for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>maxUnavailable</b></td>
        <td>integer</td>
        <td>
          Indicates the maxUnavailable property for the Pdb.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.probe
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeeper)</sup></sup>



Liveness and readiness probe values.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Indicates whether the probe is enabled or not.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>initial</b></td>
        <td>integer</td>
        <td>
          Indicates the initial delay (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>period</b></td>
        <td>integer</td>
        <td>
          Indicates the period (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>timeout</b></td>
        <td>integer</td>
        <td>
          Indicates the timeout (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.resources
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeeper)</sup></sup>



Resource requirements for the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>limits</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>requests</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.service
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeeper)</sup></sup>



Configurations for the Service resources associated to the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeeperserviceadditionalportsindex">additionalPorts</a></b></td>
        <td>[]object</td>
        <td>
          Additional ports for the ZooKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Additional annotations to add to the ZooKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.service.additionalPorts[index]
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeeperservice)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>appProtocol</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodePort</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>port</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>protocol</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>targetPort</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.updateStrategy
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeeper)</sup></sup>



Update strategy for the ZooKeeper pod. Default value is rolling update.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#pulsarclusterstatuscurrentspeczookeeperupdatestrategyrollingupdate">rollingUpdate</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.status.currentSpec.zookeeper.updateStrategy.rollingUpdate
<sup><sup>[↩ Parent](#pulsarclusterstatuscurrentspeczookeeperupdatestrategy)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>maxUnavailable</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>partition</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>

## ZooKeeper
<sup><sup>[↩ Parent](#comdatastaxossv1alpha1 )</sup></sup>








<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
      <td><b>apiVersion</b></td>
      <td>string</td>
      <td>com.datastax.oss/v1alpha1</td>
      <td>true</td>
      </tr>
      <tr>
      <td><b>kind</b></td>
      <td>string</td>
      <td>ZooKeeper</td>
      <td>true</td>
      </tr>
      <tr>
      <td><b><a href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.20/#objectmeta-v1-meta">metadata</a></b></td>
      <td>object</td>
      <td>Refer to the Kubernetes API documentation for the fields of the `metadata` field.</td>
      <td>true</td>
      </tr><tr>
        <td><b><a href="#zookeeperspec">spec</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatus">status</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec
<sup><sup>[↩ Parent](#zookeeper)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#zookeeperspecglobal">global</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspeczookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.global
<sup><sup>[↩ Parent](#zookeeperspec)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          Pulsar cluster base name.<br/>
        </td>
        <td>true</td>
      </tr><tr>
        <td><b><a href="#zookeeperspecglobaldnsconfig">dnsConfig</a></b></td>
        <td>object</td>
        <td>
          Additional DNS config for each pod created by the operator.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          Default Pulsar image to use. Any components can be configured to use a different image.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          Default Pulsar image pull policy to use. Any components can be configured to use a different image pull policy. Default value is 'IfNotPresent'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>kubernetesClusterDomain</b></td>
        <td>string</td>
        <td>
          The domain name for your kubernetes cluster.
This domain is documented here: https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#a-aaaa-records-1 .
It's used to fully qualify service names when configuring Pulsar.
The default value is 'cluster.local'.
<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodeSelectors</b></td>
        <td>map[string]string</td>
        <td>
          Global node selector. If set, this will apply to all components.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>persistence</b></td>
        <td>boolean</td>
        <td>
          If persistence is enabled, components that has state will be deployed with PersistentVolumeClaims, otherwise, for test purposes, they will be deployed with emptDir
<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspecglobalstorage">storage</a></b></td>
        <td>object</td>
        <td>
          Storage configuration.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspecglobaltls">tls</a></b></td>
        <td>object</td>
        <td>
          TLS configuration for the cluster.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.global.dnsConfig
<sup><sup>[↩ Parent](#zookeeperspecglobal)</sup></sup>



Additional DNS config for each pod created by the operator.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>nameservers</b></td>
        <td>[]string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspecglobaldnsconfigoptionsindex">options</a></b></td>
        <td>[]object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>searches</b></td>
        <td>[]string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.global.dnsConfig.options[index]
<sup><sup>[↩ Parent](#zookeeperspecglobaldnsconfig)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>value</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.global.storage
<sup><sup>[↩ Parent](#zookeeperspecglobal)</sup></sup>



Storage configuration.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>existingStorageClassName</b></td>
        <td>string</td>
        <td>
          Indicates if an already existing storage class should be used.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspecglobalstoragestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.global.storage.storageClass
<sup><sup>[↩ Parent](#zookeeperspecglobalstorage)</sup></sup>



Indicates if a StorageClass is used. The operator will create the StorageClass if needed.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>extraParams</b></td>
        <td>map[string]string</td>
        <td>
          Adds extra parameters for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>fsType</b></td>
        <td>string</td>
        <td>
          Indicates the 'fsType' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>provisioner</b></td>
        <td>string</td>
        <td>
          Indicates the provisioner property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reclaimPolicy</b></td>
        <td>string</td>
        <td>
          Indicates the reclaimPolicy property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Indicates the 'type' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.global.tls
<sup><sup>[↩ Parent](#zookeeperspecglobal)</sup></sup>



TLS configuration for the cluster.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Global switch to turn on or off the TLS configurations.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspecglobaltlszookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the ZooKeeper component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.global.tls.zookeeper
<sup><sup>[↩ Parent](#zookeeperspecglobaltls)</sup></sup>



TLS configurations related to the ZooKeeper component.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Enable tls for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>tlsSecretName</b></td>
        <td>string</td>
        <td>
          Enable certificates for this component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper
<sup><sup>[↩ Parent](#zookeeperspec)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Annotations to add to each ZooKeeper resource.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>component</b></td>
        <td>string</td>
        <td>
          Base name of the ZooKeeper component. Default is 'zookeeper'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to the ZooKeeper server.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspeczookeeperdatavolume">dataVolume</a></b></td>
        <td>object</td>
        <td>
          Volume configuration for ZooKeeper data.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>gracePeriod</b></td>
        <td>integer</td>
        <td>
          Termination grace period in seconds for the ZooKeeper pod. Default value is 60.<br/>
          <br/>
            <i>Minimum</i>: 0<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          Pulsar image to use for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          Pulsar image pull policy to use for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspeczookeepermetadatainitializationjob">metadataInitializationJob</a></b></td>
        <td>object</td>
        <td>
          Configuration about the job that initializes the Pulsar cluster creating the needed ZooKeeper nodes.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodeSelectors</b></td>
        <td>map[string]string</td>
        <td>
          Additional node selectors for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspeczookeeperpdb">pdb</a></b></td>
        <td>object</td>
        <td>
          Pod disruption budget configuration for the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>podManagementPolicy</b></td>
        <td>string</td>
        <td>
          Pod management policy for the ZooKeeper pod. Default value is 'Parallel'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspeczookeeperprobe">probe</a></b></td>
        <td>object</td>
        <td>
          Liveness and readiness probe values.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>replicas</b></td>
        <td>integer</td>
        <td>
          Replicas of ZooKeeper instances.<br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspeczookeeperresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspeczookeeperservice">service</a></b></td>
        <td>object</td>
        <td>
          Configurations for the Service resources associated to the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspeczookeeperupdatestrategy">updateStrategy</a></b></td>
        <td>object</td>
        <td>
          Update strategy for the ZooKeeper pod. Default value is rolling update.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.dataVolume
<sup><sup>[↩ Parent](#zookeeperspeczookeeper)</sup></sup>



Volume configuration for ZooKeeper data.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>existingStorageClassName</b></td>
        <td>string</td>
        <td>
          Indicates if an already existing storage class should be used.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          Indicates the suffix for the volume. Default value is 'data'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>size</b></td>
        <td>string</td>
        <td>
          Indicates the requested size for the volume. The format follows the Kubernetes' Quantity. Default value is '5Gi'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspeczookeeperdatavolumestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.dataVolume.storageClass
<sup><sup>[↩ Parent](#zookeeperspeczookeeperdatavolume)</sup></sup>



Indicates if a StorageClass is used. The operator will create the StorageClass if needed.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>extraParams</b></td>
        <td>map[string]string</td>
        <td>
          Adds extra parameters for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>fsType</b></td>
        <td>string</td>
        <td>
          Indicates the 'fsType' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>provisioner</b></td>
        <td>string</td>
        <td>
          Indicates the provisioner property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reclaimPolicy</b></td>
        <td>string</td>
        <td>
          Indicates the reclaimPolicy property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Indicates the 'type' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.metadataInitializationJob
<sup><sup>[↩ Parent](#zookeeperspeczookeeper)</sup></sup>



Configuration about the job that initializes the Pulsar cluster creating the needed ZooKeeper nodes.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#zookeeperspeczookeepermetadatainitializationjobresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the Job's Pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>timeout</b></td>
        <td>integer</td>
        <td>
          Timeout (in seconds) for the metadata initialization execution. Default value is 60.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.metadataInitializationJob.resources
<sup><sup>[↩ Parent](#zookeeperspeczookeepermetadatainitializationjob)</sup></sup>



Resource requirements for the Job's Pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>limits</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>requests</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.pdb
<sup><sup>[↩ Parent](#zookeeperspeczookeeper)</sup></sup>



Pod disruption budget configuration for the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Indicates if the Pdb policy is enabled for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>maxUnavailable</b></td>
        <td>integer</td>
        <td>
          Indicates the maxUnavailable property for the Pdb.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.probe
<sup><sup>[↩ Parent](#zookeeperspeczookeeper)</sup></sup>



Liveness and readiness probe values.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Indicates whether the probe is enabled or not.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>initial</b></td>
        <td>integer</td>
        <td>
          Indicates the initial delay (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>period</b></td>
        <td>integer</td>
        <td>
          Indicates the period (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>timeout</b></td>
        <td>integer</td>
        <td>
          Indicates the timeout (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.resources
<sup><sup>[↩ Parent](#zookeeperspeczookeeper)</sup></sup>



Resource requirements for the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>limits</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>requests</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.service
<sup><sup>[↩ Parent](#zookeeperspeczookeeper)</sup></sup>



Configurations for the Service resources associated to the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#zookeeperspeczookeeperserviceadditionalportsindex">additionalPorts</a></b></td>
        <td>[]object</td>
        <td>
          Additional ports for the ZooKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Additional annotations to add to the ZooKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.service.additionalPorts[index]
<sup><sup>[↩ Parent](#zookeeperspeczookeeperservice)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>appProtocol</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodePort</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>port</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>protocol</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>targetPort</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.updateStrategy
<sup><sup>[↩ Parent](#zookeeperspeczookeeper)</sup></sup>



Update strategy for the ZooKeeper pod. Default value is rolling update.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#zookeeperspeczookeeperupdatestrategyrollingupdate">rollingUpdate</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.spec.zookeeper.updateStrategy.rollingUpdate
<sup><sup>[↩ Parent](#zookeeperspeczookeeperupdatestrategy)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>maxUnavailable</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>partition</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status
<sup><sup>[↩ Parent](#zookeeper)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#zookeeperstatuscurrentspec">currentSpec</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>error</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec
<sup><sup>[↩ Parent](#zookeeperstatus)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#zookeeperstatuscurrentspecglobal">global</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.global
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspec)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          Pulsar cluster base name.<br/>
        </td>
        <td>true</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspecglobaldnsconfig">dnsConfig</a></b></td>
        <td>object</td>
        <td>
          Additional DNS config for each pod created by the operator.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          Default Pulsar image to use. Any components can be configured to use a different image.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          Default Pulsar image pull policy to use. Any components can be configured to use a different image pull policy. Default value is 'IfNotPresent'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>kubernetesClusterDomain</b></td>
        <td>string</td>
        <td>
          The domain name for your kubernetes cluster.
This domain is documented here: https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#a-aaaa-records-1 .
It's used to fully qualify service names when configuring Pulsar.
The default value is 'cluster.local'.
<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodeSelectors</b></td>
        <td>map[string]string</td>
        <td>
          Global node selector. If set, this will apply to all components.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>persistence</b></td>
        <td>boolean</td>
        <td>
          If persistence is enabled, components that has state will be deployed with PersistentVolumeClaims, otherwise, for test purposes, they will be deployed with emptDir
<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspecglobalstorage">storage</a></b></td>
        <td>object</td>
        <td>
          Storage configuration.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspecglobaltls">tls</a></b></td>
        <td>object</td>
        <td>
          TLS configuration for the cluster.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.global.dnsConfig
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspecglobal)</sup></sup>



Additional DNS config for each pod created by the operator.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>nameservers</b></td>
        <td>[]string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspecglobaldnsconfigoptionsindex">options</a></b></td>
        <td>[]object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>searches</b></td>
        <td>[]string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.global.dnsConfig.options[index]
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspecglobaldnsconfig)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>value</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.global.storage
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspecglobal)</sup></sup>



Storage configuration.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>existingStorageClassName</b></td>
        <td>string</td>
        <td>
          Indicates if an already existing storage class should be used.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspecglobalstoragestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.global.storage.storageClass
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspecglobalstorage)</sup></sup>



Indicates if a StorageClass is used. The operator will create the StorageClass if needed.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>extraParams</b></td>
        <td>map[string]string</td>
        <td>
          Adds extra parameters for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>fsType</b></td>
        <td>string</td>
        <td>
          Indicates the 'fsType' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>provisioner</b></td>
        <td>string</td>
        <td>
          Indicates the provisioner property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reclaimPolicy</b></td>
        <td>string</td>
        <td>
          Indicates the reclaimPolicy property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Indicates the 'type' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.global.tls
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspecglobal)</sup></sup>



TLS configuration for the cluster.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Global switch to turn on or off the TLS configurations.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspecglobaltlszookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the ZooKeeper component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.global.tls.zookeeper
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspecglobaltls)</sup></sup>



TLS configurations related to the ZooKeeper component.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Enable tls for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>tlsSecretName</b></td>
        <td>string</td>
        <td>
          Enable certificates for this component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspec)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Annotations to add to each ZooKeeper resource.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>component</b></td>
        <td>string</td>
        <td>
          Base name of the ZooKeeper component. Default is 'zookeeper'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to the ZooKeeper server.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeeperdatavolume">dataVolume</a></b></td>
        <td>object</td>
        <td>
          Volume configuration for ZooKeeper data.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>gracePeriod</b></td>
        <td>integer</td>
        <td>
          Termination grace period in seconds for the ZooKeeper pod. Default value is 60.<br/>
          <br/>
            <i>Minimum</i>: 0<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          Pulsar image to use for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          Pulsar image pull policy to use for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeepermetadatainitializationjob">metadataInitializationJob</a></b></td>
        <td>object</td>
        <td>
          Configuration about the job that initializes the Pulsar cluster creating the needed ZooKeeper nodes.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodeSelectors</b></td>
        <td>map[string]string</td>
        <td>
          Additional node selectors for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeeperpdb">pdb</a></b></td>
        <td>object</td>
        <td>
          Pod disruption budget configuration for the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>podManagementPolicy</b></td>
        <td>string</td>
        <td>
          Pod management policy for the ZooKeeper pod. Default value is 'Parallel'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeeperprobe">probe</a></b></td>
        <td>object</td>
        <td>
          Liveness and readiness probe values.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>replicas</b></td>
        <td>integer</td>
        <td>
          Replicas of ZooKeeper instances.<br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeeperresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeeperservice">service</a></b></td>
        <td>object</td>
        <td>
          Configurations for the Service resources associated to the ZooKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeeperupdatestrategy">updateStrategy</a></b></td>
        <td>object</td>
        <td>
          Update strategy for the ZooKeeper pod. Default value is rolling update.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.dataVolume
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeeper)</sup></sup>



Volume configuration for ZooKeeper data.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>existingStorageClassName</b></td>
        <td>string</td>
        <td>
          Indicates if an already existing storage class should be used.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          Indicates the suffix for the volume. Default value is 'data'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>size</b></td>
        <td>string</td>
        <td>
          Indicates the requested size for the volume. The format follows the Kubernetes' Quantity. Default value is '5Gi'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeeperdatavolumestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.dataVolume.storageClass
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeeperdatavolume)</sup></sup>



Indicates if a StorageClass is used. The operator will create the StorageClass if needed.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>extraParams</b></td>
        <td>map[string]string</td>
        <td>
          Adds extra parameters for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>fsType</b></td>
        <td>string</td>
        <td>
          Indicates the 'fsType' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>provisioner</b></td>
        <td>string</td>
        <td>
          Indicates the provisioner property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reclaimPolicy</b></td>
        <td>string</td>
        <td>
          Indicates the reclaimPolicy property for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Indicates the 'type' parameter for the StorageClass.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.metadataInitializationJob
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeeper)</sup></sup>



Configuration about the job that initializes the Pulsar cluster creating the needed ZooKeeper nodes.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeepermetadatainitializationjobresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the Job's Pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>timeout</b></td>
        <td>integer</td>
        <td>
          Timeout (in seconds) for the metadata initialization execution. Default value is 60.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.metadataInitializationJob.resources
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeepermetadatainitializationjob)</sup></sup>



Resource requirements for the Job's Pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>limits</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>requests</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.pdb
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeeper)</sup></sup>



Pod disruption budget configuration for the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Indicates if the Pdb policy is enabled for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>maxUnavailable</b></td>
        <td>integer</td>
        <td>
          Indicates the maxUnavailable property for the Pdb.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.probe
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeeper)</sup></sup>



Liveness and readiness probe values.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Indicates whether the probe is enabled or not.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>initial</b></td>
        <td>integer</td>
        <td>
          Indicates the initial delay (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>period</b></td>
        <td>integer</td>
        <td>
          Indicates the period (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>timeout</b></td>
        <td>integer</td>
        <td>
          Indicates the timeout (in seconds) for the probe.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.resources
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeeper)</sup></sup>



Resource requirements for the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>limits</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>requests</b></td>
        <td>map[string]int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.service
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeeper)</sup></sup>



Configurations for the Service resources associated to the ZooKeeper pod.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeeperserviceadditionalportsindex">additionalPorts</a></b></td>
        <td>[]object</td>
        <td>
          Additional ports for the ZooKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Additional annotations to add to the ZooKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.service.additionalPorts[index]
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeeperservice)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>appProtocol</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>name</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>nodePort</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>port</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>protocol</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>targetPort</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.updateStrategy
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeeper)</sup></sup>



Update strategy for the ZooKeeper pod. Default value is rolling update.

<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b><a href="#zookeeperstatuscurrentspeczookeeperupdatestrategyrollingupdate">rollingUpdate</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### ZooKeeper.status.currentSpec.zookeeper.updateStrategy.rollingUpdate
<sup><sup>[↩ Parent](#zookeeperstatuscurrentspeczookeeperupdatestrategy)</sup></sup>





<table>
    <thead>
        <tr>
            <th>Name</th>
            <th>Type</th>
            <th>Description</th>
            <th>Required</th>
        </tr>
    </thead>
    <tbody><tr>
        <td><b>maxUnavailable</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>partition</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>