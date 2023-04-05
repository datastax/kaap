/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.pulsaroperator.tests.helm;

import com.datastax.oss.pulsaroperator.crds.cluster.PulsarCluster;
import com.datastax.oss.pulsaroperator.crds.cluster.PulsarClusterSpec;
import com.datastax.oss.pulsaroperator.crds.configs.KafkaConfig;
import com.datastax.oss.pulsaroperator.crds.configs.tls.TlsConfig;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "helm")
public class KafkaTlsTest extends BaseHelmTest {

    @Test
    public void test() throws Exception {
        try {
            applyCertManagerCRDs();
            helmInstall(Chart.STACK, """
                    pulsar-operator:
                        operator:
                            image: %s
                            imagePullPolicy: Never
                    cert-manager:
                      enabled: true
                      global:
                        leaderElection:
                            namespace: %s
                    """.formatted(OPERATOR_IMAGE, namespace));
            awaitOperatorRunning();

            final PulsarClusterSpec specs = getDefaultPulsarClusterSpecs();
            specs.getGlobal()
                    .setTls(TlsConfig.builder()
                            .enabled(true)
                            .certProvisioner(TlsConfig.CertProvisionerConfig.builder()
                                    .selfSigned(TlsConfig.SelfSignedCertProvisionerConfig.builder()
                                            .enabled(true)
                                            .build())
                                    .build())
                            .broker(TlsConfig.TlsEntryConfig.builder()
                                    .enabled(true)
                                    .build())
                            .proxy(TlsConfig.ProxyTlsEntryConfig.proxyBuilder()
                                    .enabled(true)
                                    .enabledWithBroker(true)
                                    .build())
                            .build());

            specs.getFunctionsWorker().setReplicas(0);
            specs.getProxy().setKafka(KafkaConfig.builder()
                    .enabled(true)
                    .build());
            specs.getBroker().setKafka(KafkaConfig.builder()
                    .enabled(true)
                    .build());
            applyPulsarCluster(specsToYaml(specs));
            awaitInstalled();


            applyManifest("""
                    apiVersion: apps/v1
                    kind: Deployment
                    metadata:
                      name: kafka-client-producer
                      labels:
                        role: kafka-client-producer
                    spec:
                      replicas: 1
                      selector:
                        matchLabels:
                          role: kafka-client-producer
                      template:
                        metadata:
                          labels:
                            role: kafka-client-producer
                        spec:
                          volumes:
                            - name: certs
                              secret:
                                secretName: pulsar-tls
                          containers:
                            - name: kclient
                              image: confluentinc/cp-kafka
                              command: ["/bin/sh"]
                              args:
                                - "-c"
                                - >-
                                  keytool -import --trustcacerts -file /pulsar-certs/tls.crt -keystore cert.jks -storepass pulsar -noprompt &&
                                  kafka-producer-perf-test \\
                                    --topic test  \\
                                    --num-records 1000 \\
                                    --record-size 10240 \\
                                    --throughput 1000 \\
                                    --producer-props bootstrap.servers=pulsar-proxy:9093 security.protocol=SSL ssl.truststore.location=cert.jks ssl.truststore.password=pulsar
                              volumeMounts:
                                - mountPath: /pulsar-certs
                                  name: certs
                                  readOnly: true
                    """);

            applyManifest("""
                    apiVersion: batch/v1
                    kind: Job
                    metadata:
                      name: kafka-client-consumer
                      labels:
                        role: kafka-client-consumer
                    spec:
                      template:
                        metadata:
                          labels:
                            role: kafka-client-consumer
                        spec:
                          restartPolicy: OnFailure
                          volumes:
                            - name: certs
                              secret:
                                secretName: pulsar-tls
                          containers:
                            - name: kclient
                              image: confluentinc/cp-kafka
                              command: ["/bin/sh"]
                              args:
                                - "-c"
                                - >-
                                  set -e &&
                                  keytool -import --trustcacerts -file /pulsar-certs/tls.crt -keystore cert.jks -storepass pulsar -noprompt &&
                                  echo "bootstrap.servers=pulsar-proxy:9093
                                  security.protocol=SSL
                                  ssl.truststore.location=cert.jks
                                  ssl.truststore.password=pulsar" > consumer-props.conf &&
                                  kafka-consumer-perf-test \\
                                    --topic test  \\
                                    --consumer.config consumer-props.conf \\
                                    --bootstrap-server pulsar-proxy:9093 \\
                                    --print-metrics \\
                                    --from-latest \\
                                    --messages 1000 \\
                                    --show-detailed-stats \\
                                    --reporting-interval 1000
                              volumeMounts:
                                - mountPath: /pulsar-certs
                                  name: certs
                                  readOnly: true
                    """);

            // we need to also consider the download of the image
            awaitJobCompleted("kafka-client-consumer", 3);
            client.resources(PulsarCluster.class)
                    .inNamespace(namespace)
                    .withName("pulsar-cluster")
                    .delete();
            awaitUninstalled();
        } catch (Throwable t) {
            log.error("test failed with {}", t.getMessage(), t);
            throw new RuntimeException(t);
        }
    }
}
