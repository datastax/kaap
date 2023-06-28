# Release a new version

The project contains 3 different and independents artifacts:
1. K8s Autoscaling for Apache Pulsar docker image
2. Pulsar Stack Helm Chart
3. K8s Autoscaling for Apache Pulsar Helm Chart

The releases process are independent.


## K8s Autoscaling for Apache Pulsar docker image

Run the following command:

```bash
./release/release.sh operator-image <version>
```

This will create a new tag `operator-<version>`, bump the pom versions and push the image to the docker registry.



## Pulsar Stack Helm Chart

Run the following command:

```bash
./release/release.sh kaap-stack <version>
```

This will create a new tag `stack-<version>`, bump the chart version, creates a new Github release with the chart tarball and update the docs.


## K8s Autoscaling for Apache Pulsar Helm Chart

Run the following command:

```bash
./release/release.sh kaap <version>
```

This will create a new tag `kaap-<version>`, bump the chart version, creates a new Github release with the chart tarball and update the docs.