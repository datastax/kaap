# Secure the cluster with TLS and cert-manager (Let's Encrypt)

## Requirements
This example shows how to configure the chart in order to:
- Expose the Proxy as Load Balancer
- Submit SSL Certificate request to Let's Encrypt for your Google CloudDomain domain
- Use the Google CloudDNS Acme client as client for Let's Encrypt
- Deploy ExternalDNS to automatically DNS records when the proxy service gets a new public IP address


## Getting started
### Prepare your environment
Change the file `helm/examples/cert-manager-acme/lets-encrypt-certificate.yaml` and
`helm/examples/cert-manager-acme/values.yaml` replacing:
1. DOMAIN: with your CloudDNS domain.
2. EMAIL: with your email.
3. GCP_PROJECT: with your gcp project id.
4. SECRET_KEY: secret key of the `clouddns-secret` you just created. If you downloaded the file from the GCP UI, it's `<gcp-project>-<service-account>.json`.

### Install the cluster
Install the cluster with TLS enabled in the proxy.
The proxy will need a secret called `pulsar-tls`. 
This secret will be created by `cert-manager`.

```
helm install kaap kaap/kaap-stack \
    --values helm/examples/cert-manager-acme/values.yaml \
    --set cert-manager.global.leaderElection.namespace=default
```

Add a secret with the GCP service account credentials used to communicate with CloudDNS.
The service account must include `DNS Administrator` in their roles.

### Add GCP credentials in the cluster
```
kubectl create secret generic clouddns-secret \
--from-file=$HOME/Downloads/<gcp-project>-<service-account>.json
```

### Apply the Issuer and Certificate 
```
kubectl apply -f helm/examples/cert-manager-acme/lets-encrypt-certificate.yaml
```

At this point a new secret should be created in the namespace named `pulsar-tls`. It might require minutes because there are multiple steps executed by the cert-manager controller.
If the secret has not been created after a while, you can check the cert-manager controller logs.
