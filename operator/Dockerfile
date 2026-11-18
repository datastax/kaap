FROM registry.access.redhat.com/ubi8/openjdk-21:latest

ENV LANGUAGE='en_US:en'

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=185 target/quarkus-app/*.jar /deployments/
COPY --chown=185 target/quarkus-app/app/ /deployments/app/
COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185

ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
# CVE-2026-114938: the AVX-512 VBMI Base64 decode intrinsic can corrupt memory on malformed input.
# Disable it until the base image ships a fixed JDK (21.0.13.1+). JAVA_TOOL_OPTIONS is used because the
# ENTRYPOINT calls java directly, so JAVA_OPTS_APPEND is not read.
ENV JAVA_TOOL_OPTIONS="-XX:-UseBASE64Intrinsics"

WORKDIR /deployments
ENTRYPOINT [ "/bin/java", "-jar", "quarkus-run.jar" ]
