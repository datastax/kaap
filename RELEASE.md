# Release a new version

The project contains 3 different and independents artifacts:
1. Pulsar Operator docker image
2. Pulsar Stack Helm Chart
3. Pulsar Operator Helm Chart

The releases process are independent.


## Pulsar Operator docker image

Run the following command:

```bash
./release/release.sh operator-image <version>
```

This will create a new tag `operator-<version>`, bump the pom versions and push the image to the docker registry.



## Pulsar Stack Helm Chart

Run the following command:

```bash
./release/release.sh pulsar-stack <version>
```

This will create a new tag `pulsar-stack-<version>`, bump the chart version, creates a new Github release with the chart tarball and update the docs.


## Pulsar Operator Helm Chart

Run the following command:

```bash
./release/release.sh pulsar-operator <version>
```

This will create a new tag `pulsar-operator-<version>`, bump the chart version, creates a new Github release with the chart tarball and update the docs.