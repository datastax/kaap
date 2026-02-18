# Release a new version

The project contains 3 different and independents artifacts:
1. Operator image (datastax/kaap:latest)
2. KAAP Chart
3. KAAP Stack Chart

The releases process are independent. You can also do the release on a different branch and merge to main.
(**PR based**).
For reference, check [here.](https://github.com/datastax/kaap/pull/212)

## Release Order Guidance
This is not required to be followed if you are just releasing a new version for only one project.

### operator -> kaap → kaap-stack
To ensure `kaap-stack` is packaged with the latest `kaap` version and latest `operator` version.
This applies **only when a new `kaap` or `operator` version is part of the release cycle**.

This is also because `kaap-stack` uses a local dependency:
```yaml
repository: file://../kaap
```

A release is considered completed only when the PR is merged if we are doing a PR based approach.

## Operator image (datastax/kaap:latest)

Run the following command:

```bash
./release/release.sh operator <version>
```

This will create a new tag `operator-<version>`, bump the pom versions and push the image to the docker registry.



## KAAP Chart

Run the following command:

```bash
./release/release.sh kaap-chart <version>
```

This will create a new tag `kaap-<version>`, bump the chart version, creates a new Github release with the chart tarball and update the docs.

**NOTE:** You have to **manually update the default operator version** in the values.yaml of KAAP if you need to change it.
For reference, check [here.](https://github.com/datastax/kaap/pull/213/commits/ebc1bb38edeaf2164d6f2d7c1729ebfe8ed94db1)

## KAAP Stack Chart

Run the following command:

```bash
./release/release.sh kaap-stack-chart <version>
```

This will create a new tag `kaap-stack-<version>`, bump the chart version, creates a new Github release with the chart tarball and update the docs.

**NOTE:** You have to **manually update the kaap version** if there is a major version change of KAAP in the KAAP-STACK 
chart.yaml. Minor version changes get picked up because of the regex 0.4.x.
For reference, check [here.](https://github.com/datastax/kaap/pull/213/commits/853d3135e5eb6bd25d373e5506e14640083b2c04)