# Keycloak integration

Install Pulsar Stack and a cluster:
```
helm install pstack -n pstack --create-namespace helm/k8saap-stack --values helm/examples/keycloak/values.yaml 
```

Get the client secret from the Keycloak UI:
```
kubectl port-forward statefulset/pstack-keycloak 8080:8080
open localhost:8080
```
Login using the credentials configured in the values.yaml. (`admin` and `adminpassword`).
Go to `Clients` -> `pulsar-admin-example-client` > `Credentials` and generate a secret.

Note that for Pulsar `pulsar-admin-example-client` is considered as the subject `admin` (which is a super user by default).
This is because there's a hard claim in the client mappings.


Enter the bastion shell and request a token for the user `pulsar-admin-example-client`.
```
curl \
    -d 'client_id=pulsar-admin-example-client' \
    -d 'client_secret=JZuCDZBq482dibqRbIZpDIeIPj6Kvh2k' \
    -d 'grant_type=client_credentials' \
    'http://pstack-keycloak/realms/pulsar/protocol/openid-connect/token'
export authParams="token:access_token"
/pulsar/bin/apply-config-from-env.py /pulsar/conf/client.conf
bin/pulsar-admin tenants list
```



