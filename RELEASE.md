# Release a new version

The project contains 3 different and independents artifacts:
1. Operator image (datastax/kaap:latest)
2. KAAP Chart
3. KAAP Stack Chart

The releases process are independent.


## Operator image (datastax/kaap:latest)

Run the following command:

```bash
./release/release.sh operator <version>
```

This will create a new tag `operator-<version>`, bump the pom versions and push the image to the docker registry.



## KAAP Chart

Run the following command:

```bash
./release/release.sh kaap <version>
```

This will create a new tag `kaap-<version>`, bump the chart version, creates a new Github release with the chart tarball and update the docs.


## KAAP Stack Chart

Run the following command:

```bash
./release/release.sh kaap-stack <version>
```

This will create a new tag `kaap-stack-<version>`, bump the chart version, creates a new Github release with the chart tarball and update the docs.