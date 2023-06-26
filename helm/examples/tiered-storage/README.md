# Setup tiered storage on GCP, AWS, Azure


## GCP 

Create a secret with the service account key file. 
You can download in this way:
1. Go to https://console.cloud.google.com/iam-admin/serviceaccounts
2. Select your project.
3. Create a new service account.
4. Give the service account permission to access the bucket. For example, the "Storage Object Admin" role.
5. Create a key for the service account and save it as a JSON file.

```
json_file_path=gcp-credentials.json
kubectl create secret generic gcp-credentials --from-file=gcp-credentials.json=$json_file_path
```

Fill the placeholder in the PulsarCluster file (helm/examples/tiered-storage/values.yaml):
1. Attach the secret to the broker StatefulSet:
```
broker:
    additionalVolumes:
        volumes:
            - name: gcp-credentials
              secret:
                secretName: gcp-credentials
        mounts:
            - name: gcp-credentials
              mountPath: /pulsar/gcp-credentials
              readOnly: true

```

2. Add the offloader driver configuration:

```
broker:
    config:
        managedLedgerOffloadDriver: "google-cloud-storage"
        gcsManagedLedgerOffloadBucket: <bucket-name>
        gcsManagedLedgerOffloadRegion: <bucket-region>
        gcsManagedLedgerOffloadServiceAccountKeyFile: "/pulsar/gcp-credentials/gcp-credentials.json"
```

Install the K8s Autoscaling for Apache Pulsar and the cluster.
```
helm install k8saap --create-namespace helm/k8saap-stack \
    --values helm/examples/tiered-storage/values.yaml 
```