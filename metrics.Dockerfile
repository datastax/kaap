FROM redhat/ubi9:latest AS builder
ARG VERSION
ARG TARGETPLATFORM

# Install Vector
ENV VECTOR_VERSION=0.45.0
RUN case ${TARGETPLATFORM} in \
         "linux/amd64")  VECTOR_ARCH=x86_64  ;; \
         "linux/arm64")  VECTOR_ARCH=aarch64  ;; \
    esac \
 && rpm -i https://packages.timber.io/vector/${VECTOR_VERSION}/vector-${VECTOR_VERSION}-1.${VECTOR_ARCH}.rpm

FROM redhat/ubi9-micro:latest

ARG VERSION
ARG TARGETPLATFORM

# Copy our configuration
COPY ./conf/vector_config.toml /etc/vector/vector.toml
COPY --from=builder /usr/lib64/libstdc++.so.6 /usr/lib64/libstdc++.so.6
COPY --from=builder /usr/bin/vector /usr/bin/vector

USER 185
ENTRYPOINT ["/usr/bin/vector", "--config", "/etc/vector/vector.toml"]