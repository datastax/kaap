# API Reference

Packages:

- [com.datastax.oss/v1alpha1](#comdatastaxossv1alpha1)

# com.datastax.oss/v1alpha1

Resource Types:

- [BookKeeper](#bookkeeper)

- [Broker](#broker)

- [Proxy](#proxy)

- [PulsarCluster](#pulsarcluster)

- [ZooKeeper](#zookeeper)




## BookKeeper
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
      <td>BookKeeper</td>
      <td>true</td>
      </tr>
      <tr>
      <td><b><a href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.20/#objectmeta-v1-meta">metadata</a></b></td>
      <td>object</td>
      <td>Refer to the Kubernetes API documentation for the fields of the `metadata` field.</td>
      <td>true</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspec">spec</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperstatus">status</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec
<sup><sup>[↩ Parent](#bookkeeper)</sup></sup>





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
        <td><b><a href="#bookkeeperspecbookkeeper">bookkeeper</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecglobal">global</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec.bookkeeper
<sup><sup>[↩ Parent](#bookkeeperspec)</sup></sup>





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
          Annotations to add to each BookKeeper resource.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>gracePeriod</b></td>
        <td>integer</td>
        <td>
          Termination grace period in seconds for the BookKeeper pod. Default value is 60.<br/>
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
        <td><b>nodeSelectors</b></td>
        <td>map[string]string</td>
        <td>
          Additional node selectors for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecbookkeeperpdb">pdb</a></b></td>
        <td>object</td>
        <td>
          Pod disruption budget configuration for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>podManagementPolicy</b></td>
        <td>string</td>
        <td>
          Pod management policy for the BookKeeper pod. Default value is 'Parallel'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecbookkeeperprobe">probe</a></b></td>
        <td>object</td>
        <td>
          Liveness and readiness probe values.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>pvcPrefix</b></td>
        <td>string</td>
        <td>
          Prefix for each PVC created.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>replicas</b></td>
        <td>integer</td>
        <td>
          Replicas of this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecbookkeeperresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the BookKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecbookkeeperservice">service</a></b></td>
        <td>object</td>
        <td>
          Configurations for the Service resources associated to the BookKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecbookkeeperupdatestrategy">updateStrategy</a></b></td>
        <td>object</td>
        <td>
          Update strategy for the BookKeeper pod/s. Default value is rolling update.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecbookkeepervolumes">volumes</a></b></td>
        <td>object</td>
        <td>
          Volumes configuration.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec.bookkeeper.pdb
<sup><sup>[↩ Parent](#bookkeeperspecbookkeeper)</sup></sup>



Pod disruption budget configuration for this component.

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


### BookKeeper.spec.bookkeeper.probe
<sup><sup>[↩ Parent](#bookkeeperspecbookkeeper)</sup></sup>



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


### BookKeeper.spec.bookkeeper.resources
<sup><sup>[↩ Parent](#bookkeeperspecbookkeeper)</sup></sup>



Resource requirements for the BookKeeper pod.

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


### BookKeeper.spec.bookkeeper.service
<sup><sup>[↩ Parent](#bookkeeperspecbookkeeper)</sup></sup>



Configurations for the Service resources associated to the BookKeeper pod.

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
        <td><b><a href="#bookkeeperspecbookkeeperserviceadditionalportsindex">additionalPorts</a></b></td>
        <td>[]object</td>
        <td>
          Additional ports for the BookKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Additional annotations to add to the BookKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec.bookkeeper.service.additionalPorts[index]
<sup><sup>[↩ Parent](#bookkeeperspecbookkeeperservice)</sup></sup>





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


### BookKeeper.spec.bookkeeper.updateStrategy
<sup><sup>[↩ Parent](#bookkeeperspecbookkeeper)</sup></sup>



Update strategy for the BookKeeper pod/s. Default value is rolling update.

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
        <td><b><a href="#bookkeeperspecbookkeeperupdatestrategyrollingupdate">rollingUpdate</a></b></td>
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


### BookKeeper.spec.bookkeeper.updateStrategy.rollingUpdate
<sup><sup>[↩ Parent](#bookkeeperspecbookkeeperupdatestrategy)</sup></sup>





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


### BookKeeper.spec.bookkeeper.volumes
<sup><sup>[↩ Parent](#bookkeeperspecbookkeeper)</sup></sup>



Volumes configuration.

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
        <td><b><a href="#bookkeeperspecbookkeepervolumesjournal">journal</a></b></td>
        <td>object</td>
        <td>
          Indicates the volume config for the journal.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecbookkeepervolumesledgers">ledgers</a></b></td>
        <td>object</td>
        <td>
          Indicates the volume config for the ledgers.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec.bookkeeper.volumes.journal
<sup><sup>[↩ Parent](#bookkeeperspecbookkeepervolumes)</sup></sup>



Indicates the volume config for the journal.

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
        <td><b><a href="#bookkeeperspecbookkeepervolumesjournalstorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec.bookkeeper.volumes.journal.storageClass
<sup><sup>[↩ Parent](#bookkeeperspecbookkeepervolumesjournal)</sup></sup>



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


### BookKeeper.spec.bookkeeper.volumes.ledgers
<sup><sup>[↩ Parent](#bookkeeperspecbookkeepervolumes)</sup></sup>



Indicates the volume config for the ledgers.

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
        <td><b><a href="#bookkeeperspecbookkeepervolumesledgersstorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec.bookkeeper.volumes.ledgers.storageClass
<sup><sup>[↩ Parent](#bookkeeperspecbookkeepervolumesledgers)</sup></sup>



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


### BookKeeper.spec.global
<sup><sup>[↩ Parent](#bookkeeperspec)</sup></sup>





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
        <td><b><a href="#bookkeeperspecglobalcomponents">components</a></b></td>
        <td>object</td>
        <td>
          Pulsar cluster components names.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecglobaldnsconfig">dnsConfig</a></b></td>
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
        <td><b><a href="#bookkeeperspecglobalstorage">storage</a></b></td>
        <td>object</td>
        <td>
          Storage configuration.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecglobaltls">tls</a></b></td>
        <td>object</td>
        <td>
          TLS configuration for the cluster.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec.global.components
<sup><sup>[↩ Parent](#bookkeeperspecglobal)</sup></sup>



Pulsar cluster components names.

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
        <td><b>bookkeeperBaseName</b></td>
        <td>string</td>
        <td>
          BookKeeper base name. Default value is 'bookkeeper'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>brokerBaseName</b></td>
        <td>string</td>
        <td>
          Broker base name. Default value is 'broker'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>proxyBaseName</b></td>
        <td>string</td>
        <td>
          Proxy base name. Default value is 'proxy'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>zookeeperBaseName</b></td>
        <td>string</td>
        <td>
          Zookeeper base name. Default value is 'zookeeper'.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec.global.dnsConfig
<sup><sup>[↩ Parent](#bookkeeperspecglobal)</sup></sup>



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
        <td><b><a href="#bookkeeperspecglobaldnsconfigoptionsindex">options</a></b></td>
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


### BookKeeper.spec.global.dnsConfig.options[index]
<sup><sup>[↩ Parent](#bookkeeperspecglobaldnsconfig)</sup></sup>





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


### BookKeeper.spec.global.storage
<sup><sup>[↩ Parent](#bookkeeperspecglobal)</sup></sup>



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
        <td><b><a href="#bookkeeperspecglobalstoragestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec.global.storage.storageClass
<sup><sup>[↩ Parent](#bookkeeperspecglobalstorage)</sup></sup>



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


### BookKeeper.spec.global.tls
<sup><sup>[↩ Parent](#bookkeeperspecglobal)</sup></sup>



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
        <td><b><a href="#bookkeeperspecglobaltlsbookkeeper">bookkeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the BookKeeper component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecglobaltlsbroker">broker</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the broker component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>defaultSecretName</b></td>
        <td>string</td>
        <td>
          Default secret name.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Global switch to turn on or off the TLS configurations.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#bookkeeperspecglobaltlszookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the ZooKeeper component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### BookKeeper.spec.global.tls.bookkeeper
<sup><sup>[↩ Parent](#bookkeeperspecglobaltls)</sup></sup>



TLS configurations related to the BookKeeper component.

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


### BookKeeper.spec.global.tls.broker
<sup><sup>[↩ Parent](#bookkeeperspecglobaltls)</sup></sup>



TLS configurations related to the broker component.

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


### BookKeeper.spec.global.tls.zookeeper
<sup><sup>[↩ Parent](#bookkeeperspecglobaltls)</sup></sup>



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


### BookKeeper.status
<sup><sup>[↩ Parent](#bookkeeper)</sup></sup>





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
        <td><b>message</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>ready</b></td>
        <td>boolean</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reason</b></td>
        <td>enum</td>
        <td>
          <br/>
          <br/>
            <i>Enum</i>: ErrorUpgrading, ErrorConfig<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>

## Broker
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
      <td>Broker</td>
      <td>true</td>
      </tr>
      <tr>
      <td><b><a href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.20/#objectmeta-v1-meta">metadata</a></b></td>
      <td>object</td>
      <td>Refer to the Kubernetes API documentation for the fields of the `metadata` field.</td>
      <td>true</td>
      </tr><tr>
        <td><b><a href="#brokerspec">spec</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerstatus">status</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec
<sup><sup>[↩ Parent](#broker)</sup></sup>





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
        <td><b><a href="#brokerspecbroker">broker</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecglobal">global</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.broker
<sup><sup>[↩ Parent](#brokerspec)</sup></sup>





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
          Annotations to add to each Broker resource.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecbrokerautoscaler">autoscaler</a></b></td>
        <td>object</td>
        <td>
          Autoscaling config.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>functionsWorkerEnabled</b></td>
        <td>boolean</td>
        <td>
          Enable functions worker embedded in the broker.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>gracePeriod</b></td>
        <td>integer</td>
        <td>
          Termination grace period in seconds for the Broker pod. Default value is 60.<br/>
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
        <td><b><a href="#brokerspecbrokerinitcontainer">initContainer</a></b></td>
        <td>object</td>
        <td>
          Additional init container.<br/>
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
        <td><b><a href="#brokerspecbrokerpdb">pdb</a></b></td>
        <td>object</td>
        <td>
          Pod disruption budget configuration for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>podManagementPolicy</b></td>
        <td>string</td>
        <td>
          Pod management policy for the Broker pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecbrokerprobe">probe</a></b></td>
        <td>object</td>
        <td>
          Liveness and readiness probe values.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>replicas</b></td>
        <td>integer</td>
        <td>
          Replicas of this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecbrokerresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the Broker pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecbrokerservice">service</a></b></td>
        <td>object</td>
        <td>
          Configurations for the Service resources associated to the Broker pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>serviceAccountName</b></td>
        <td>string</td>
        <td>
          Service account name for the Broker StatefulSet.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecbrokertransactions">transactions</a></b></td>
        <td>object</td>
        <td>
          Enable transactions in the broker.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecbrokerupdatestrategy">updateStrategy</a></b></td>
        <td>object</td>
        <td>
          Update strategy for the Broker pod/s. <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>webSocketServiceEnabled</b></td>
        <td>boolean</td>
        <td>
          Enable websocket service in the broker.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.broker.autoscaler
<sup><sup>[↩ Parent](#brokerspecbroker)</sup></sup>



Autoscaling config.

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
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>higherCpuThreshold</b></td>
        <td>number</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 0<br/>
            <i>Maximum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>lowerCpuThreshold</b></td>
        <td>number</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 0<br/>
            <i>Maximum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>max</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>min</b></td>
        <td>integer</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>periodMs</b></td>
        <td>integer</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 1000<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>scaleDownBy</b></td>
        <td>integer</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>scaleUpBy</b></td>
        <td>integer</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>stabilizationWindowMs</b></td>
        <td>integer</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.broker.initContainer
<sup><sup>[↩ Parent](#brokerspecbroker)</sup></sup>



Additional init container.

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
        <td><b>args</b></td>
        <td>[]string</td>
        <td>
          The command args used for the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>command</b></td>
        <td>[]string</td>
        <td>
          The command used for the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>emptyDirPath</b></td>
        <td>string</td>
        <td>
          The container path where the emptyDir volume is mounted.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          The image used to run the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          The image pull policy used for the container.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.broker.pdb
<sup><sup>[↩ Parent](#brokerspecbroker)</sup></sup>



Pod disruption budget configuration for this component.

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


### Broker.spec.broker.probe
<sup><sup>[↩ Parent](#brokerspecbroker)</sup></sup>



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


### Broker.spec.broker.resources
<sup><sup>[↩ Parent](#brokerspecbroker)</sup></sup>



Resource requirements for the Broker pod.

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


### Broker.spec.broker.service
<sup><sup>[↩ Parent](#brokerspecbroker)</sup></sup>



Configurations for the Service resources associated to the Broker pod.

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
        <td><b><a href="#brokerspecbrokerserviceadditionalportsindex">additionalPorts</a></b></td>
        <td>[]object</td>
        <td>
          Additional ports for the Broker Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Additional annotations to add to the Broker Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>headless</b></td>
        <td>boolean</td>
        <td>
          Do not assign an IP to the Service.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Service type. Default value is 'ClusterIP'<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.broker.service.additionalPorts[index]
<sup><sup>[↩ Parent](#brokerspecbrokerservice)</sup></sup>





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


### Broker.spec.broker.transactions
<sup><sup>[↩ Parent](#brokerspecbroker)</sup></sup>



Enable transactions in the broker.

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
          Enable the transaction coordinator in the broker.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecbrokertransactionsinitjob">initJob</a></b></td>
        <td>object</td>
        <td>
          Initialization job configuration.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>partitions</b></td>
        <td>integer</td>
        <td>
          Partitions count for the transaction's topic.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.broker.transactions.initJob
<sup><sup>[↩ Parent](#brokerspecbrokertransactions)</sup></sup>



Initialization job configuration.

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
        <td><b><a href="#brokerspecbrokertransactionsinitjobresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the Job's Pod.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.broker.transactions.initJob.resources
<sup><sup>[↩ Parent](#brokerspecbrokertransactionsinitjob)</sup></sup>



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


### Broker.spec.broker.updateStrategy
<sup><sup>[↩ Parent](#brokerspecbroker)</sup></sup>



Update strategy for the Broker pod/s. 

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
        <td><b><a href="#brokerspecbrokerupdatestrategyrollingupdate">rollingUpdate</a></b></td>
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


### Broker.spec.broker.updateStrategy.rollingUpdate
<sup><sup>[↩ Parent](#brokerspecbrokerupdatestrategy)</sup></sup>





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


### Broker.spec.global
<sup><sup>[↩ Parent](#brokerspec)</sup></sup>





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
        <td><b><a href="#brokerspecglobalcomponents">components</a></b></td>
        <td>object</td>
        <td>
          Pulsar cluster components names.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecglobaldnsconfig">dnsConfig</a></b></td>
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
        <td><b><a href="#brokerspecglobalstorage">storage</a></b></td>
        <td>object</td>
        <td>
          Storage configuration.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecglobaltls">tls</a></b></td>
        <td>object</td>
        <td>
          TLS configuration for the cluster.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.global.components
<sup><sup>[↩ Parent](#brokerspecglobal)</sup></sup>



Pulsar cluster components names.

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
        <td><b>bookkeeperBaseName</b></td>
        <td>string</td>
        <td>
          BookKeeper base name. Default value is 'bookkeeper'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>brokerBaseName</b></td>
        <td>string</td>
        <td>
          Broker base name. Default value is 'broker'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>proxyBaseName</b></td>
        <td>string</td>
        <td>
          Proxy base name. Default value is 'proxy'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>zookeeperBaseName</b></td>
        <td>string</td>
        <td>
          Zookeeper base name. Default value is 'zookeeper'.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.global.dnsConfig
<sup><sup>[↩ Parent](#brokerspecglobal)</sup></sup>



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
        <td><b><a href="#brokerspecglobaldnsconfigoptionsindex">options</a></b></td>
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


### Broker.spec.global.dnsConfig.options[index]
<sup><sup>[↩ Parent](#brokerspecglobaldnsconfig)</sup></sup>





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


### Broker.spec.global.storage
<sup><sup>[↩ Parent](#brokerspecglobal)</sup></sup>



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
        <td><b><a href="#brokerspecglobalstoragestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.global.storage.storageClass
<sup><sup>[↩ Parent](#brokerspecglobalstorage)</sup></sup>



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


### Broker.spec.global.tls
<sup><sup>[↩ Parent](#brokerspecglobal)</sup></sup>



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
        <td><b><a href="#brokerspecglobaltlsbookkeeper">bookkeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the BookKeeper component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecglobaltlsbroker">broker</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the broker component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>defaultSecretName</b></td>
        <td>string</td>
        <td>
          Default secret name.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Global switch to turn on or off the TLS configurations.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#brokerspecglobaltlszookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the ZooKeeper component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Broker.spec.global.tls.bookkeeper
<sup><sup>[↩ Parent](#brokerspecglobaltls)</sup></sup>



TLS configurations related to the BookKeeper component.

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


### Broker.spec.global.tls.broker
<sup><sup>[↩ Parent](#brokerspecglobaltls)</sup></sup>



TLS configurations related to the broker component.

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


### Broker.spec.global.tls.zookeeper
<sup><sup>[↩ Parent](#brokerspecglobaltls)</sup></sup>



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


### Broker.status
<sup><sup>[↩ Parent](#broker)</sup></sup>





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
        <td><b>message</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>ready</b></td>
        <td>boolean</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reason</b></td>
        <td>enum</td>
        <td>
          <br/>
          <br/>
            <i>Enum</i>: ErrorUpgrading, ErrorConfig<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>

## Proxy
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
      <td>Proxy</td>
      <td>true</td>
      </tr>
      <tr>
      <td><b><a href="https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.20/#objectmeta-v1-meta">metadata</a></b></td>
      <td>object</td>
      <td>Refer to the Kubernetes API documentation for the fields of the `metadata` field.</td>
      <td>true</td>
      </tr><tr>
        <td><b><a href="#proxyspec">spec</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#proxystatus">status</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Proxy.spec
<sup><sup>[↩ Parent](#proxy)</sup></sup>





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
        <td><b><a href="#proxyspecglobal">global</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#proxyspecproxy">proxy</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Proxy.spec.global
<sup><sup>[↩ Parent](#proxyspec)</sup></sup>





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
        <td><b><a href="#proxyspecglobalcomponents">components</a></b></td>
        <td>object</td>
        <td>
          Pulsar cluster components names.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#proxyspecglobaldnsconfig">dnsConfig</a></b></td>
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
        <td><b><a href="#proxyspecglobalstorage">storage</a></b></td>
        <td>object</td>
        <td>
          Storage configuration.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#proxyspecglobaltls">tls</a></b></td>
        <td>object</td>
        <td>
          TLS configuration for the cluster.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Proxy.spec.global.components
<sup><sup>[↩ Parent](#proxyspecglobal)</sup></sup>



Pulsar cluster components names.

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
        <td><b>bookkeeperBaseName</b></td>
        <td>string</td>
        <td>
          BookKeeper base name. Default value is 'bookkeeper'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>brokerBaseName</b></td>
        <td>string</td>
        <td>
          Broker base name. Default value is 'broker'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>proxyBaseName</b></td>
        <td>string</td>
        <td>
          Proxy base name. Default value is 'proxy'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>zookeeperBaseName</b></td>
        <td>string</td>
        <td>
          Zookeeper base name. Default value is 'zookeeper'.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Proxy.spec.global.dnsConfig
<sup><sup>[↩ Parent](#proxyspecglobal)</sup></sup>



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
        <td><b><a href="#proxyspecglobaldnsconfigoptionsindex">options</a></b></td>
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


### Proxy.spec.global.dnsConfig.options[index]
<sup><sup>[↩ Parent](#proxyspecglobaldnsconfig)</sup></sup>





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


### Proxy.spec.global.storage
<sup><sup>[↩ Parent](#proxyspecglobal)</sup></sup>



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
        <td><b><a href="#proxyspecglobalstoragestorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Proxy.spec.global.storage.storageClass
<sup><sup>[↩ Parent](#proxyspecglobalstorage)</sup></sup>



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


### Proxy.spec.global.tls
<sup><sup>[↩ Parent](#proxyspecglobal)</sup></sup>



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
        <td><b><a href="#proxyspecglobaltlsbookkeeper">bookkeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the BookKeeper component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#proxyspecglobaltlsbroker">broker</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the broker component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>defaultSecretName</b></td>
        <td>string</td>
        <td>
          Default secret name.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>enabled</b></td>
        <td>boolean</td>
        <td>
          Global switch to turn on or off the TLS configurations.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#proxyspecglobaltlszookeeper">zookeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the ZooKeeper component.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Proxy.spec.global.tls.bookkeeper
<sup><sup>[↩ Parent](#proxyspecglobaltls)</sup></sup>



TLS configurations related to the BookKeeper component.

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


### Proxy.spec.global.tls.broker
<sup><sup>[↩ Parent](#proxyspecglobaltls)</sup></sup>



TLS configurations related to the broker component.

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


### Proxy.spec.global.tls.zookeeper
<sup><sup>[↩ Parent](#proxyspecglobaltls)</sup></sup>



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


### Proxy.spec.proxy
<sup><sup>[↩ Parent](#proxyspec)</sup></sup>





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
          Annotations to add to each Broker resource.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>gracePeriod</b></td>
        <td>integer</td>
        <td>
          Termination grace period in seconds for the Broker pod. Default value is 60.<br/>
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
        <td><b><a href="#proxyspecproxyinitcontainer">initContainer</a></b></td>
        <td>object</td>
        <td>
          Additional init container.<br/>
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
        <td><b><a href="#proxyspecproxypdb">pdb</a></b></td>
        <td>object</td>
        <td>
          Pod disruption budget configuration for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#proxyspecproxyprobe">probe</a></b></td>
        <td>object</td>
        <td>
          Liveness and readiness probe values.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>replicas</b></td>
        <td>integer</td>
        <td>
          Replicas of this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#proxyspecproxyresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the Broker pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#proxyspecproxyservice">service</a></b></td>
        <td>object</td>
        <td>
          Configurations for the Service resources associated to the Broker pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#proxyspecproxyupdatestrategy">updateStrategy</a></b></td>
        <td>object</td>
        <td>
          Update strategy for the Broker pod/s. <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Proxy.spec.proxy.initContainer
<sup><sup>[↩ Parent](#proxyspecproxy)</sup></sup>



Additional init container.

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
        <td><b>args</b></td>
        <td>[]string</td>
        <td>
          The command args used for the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>command</b></td>
        <td>[]string</td>
        <td>
          The command used for the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>emptyDirPath</b></td>
        <td>string</td>
        <td>
          The container path where the emptyDir volume is mounted.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          The image used to run the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          The image pull policy used for the container.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Proxy.spec.proxy.pdb
<sup><sup>[↩ Parent](#proxyspecproxy)</sup></sup>



Pod disruption budget configuration for this component.

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


### Proxy.spec.proxy.probe
<sup><sup>[↩ Parent](#proxyspecproxy)</sup></sup>



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


### Proxy.spec.proxy.resources
<sup><sup>[↩ Parent](#proxyspecproxy)</sup></sup>



Resource requirements for the Broker pod.

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


### Proxy.spec.proxy.service
<sup><sup>[↩ Parent](#proxyspecproxy)</sup></sup>



Configurations for the Service resources associated to the Broker pod.

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
        <td><b><a href="#proxyspecproxyserviceadditionalportsindex">additionalPorts</a></b></td>
        <td>[]object</td>
        <td>
          Additional ports for the Broker Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Additional annotations to add to the Broker Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>enablePlainTextWithTLS</b></td>
        <td>boolean</td>
        <td>
          Enable plain text connections even if TLS is enabled.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>loadBalancerIP</b></td>
        <td>string</td>
        <td>
          Assign a load balancer IP.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Service type. Default value is 'ClusterIP'<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Proxy.spec.proxy.service.additionalPorts[index]
<sup><sup>[↩ Parent](#proxyspecproxyservice)</sup></sup>





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


### Proxy.spec.proxy.updateStrategy
<sup><sup>[↩ Parent](#proxyspecproxy)</sup></sup>



Update strategy for the Broker pod/s. 

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
        <td><b><a href="#proxyspecproxyupdatestrategyrollingupdate">rollingUpdate</a></b></td>
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


### Proxy.spec.proxy.updateStrategy.rollingUpdate
<sup><sup>[↩ Parent](#proxyspecproxyupdatestrategy)</sup></sup>





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
        <td><b>maxSurge</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>maxUnavailable</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### Proxy.status
<sup><sup>[↩ Parent](#proxy)</sup></sup>





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
        <td><b>message</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>ready</b></td>
        <td>boolean</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reason</b></td>
        <td>enum</td>
        <td>
          <br/>
          <br/>
            <i>Enum</i>: ErrorUpgrading, ErrorConfig<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>

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
        <td><b><a href="#pulsarclusterspecbookkeeper">bookkeeper</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbroker">broker</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecglobal">global</a></b></td>
        <td>object</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecproxy">proxy</a></b></td>
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


### PulsarCluster.spec.bookkeeper
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
          Annotations to add to each BookKeeper resource.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>gracePeriod</b></td>
        <td>integer</td>
        <td>
          Termination grace period in seconds for the BookKeeper pod. Default value is 60.<br/>
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
        <td><b>nodeSelectors</b></td>
        <td>map[string]string</td>
        <td>
          Additional node selectors for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbookkeeperpdb">pdb</a></b></td>
        <td>object</td>
        <td>
          Pod disruption budget configuration for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>podManagementPolicy</b></td>
        <td>string</td>
        <td>
          Pod management policy for the BookKeeper pod. Default value is 'Parallel'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbookkeeperprobe">probe</a></b></td>
        <td>object</td>
        <td>
          Liveness and readiness probe values.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>pvcPrefix</b></td>
        <td>string</td>
        <td>
          Prefix for each PVC created.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>replicas</b></td>
        <td>integer</td>
        <td>
          Replicas of this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbookkeeperresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the BookKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbookkeeperservice">service</a></b></td>
        <td>object</td>
        <td>
          Configurations for the Service resources associated to the BookKeeper pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbookkeeperupdatestrategy">updateStrategy</a></b></td>
        <td>object</td>
        <td>
          Update strategy for the BookKeeper pod/s. Default value is rolling update.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbookkeepervolumes">volumes</a></b></td>
        <td>object</td>
        <td>
          Volumes configuration.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.bookkeeper.pdb
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeeper)</sup></sup>



Pod disruption budget configuration for this component.

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


### PulsarCluster.spec.bookkeeper.probe
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeeper)</sup></sup>



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


### PulsarCluster.spec.bookkeeper.resources
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeeper)</sup></sup>



Resource requirements for the BookKeeper pod.

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


### PulsarCluster.spec.bookkeeper.service
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeeper)</sup></sup>



Configurations for the Service resources associated to the BookKeeper pod.

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
        <td><b><a href="#pulsarclusterspecbookkeeperserviceadditionalportsindex">additionalPorts</a></b></td>
        <td>[]object</td>
        <td>
          Additional ports for the BookKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Additional annotations to add to the BookKeeper Service resources.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.bookkeeper.service.additionalPorts[index]
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeeperservice)</sup></sup>





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


### PulsarCluster.spec.bookkeeper.updateStrategy
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeeper)</sup></sup>



Update strategy for the BookKeeper pod/s. Default value is rolling update.

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
        <td><b><a href="#pulsarclusterspecbookkeeperupdatestrategyrollingupdate">rollingUpdate</a></b></td>
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


### PulsarCluster.spec.bookkeeper.updateStrategy.rollingUpdate
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeeperupdatestrategy)</sup></sup>





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


### PulsarCluster.spec.bookkeeper.volumes
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeeper)</sup></sup>



Volumes configuration.

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
        <td><b><a href="#pulsarclusterspecbookkeepervolumesjournal">journal</a></b></td>
        <td>object</td>
        <td>
          Indicates the volume config for the journal.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbookkeepervolumesledgers">ledgers</a></b></td>
        <td>object</td>
        <td>
          Indicates the volume config for the ledgers.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.bookkeeper.volumes.journal
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeepervolumes)</sup></sup>



Indicates the volume config for the journal.

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
        <td><b><a href="#pulsarclusterspecbookkeepervolumesjournalstorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.bookkeeper.volumes.journal.storageClass
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeepervolumesjournal)</sup></sup>



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


### PulsarCluster.spec.bookkeeper.volumes.ledgers
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeepervolumes)</sup></sup>



Indicates the volume config for the ledgers.

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
        <td><b><a href="#pulsarclusterspecbookkeepervolumesledgersstorageclass">storageClass</a></b></td>
        <td>object</td>
        <td>
          Indicates if a StorageClass is used. The operator will create the StorageClass if needed.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.bookkeeper.volumes.ledgers.storageClass
<sup><sup>[↩ Parent](#pulsarclusterspecbookkeepervolumesledgers)</sup></sup>



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


### PulsarCluster.spec.broker
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
          Annotations to add to each Broker resource.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbrokerautoscaler">autoscaler</a></b></td>
        <td>object</td>
        <td>
          Autoscaling config.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>functionsWorkerEnabled</b></td>
        <td>boolean</td>
        <td>
          Enable functions worker embedded in the broker.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>gracePeriod</b></td>
        <td>integer</td>
        <td>
          Termination grace period in seconds for the Broker pod. Default value is 60.<br/>
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
        <td><b><a href="#pulsarclusterspecbrokerinitcontainer">initContainer</a></b></td>
        <td>object</td>
        <td>
          Additional init container.<br/>
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
        <td><b><a href="#pulsarclusterspecbrokerpdb">pdb</a></b></td>
        <td>object</td>
        <td>
          Pod disruption budget configuration for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>podManagementPolicy</b></td>
        <td>string</td>
        <td>
          Pod management policy for the Broker pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbrokerprobe">probe</a></b></td>
        <td>object</td>
        <td>
          Liveness and readiness probe values.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>replicas</b></td>
        <td>integer</td>
        <td>
          Replicas of this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbrokerresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the Broker pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbrokerservice">service</a></b></td>
        <td>object</td>
        <td>
          Configurations for the Service resources associated to the Broker pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>serviceAccountName</b></td>
        <td>string</td>
        <td>
          Service account name for the Broker StatefulSet.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbrokertransactions">transactions</a></b></td>
        <td>object</td>
        <td>
          Enable transactions in the broker.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbrokerupdatestrategy">updateStrategy</a></b></td>
        <td>object</td>
        <td>
          Update strategy for the Broker pod/s. <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>webSocketServiceEnabled</b></td>
        <td>boolean</td>
        <td>
          Enable websocket service in the broker.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.broker.autoscaler
<sup><sup>[↩ Parent](#pulsarclusterspecbroker)</sup></sup>



Autoscaling config.

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
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>higherCpuThreshold</b></td>
        <td>number</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 0<br/>
            <i>Maximum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>lowerCpuThreshold</b></td>
        <td>number</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 0<br/>
            <i>Maximum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>max</b></td>
        <td>integer</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>min</b></td>
        <td>integer</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>periodMs</b></td>
        <td>integer</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 1000<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>scaleDownBy</b></td>
        <td>integer</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>scaleUpBy</b></td>
        <td>integer</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>stabilizationWindowMs</b></td>
        <td>integer</td>
        <td>
          <br/>
          <br/>
            <i>Minimum</i>: 1<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.broker.initContainer
<sup><sup>[↩ Parent](#pulsarclusterspecbroker)</sup></sup>



Additional init container.

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
        <td><b>args</b></td>
        <td>[]string</td>
        <td>
          The command args used for the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>command</b></td>
        <td>[]string</td>
        <td>
          The command used for the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>emptyDirPath</b></td>
        <td>string</td>
        <td>
          The container path where the emptyDir volume is mounted.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          The image used to run the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          The image pull policy used for the container.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.broker.pdb
<sup><sup>[↩ Parent](#pulsarclusterspecbroker)</sup></sup>



Pod disruption budget configuration for this component.

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


### PulsarCluster.spec.broker.probe
<sup><sup>[↩ Parent](#pulsarclusterspecbroker)</sup></sup>



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


### PulsarCluster.spec.broker.resources
<sup><sup>[↩ Parent](#pulsarclusterspecbroker)</sup></sup>



Resource requirements for the Broker pod.

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


### PulsarCluster.spec.broker.service
<sup><sup>[↩ Parent](#pulsarclusterspecbroker)</sup></sup>



Configurations for the Service resources associated to the Broker pod.

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
        <td><b><a href="#pulsarclusterspecbrokerserviceadditionalportsindex">additionalPorts</a></b></td>
        <td>[]object</td>
        <td>
          Additional ports for the Broker Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Additional annotations to add to the Broker Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>headless</b></td>
        <td>boolean</td>
        <td>
          Do not assign an IP to the Service.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Service type. Default value is 'ClusterIP'<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.broker.service.additionalPorts[index]
<sup><sup>[↩ Parent](#pulsarclusterspecbrokerservice)</sup></sup>





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


### PulsarCluster.spec.broker.transactions
<sup><sup>[↩ Parent](#pulsarclusterspecbroker)</sup></sup>



Enable transactions in the broker.

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
          Enable the transaction coordinator in the broker.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecbrokertransactionsinitjob">initJob</a></b></td>
        <td>object</td>
        <td>
          Initialization job configuration.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>partitions</b></td>
        <td>integer</td>
        <td>
          Partitions count for the transaction's topic.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.broker.transactions.initJob
<sup><sup>[↩ Parent](#pulsarclusterspecbrokertransactions)</sup></sup>



Initialization job configuration.

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
        <td><b><a href="#pulsarclusterspecbrokertransactionsinitjobresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the Job's Pod.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.broker.transactions.initJob.resources
<sup><sup>[↩ Parent](#pulsarclusterspecbrokertransactionsinitjob)</sup></sup>



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


### PulsarCluster.spec.broker.updateStrategy
<sup><sup>[↩ Parent](#pulsarclusterspecbroker)</sup></sup>



Update strategy for the Broker pod/s. 

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
        <td><b><a href="#pulsarclusterspecbrokerupdatestrategyrollingupdate">rollingUpdate</a></b></td>
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


### PulsarCluster.spec.broker.updateStrategy.rollingUpdate
<sup><sup>[↩ Parent](#pulsarclusterspecbrokerupdatestrategy)</sup></sup>





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
        <td><b><a href="#pulsarclusterspecglobalcomponents">components</a></b></td>
        <td>object</td>
        <td>
          Pulsar cluster components names.<br/>
        </td>
        <td>false</td>
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


### PulsarCluster.spec.global.components
<sup><sup>[↩ Parent](#pulsarclusterspecglobal)</sup></sup>



Pulsar cluster components names.

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
        <td><b>bookkeeperBaseName</b></td>
        <td>string</td>
        <td>
          BookKeeper base name. Default value is 'bookkeeper'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>brokerBaseName</b></td>
        <td>string</td>
        <td>
          Broker base name. Default value is 'broker'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>proxyBaseName</b></td>
        <td>string</td>
        <td>
          Proxy base name. Default value is 'proxy'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>zookeeperBaseName</b></td>
        <td>string</td>
        <td>
          Zookeeper base name. Default value is 'zookeeper'.<br/>
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
        <td><b><a href="#pulsarclusterspecglobaltlsbookkeeper">bookkeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the BookKeeper component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecglobaltlsbroker">broker</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the broker component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>defaultSecretName</b></td>
        <td>string</td>
        <td>
          Default secret name.<br/>
        </td>
        <td>false</td>
      </tr><tr>
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


### PulsarCluster.spec.global.tls.bookkeeper
<sup><sup>[↩ Parent](#pulsarclusterspecglobaltls)</sup></sup>



TLS configurations related to the BookKeeper component.

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


### PulsarCluster.spec.global.tls.broker
<sup><sup>[↩ Parent](#pulsarclusterspecglobaltls)</sup></sup>



TLS configurations related to the broker component.

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


### PulsarCluster.spec.proxy
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
          Annotations to add to each Broker resource.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>gracePeriod</b></td>
        <td>integer</td>
        <td>
          Termination grace period in seconds for the Broker pod. Default value is 60.<br/>
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
        <td><b><a href="#pulsarclusterspecproxyinitcontainer">initContainer</a></b></td>
        <td>object</td>
        <td>
          Additional init container.<br/>
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
        <td><b><a href="#pulsarclusterspecproxypdb">pdb</a></b></td>
        <td>object</td>
        <td>
          Pod disruption budget configuration for this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecproxyprobe">probe</a></b></td>
        <td>object</td>
        <td>
          Liveness and readiness probe values.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>replicas</b></td>
        <td>integer</td>
        <td>
          Replicas of this component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecproxyresources">resources</a></b></td>
        <td>object</td>
        <td>
          Resource requirements for the Broker pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecproxyservice">service</a></b></td>
        <td>object</td>
        <td>
          Configurations for the Service resources associated to the Broker pod.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#pulsarclusterspecproxyupdatestrategy">updateStrategy</a></b></td>
        <td>object</td>
        <td>
          Update strategy for the Broker pod/s. <br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.proxy.initContainer
<sup><sup>[↩ Parent](#pulsarclusterspecproxy)</sup></sup>



Additional init container.

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
        <td><b>args</b></td>
        <td>[]string</td>
        <td>
          The command args used for the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>command</b></td>
        <td>[]string</td>
        <td>
          The command used for the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>emptyDirPath</b></td>
        <td>string</td>
        <td>
          The container path where the emptyDir volume is mounted.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>image</b></td>
        <td>string</td>
        <td>
          The image used to run the container.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>imagePullPolicy</b></td>
        <td>string</td>
        <td>
          The image pull policy used for the container.<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.proxy.pdb
<sup><sup>[↩ Parent](#pulsarclusterspecproxy)</sup></sup>



Pod disruption budget configuration for this component.

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


### PulsarCluster.spec.proxy.probe
<sup><sup>[↩ Parent](#pulsarclusterspecproxy)</sup></sup>



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


### PulsarCluster.spec.proxy.resources
<sup><sup>[↩ Parent](#pulsarclusterspecproxy)</sup></sup>



Resource requirements for the Broker pod.

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


### PulsarCluster.spec.proxy.service
<sup><sup>[↩ Parent](#pulsarclusterspecproxy)</sup></sup>



Configurations for the Service resources associated to the Broker pod.

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
        <td><b><a href="#pulsarclusterspecproxyserviceadditionalportsindex">additionalPorts</a></b></td>
        <td>[]object</td>
        <td>
          Additional ports for the Broker Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>annotations</b></td>
        <td>map[string]string</td>
        <td>
          Additional annotations to add to the Broker Service resources.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>enablePlainTextWithTLS</b></td>
        <td>boolean</td>
        <td>
          Enable plain text connections even if TLS is enabled.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>loadBalancerIP</b></td>
        <td>string</td>
        <td>
          Assign a load balancer IP.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>type</b></td>
        <td>string</td>
        <td>
          Service type. Default value is 'ClusterIP'<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>


### PulsarCluster.spec.proxy.service.additionalPorts[index]
<sup><sup>[↩ Parent](#pulsarclusterspecproxyservice)</sup></sup>





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


### PulsarCluster.spec.proxy.updateStrategy
<sup><sup>[↩ Parent](#pulsarclusterspecproxy)</sup></sup>



Update strategy for the Broker pod/s. 

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
        <td><b><a href="#pulsarclusterspecproxyupdatestrategyrollingupdate">rollingUpdate</a></b></td>
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


### PulsarCluster.spec.proxy.updateStrategy.rollingUpdate
<sup><sup>[↩ Parent](#pulsarclusterspecproxyupdatestrategy)</sup></sup>





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
        <td><b>maxSurge</b></td>
        <td>int or string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>maxUnavailable</b></td>
        <td>int or string</td>
        <td>
          <br/>
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
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to this component.<br/>
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
          Pod disruption budget configuration for this component.<br/>
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
          Replicas of this component.<br/>
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



Pod disruption budget configuration for this component.

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
        <td><b>message</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>ready</b></td>
        <td>boolean</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reason</b></td>
        <td>enum</td>
        <td>
          <br/>
          <br/>
            <i>Enum</i>: ErrorUpgrading, ErrorConfig<br/>
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
        <td><b><a href="#zookeeperspecglobalcomponents">components</a></b></td>
        <td>object</td>
        <td>
          Pulsar cluster components names.<br/>
        </td>
        <td>false</td>
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


### ZooKeeper.spec.global.components
<sup><sup>[↩ Parent](#zookeeperspecglobal)</sup></sup>



Pulsar cluster components names.

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
        <td><b>bookkeeperBaseName</b></td>
        <td>string</td>
        <td>
          BookKeeper base name. Default value is 'bookkeeper'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>brokerBaseName</b></td>
        <td>string</td>
        <td>
          Broker base name. Default value is 'broker'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>proxyBaseName</b></td>
        <td>string</td>
        <td>
          Proxy base name. Default value is 'proxy'.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>zookeeperBaseName</b></td>
        <td>string</td>
        <td>
          Zookeeper base name. Default value is 'zookeeper'.<br/>
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
        <td><b><a href="#zookeeperspecglobaltlsbookkeeper">bookkeeper</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the BookKeeper component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b><a href="#zookeeperspecglobaltlsbroker">broker</a></b></td>
        <td>object</td>
        <td>
          TLS configurations related to the broker component.<br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>defaultSecretName</b></td>
        <td>string</td>
        <td>
          Default secret name.<br/>
        </td>
        <td>false</td>
      </tr><tr>
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


### ZooKeeper.spec.global.tls.bookkeeper
<sup><sup>[↩ Parent](#zookeeperspecglobaltls)</sup></sup>



TLS configurations related to the BookKeeper component.

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


### ZooKeeper.spec.global.tls.broker
<sup><sup>[↩ Parent](#zookeeperspecglobaltls)</sup></sup>



TLS configurations related to the broker component.

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
        <td><b>config</b></td>
        <td>map[string]string</td>
        <td>
          Configuration entries directly passed to this component.<br/>
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
          Pod disruption budget configuration for this component.<br/>
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
          Replicas of this component.<br/>
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



Pod disruption budget configuration for this component.

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
        <td><b>message</b></td>
        <td>string</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>ready</b></td>
        <td>boolean</td>
        <td>
          <br/>
        </td>
        <td>false</td>
      </tr><tr>
        <td><b>reason</b></td>
        <td>enum</td>
        <td>
          <br/>
          <br/>
            <i>Enum</i>: ErrorUpgrading, ErrorConfig<br/>
        </td>
        <td>false</td>
      </tr></tbody>
</table>