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
package com.datastax.oss.kaap.migrationtool;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.crds.cluster.PulsarCluster;
import com.datastax.oss.kaap.migrationtool.diff.DiffChecker;
import com.datastax.oss.kaap.migrationtool.diff.DiffCollectorOutputWriter;
import com.datastax.oss.kaap.mocks.MockKubernetesClient;
import com.datastax.oss.kaap.mocks.MockResourcesResolver;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PulsarClusterResourceGeneratorTest {


    private static final String NAMESPACE = "ns";
    public static final String CLUSTER_NAME = "pulsar-cluster";
    public static final String CONTEXT = "context";

    @Test(expectedExceptions = IllegalStateException.class)
    @SneakyThrows
    public void testEmpty() {
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE);
        generate(client, Files.createTempDirectory("test"));
    }

    @Test
    public void test() throws Exception {

        final MockResourcesResolver mockResourcesResolver = new MockResourcesResolver();
        TestResourcesLoader.importPathFromClasspath("/pulsar-helm-chart/base-release", mockResourcesResolver);
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, mockResourcesResolver);

        Path tmpDir = Files.createTempDirectory("test");

        final DiffCollectorOutputWriter diff = generate(client, tmpDir);
        final File outputDir = new File(tmpDir.toFile(), CONTEXT);
        assertValue(outputDir);
        assertDiff(diff, 155);
    }

    @Test
    public void testNoFunctions() throws Exception {

        final MockResourcesResolver mockResourcesResolver = new MockResourcesResolver();
        TestResourcesLoader.importPathFromClasspath("/pulsar-helm-chart/base-release",
                mockResourcesResolver, file -> !file.contains("-function"));
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, mockResourcesResolver);

        Path tmpDir = Files.createTempDirectory("test");

        final DiffCollectorOutputWriter diff = generate(client, tmpDir);
        final File outputDir = new File(tmpDir.toFile(), CONTEXT);
        final PulsarCluster pulsar = getPulsarClusterFromOutputdir(outputDir);
        assertDiff(diff, 117);
        Assert.assertEquals(pulsar.getSpec().getFunctionsWorker().getReplicas(), 0);
    }

    @Test
    public void testNoBastion() throws Exception {

        final MockResourcesResolver mockResourcesResolver = new MockResourcesResolver();
        TestResourcesLoader.importPathFromClasspath("/pulsar-helm-chart/base-release",
                mockResourcesResolver, file -> !file.contains("-bastion"));
        MockKubernetesClient client = new MockKubernetesClient(NAMESPACE, mockResourcesResolver);

        Path tmpDir = Files.createTempDirectory("test");

        final DiffCollectorOutputWriter diff = generate(client, tmpDir);
        final File outputDir = new File(tmpDir.toFile(), CONTEXT);
        final PulsarCluster pulsar = getPulsarClusterFromOutputdir(outputDir);
        assertDiff(diff, 148);
        Assert.assertEquals(pulsar.getSpec().getBastion().getReplicas(), 0);
    }

    private void assertDiff(DiffCollectorOutputWriter diff, int expected) throws IOException {
        var diffs = diff.getAll();
        diffs.entrySet().forEach(System.out::println);
        Assert.assertEquals(diffs.values().stream().flatMap(Collection::stream).count(), expected);
    }

    @SneakyThrows
    private void assertValue(File tmpDir) {
        final PulsarCluster pulsarCluster = getPulsarClusterFromOutputdir(tmpDir);
        Assert.assertEquals(
                SerializationUtil.writeAsYaml(pulsarCluster),
                """
                        ---
                        apiVersion: kaap.oss.datastax.com/v1beta1
                        kind: PulsarCluster
                        metadata:
                          name: pulsar-cluster
                        spec:
                          global:
                            name: pulsar-cluster
                            clusterName: pulsar-cluster
                            components:
                              zookeeperBaseName: zookeeper
                              bookkeeperBaseName: bookkeeper
                              brokerBaseName: broker
                              proxyBaseName: proxy
                              autorecoveryBaseName: autorecovery
                              bastionBaseName: bastion
                              functionsWorkerBaseName: function
                            dnsConfig:
                              options:
                              - name: ndots
                                value: 4
                            kubernetesClusterDomain: cluster.local
                            tls:
                              enabled: true
                              defaultSecretName: pulsar-tls
                              caPath: /etc/ssl/certs/ca-certificates.crt
                              zookeeper:
                                enabled: false
                              bookkeeper:
                                enabled: false
                              broker:
                                enabled: true
                                secretName: pulsar-tls
                              brokerResourceSets: {}
                              proxy:
                                enabled: true
                                secretName: pulsar-tls
                                enabledWithBroker: false
                              proxyResourceSets: {}
                              autorecovery:
                                enabled: false
                              ssCa:
                                enabled: true
                                secretName: pulsar-tls
                            persistence: true
                            restartOnConfigMapChange: true
                            auth:
                              enabled: true
                              token:
                                publicKeyFile: pulsar-public.key
                                privateKeyFile: my-private.key
                                superUserRoles:
                                - admin
                                - create-tenant
                                - superuser-backup
                                proxyRoles:
                                - create-tenant
                                initialize: false
                            imagePullPolicy: IfNotPresent
                            storage:
                              existingStorageClassName: default
                            antiAffinity:
                              host:
                                enabled: true
                                required: true
                              zone:
                                enabled: false
                            zookeeperPlainSslStorePassword: false
                          zookeeper:
                            image: pulsar:latest
                            imagePullPolicy: IfNotPresent
                            nodeSelectors: {}
                            replicas: 3
                            pdb:
                              enabled: true
                              maxUnavailable: 1
                            tolerations: []
                            antiAffinity:
                              host:
                                enabled: true
                                required: true
                              zone:
                                enabled: false
                            annotations: {}
                            podAnnotations:
                              prometheus.io/port: 8000
                              prometheus.io/scrape: "true"
                            labels:
                              app: pulsar
                              app.kubernetes.io/managed-by: Helm
                              chart: pulsar-1.0.32
                              cluster: pulsar-cluster
                              component: zookeeper
                              heritage: Helm
                              release: pulsar-cluster
                            skipVolumeClaimLabels: true
                            podLabels:
                              app: pulsar
                              cluster: pulsar-cluster
                              component: zookeeper
                              release: pulsar-cluster
                            matchLabels:
                              app: pulsar
                              cluster: ""
                              component: zookeeper
                              release: pulsar-cluster
                            imagePullSecrets: []
                            env: []
                            sidecars: []
                            initContainers: []
                            config:
                              PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                              PULSAR_GC: -XX:+UseG1GC -XX:MaxGCPauseMillis=10
                              PULSAR_LOG_LEVEL: info
                              PULSAR_LOG_ROOT_LEVEL: info
                              PULSAR_MEM: -Xms1000m -Xmx1000m -Dcom.sun.management.jmxremote -Djute.maxbuffer=10485760 -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions -XX:+AggressiveOpts -XX:+DoEscapeAnalysis -XX:+DisableExplicitGC -XX:+PerfDisableSharedMem -Dzookeeper.forceSync=yes
                              quorumListenOnAllIPs: "true"
                              snapCount: 10000
                            probes:
                              readiness:
                                enabled: true
                                timeoutSeconds: 30
                                initialDelaySeconds: 10
                                periodSeconds: 10
                                failureThreshold: 3
                                successThreshold: 1
                              liveness:
                                enabled: true
                                timeoutSeconds: 30
                                initialDelaySeconds: 10
                                periodSeconds: 10
                                failureThreshold: 3
                                successThreshold: 1
                            podManagementPolicy: OrderedReady
                            updateStrategy:
                              type: RollingUpdate
                            gracePeriod: 60
                            resources:
                              requests:
                                cpu: 1
                                memory: 1Gi
                            dataVolume:
                              name: data
                              size: 32Gi
                              existingStorageClassName: pulsar-cluster-zookeeper-data
                            service:
                              annotations:
                                publishNotReadyAddresses: "true"
                              additionalPorts: []
                            metadataInitializationJob:
                              timeout: 60
                          bookkeeper:
                            image: pulsar:latest
                            imagePullPolicy: IfNotPresent
                            nodeSelectors: {}
                            replicas: 3
                            pdb:
                              enabled: true
                              maxUnavailable: 1
                            tolerations: []
                            antiAffinity:
                              host:
                                enabled: true
                                required: false
                              zone:
                                enabled: false
                            annotations: {}
                            podAnnotations:
                              prometheus.io/port: 8000
                              prometheus.io/scrape: "true"
                            labels:
                              app: pulsar
                              app.kubernetes.io/managed-by: Helm
                              chart: pulsar-1.0.32
                              cluster: pulsar-cluster
                              component: bookkeeper
                              heritage: Helm
                              release: pulsar-cluster
                            skipVolumeClaimLabels: true
                            podLabels:
                              app: pulsar
                              cluster: pulsar-cluster
                              component: bookkeeper
                              release: pulsar-cluster
                            matchLabels:
                              app: pulsar
                              cluster: ""
                              component: bookkeeper
                              release: pulsar-cluster
                            imagePullSecrets: []
                            env: []
                            sidecars: []
                            initContainers:
                            - args:
                              - |-
                                until bin/pulsar zookeeper-shell -server pulsar-cluster-zookeeper ls /admin/clusters | grep "^\\[.*pulsar-cluster.*\\]"; do
                                  sleep 3;
                                done;
                              command:
                              - sh
                              - -c
                              image: pulsar:latest
                              imagePullPolicy: IfNotPresent
                              name: wait-zookeeper-ready
                              resources: {}
                              terminationMessagePath: /dev/termination-log
                              terminationMessagePolicy: File
                            - args:
                              - |
                                bin/apply-config-from-env.py conf/bookkeeper.conf && bin/apply-config-from-env.py conf/bkenv.sh && bin/bookkeeper shell metaformat --nonInteractive || true;
                              command:
                              - sh
                              - -c
                              envFrom:
                              - configMapRef:
                                  name: pulsar-cluster-bookkeeper
                              image: pulsar:latest
                              imagePullPolicy: IfNotPresent
                              name: pulsar-bookkeeper-metaformat
                              resources: {}
                              terminationMessagePath: /dev/termination-log
                              terminationMessagePolicy: File
                            config:
                              BOOKIE_GC: -XX:+UseG1GC -XX:MaxGCPauseMillis=10
                              BOOKIE_MEM: -Xms4g -Xmx4g -XX:MaxDirectMemorySize=4g -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler.linkCapacity=1024 -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions -XX:+AggressiveOpts -XX:+DoEscapeAnalysis -XX:ParallelGCThreads=32 -XX:ConcGCThreads=32 -XX:G1NewSizePercent=50 -XX:+DisableExplicitGC -XX:-ResizePLAB -XX:+ExitOnOutOfMemoryError -XX:+PerfDisableSharedMem
                              PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                              PULSAR_LOG_LEVEL: info
                              PULSAR_LOG_ROOT_LEVEL: info
                              autoRecoveryDaemonEnabled: "false"
                              compactionRateByEntries: 5000
                              dbStorage_readAheadCacheBatchSize: 2000
                              dbStorage_readAheadCacheMaxSizeMb: 1024
                              dbStorage_rocksDB_blockCacheSize: 500000000
                              dbStorage_writeCacheMaxSizeMb: 1024
                              gcWaitTime: 300000
                              httpServerEnabled: "true"
                              journalAdaptiveGroupWrites: "true"
                              journalBufferedWritesThreshold: 1024
                              journalMaxGroupWaitMSec: 1
                              journalMaxSizeMB: 2048
                              journalPreAllocSizeMB: 128
                              journalSyncData: "false"
                              journalWriteBufferSizeKB: 4096
                              majorCompactionInterval: 1200
                              maxPendingAddRequestsPerThread: 500000
                              maxPendingReadRequestsPerThread: 500000
                              minorCompactionInterval: 600
                              numAddWorkerThreads: 8
                              numJournalCallbackThreads: 64
                              numReadWorkerThreads: 8
                              statsProviderClass: org.apache.bookkeeper.stats.prometheus.PrometheusMetricsProvider
                              useHostNameAsBookieID: "true"
                              writeBufferSizeBytes: 4194304
                              zkServers: pulsar-cluster-zookeeper-ca:2181
                            probes:
                              readiness:
                                enabled: true
                                timeoutSeconds: 1
                                initialDelaySeconds: 10
                                periodSeconds: 30
                                failureThreshold: 3
                                successThreshold: 1
                              liveness:
                                enabled: true
                                timeoutSeconds: 1
                                initialDelaySeconds: 10
                                periodSeconds: 30
                                failureThreshold: 3
                                successThreshold: 1
                            updateStrategy:
                              type: RollingUpdate
                            podManagementPolicy: OrderedReady
                            gracePeriod: 60
                            resources:
                              requests:
                                cpu: 2
                                memory: 8Gi
                            volumes:
                              journal:
                                name: journal
                                size: 100Gi
                                existingStorageClassName: pulsar-cluster-bookkeeper-journal
                              ledgers:
                                name: ledgers
                                size: 52Gi
                                existingStorageClassName: pulsar-cluster-bookkeeper-ledgers
                            service:
                              annotations:
                                meta.helm.sh/release-name: pulsar-cluster
                                meta.helm.sh/release-namespace: pulsar
                                publishNotReadyAddresses: "true"
                                service.alpha.kubernetes.io/tolerate-unready-endpoints: "true"
                              additionalPorts: []
                            autoscaler:
                              enabled: false
                              periodMs: 10000
                              diskUsageToleranceHwm: 0.92
                              diskUsageToleranceLwm: 0.75
                              minWritableBookies: 3
                              scaleUpBy: 1
                              scaleUpMaxLimit: 30
                              scaleDownBy: 1
                              stabilizationWindowMs: 300000
                            cleanUpPvcs: true
                            setsUpdateStrategy: RollingUpdate
                            autoRackConfig:
                              enabled: false
                              periodMs: 60000
                          broker:
                            image: pulsar:latest
                            imagePullPolicy: IfNotPresent
                            nodeSelectors: {}
                            replicas: 3
                            pdb:
                              enabled: true
                              maxUnavailable: 1
                            tolerations: []
                            antiAffinity:
                              host:
                                enabled: true
                                required: false
                              zone:
                                enabled: false
                            annotations: {}
                            podAnnotations:
                              prometheus.io/path: /metrics/
                              prometheus.io/port: 8080
                              prometheus.io/scrape: "true"
                            labels:
                              app: pulsar
                              app.kubernetes.io/managed-by: Helm
                              chart: pulsar-1.0.32
                              cluster: pulsar-cluster
                              component: broker
                              heritage: Helm
                              release: pulsar-cluster
                            podLabels:
                              app: pulsar
                              cluster: pulsar-cluster
                              component: broker
                              release: pulsar-cluster
                            matchLabels:
                              app: pulsar
                              cluster: ""
                              component: broker
                              release: pulsar-cluster
                            imagePullSecrets: []
                            env:
                            - name: PULSAR_PREFIX_kafkaAdvertisedListeners
                              value: SASL_PLAINTEXT://advertisedAddress:9092
                            - name: kafkaAdvertisedListeners
                              value: SASL_PLAINTEXT://advertisedAddress:9092
                            - name: PULSAR_PREFIX_kafkaListeners
                              value: SASL_PLAINTEXT://0.0.0.0:9092
                            - name: kafkaListeners
                              value: SASL_PLAINTEXT://0.0.0.0:9092
                            - name: managedLedgerDefaultAckQuorum
                              value: 2
                            - name: managedLedgerDefaultEnsembleSize
                              value: 2
                            - name: managedLedgerDefaultWriteQuorum
                              value: 2
                            sidecars: []
                            initContainers:
                            - args:
                              - |-
                                until nslookup pulsar-cluster-bookkeeper-2.pulsar-cluster-bookkeeper.pulsar; do
                                  sleep 3;
                                done;
                              command:
                              - sh
                              - -c
                              image: pulsar:latest
                              imagePullPolicy: IfNotPresent
                              name: wait-bookkeeper-ready
                              resources: {}
                              terminationMessagePath: /dev/termination-log
                              terminationMessagePolicy: File
                            config:
                              PF_clientAuthenticationParameters: file:///pulsar/token-superuser/superuser.jwt
                              PF_clientAuthenticationPlugin: org.apache.pulsar.client.impl.auth.AuthenticationToken
                              PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                              PULSAR_GC: -XX:+UseG1GC -XX:MaxGCPauseMillis=10
                              PULSAR_LOG_LEVEL: info
                              PULSAR_LOG_ROOT_LEVEL: info
                              PULSAR_MEM: -Xms2g -Xmx2g -XX:MaxDirectMemorySize=2g -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler.linkCapacity=1024 -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions -XX:+AggressiveOpts -XX:+DoEscapeAnalysis -XX:ParallelGCThreads=32 -XX:ConcGCThreads=32 -XX:G1NewSizePercent=50 -XX:+DisableExplicitGC -XX:-ResizePLAB -XX:+ExitOnOutOfMemoryError -XX:+PerfDisableSharedMem
                              acknowledgmentAtBatchIndexLevelEnabled: "true"
                              allowAutoTopicCreation: "true"
                              allowAutoTopicCreationType: non-partitioned
                              authParams: file:///pulsar/token-superuser-stripped.jwt
                              authPlugin: org.apache.pulsar.client.impl.auth.AuthenticationToken
                              authenticationEnabled: "true"
                              authenticationProviders: org.apache.pulsar.broker.authentication.AuthenticationProviderToken
                              authorizationEnabled: "true"
                              backlogQuotaDefaultRetentionPolicy: producer_exception
                              bookkeeperClientRegionawarePolicyEnabled: "false"
                              brokerClientAuthenticationParameters: file:///pulsar/token-superuser/superuser.jwt
                              brokerClientAuthenticationPlugin: org.apache.pulsar.client.impl.auth.AuthenticationToken
                              brokerClientTlsEnabled: "true"
                              brokerClientTrustCertsFilePath: /etc/ssl/certs/ca-certificates.crt
                              brokerDeduplicationEnabled: "false"
                              brokerDeleteInactivePartitionedTopicMetadataEnabled: "true"
                              brokerDeleteInactiveTopicsEnabled: "true"
                              brokerEntryMetadataInterceptors: "org.apache.pulsar.common.intercept.AppendIndexMetadataInterceptor,org.apache.pulsar.common.intercept.AppendBrokerTimestampMetadataInterceptor"
                              brokerPublisherThrottlingTickTimeMillis: 50
                              brokerServicePortTls: 6651
                              clusterName: pulsar-cluster
                              configurationStoreServers: pulsar-cluster-zookeeper-ca:2181
                              dispatcherMaxReadBatchSize: 1000
                              enableBusyWait: "true"
                              exposeConsumerLevelMetricsInPrometheus: "false"
                              exposeTopicLevelMetricsInPrometheus: "true"
                              forceDeleteNamespaceAllowed: "true"
                              forceDeleteTenantAllowed: "true"
                              kafkaAdvertisedListeners: SASL_PLAINTEXT://advertisedAddress:9092
                              kafkaListeners: SASL_PLAINTEXT://0.0.0.0:9092
                              kafkaManageSystemNamespaces: "false"
                              kafkaNamespace: kafka
                              kafkaTransactionCoordinatorEnabled: "true"
                              kafkaTransactionProducerIdsNamespace: __kafka_unlimited
                              kopSchemaRegistryEnable: "true"
                              kopSchemaRegistryNamespace: __kafka_unlimited
                              loadBalancerBrokerOverloadedThresholdPercentage: 95
                              loadBalancerLoadSheddingStrategy: org.apache.pulsar.broker.loadbalance.impl.OverloadShedder
                              managedLedgerCacheEvictionTimeThresholdMillis: 10000
                              managedLedgerCacheSizeMB: 2000
                              managedLedgerDefaultAckQuorum: 2
                              managedLedgerDefaultEnsembleSize: 2
                              managedLedgerDefaultWriteQuorum: 2
                              managedLedgerMaxLedgerRolloverTimeMinutes: 240
                              managedLedgerMaxSizePerLedgerMbytes: 2000
                              managedLedgerMinLedgerRolloverTimeMinutes: 0
                              managedLedgerNumSchedulerThreads: 32
                              managedLedgerNumWorkerThreads: 32
                              managedLedgerOffloadMaxThreads: 4
                              messagingProtocols: kafka
                              protocolHandlerDirectory: ./protocols
                              proxyRoles: create-tenant
                              saslAllowedMechanisms: PLAIN
                              statusFilePath: /pulsar/status
                              subscriptionExpirationTimeMinutes: 20160
                              superUserRoles: "superuser-backup,create-tenant,admin"
                              tlsCertificateFilePath: /pulsar/certs/tls.crt
                              tlsEnabled: "true"
                              tlsKeyFilePath: /pulsar/tls-pk8.key
                              tlsProtocols: "TLSv1.3,TLSv1.2"
                              tlsTrustCertsFilePath: /etc/ssl/certs/ca-certificates.crt
                              tokenPublicKey: file:///pulsar/token-public-key/pulsar-public.key
                              topicFencingTimeoutSeconds: 5
                              topicPublisherThrottlingTickTimeMillis: 10
                              webServicePortTls: 8443
                              zookeeperServers: pulsar-cluster-zookeeper-ca:2181
                              zookeeperSessionExpiredPolicy: shutdown
                            probes:
                              readiness:
                                enabled: true
                                timeoutSeconds: 5
                                initialDelaySeconds: 10
                                periodSeconds: 30
                                failureThreshold: 5
                                successThreshold: 1
                              liveness:
                                enabled: true
                                timeoutSeconds: 5
                                initialDelaySeconds: 10
                                periodSeconds: 30
                                failureThreshold: 5
                                successThreshold: 1
                              useHealthCheckForLiveness: false
                              useHealthCheckForReadiness: true
                            functionsWorkerEnabled: false
                            transactions:
                              enabled: false
                              partitions: 16
                            updateStrategy:
                              rollingUpdate:
                                partition: 0
                              type: RollingUpdate
                            podManagementPolicy: OrderedReady
                            gracePeriod: 60
                            resources:
                              requests:
                                cpu: 1
                                memory: 4Gi
                            service:
                              annotations: {}
                              additionalPorts:
                              - name: kafkaplaintext
                                port: 9092
                                protocol: TCP
                                targetPort: 9092
                              - name: kafkassl
                                port: 9093
                                protocol: TCP
                                targetPort: 9093
                              type: ClusterIP
                            autoscaler:
                              enabled: false
                              periodMs: 60000
                              min: 1
                              lowerCpuThreshold: 0.3
                              higherCpuThreshold: 0.8
                              scaleUpBy: 1
                              scaleDownBy: 1
                              stabilizationWindowMs: 300000
                              resourcesUsageSource: PulsarLBReport
                            kafka:
                              enabled: false
                              exposePorts: true
                            setsUpdateStrategy: RollingUpdate
                          proxy:
                            image: pulsar:latest
                            imagePullPolicy: IfNotPresent
                            nodeSelectors: {}
                            replicas: 1
                            pdb:
                              enabled: true
                              maxUnavailable: 1
                            tolerations: []
                            antiAffinity:
                              host:
                                enabled: true
                                required: false
                              zone:
                                enabled: false
                            annotations: {}
                            podAnnotations:
                              prometheus.io/path: /metrics/
                              prometheus.io/port: 8080
                              prometheus.io/scrape: "true"
                            labels:
                              app: pulsar
                              app.kubernetes.io/managed-by: Helm
                              chart: pulsar-1.0.32
                              cluster: pulsar-cluster
                              component: proxy
                              heritage: Helm
                              release: pulsar-cluster
                            podLabels:
                              app: pulsar
                              cluster: pulsar-cluster
                              component: proxy
                              release: pulsar-cluster
                            matchLabels:
                              app: pulsar
                              cluster: ""
                              component: proxy
                              release: pulsar-cluster
                            imagePullSecrets: []
                            env:
                            - name: PULSAR_PREFIX_brokerProxyAllowedHostNames
                              value: "10.*,192.168.*,pulsar-cluster-broker*"
                            - name: PULSAR_PREFIX_brokerProxyAllowedIPAddresses
                              value: "10.0.0.0/8,192.168.0.0/16"
                            sidecars:
                            - env:
                              - name: PORT
                                value: 8964
                              - name: ClusterName
                                value: pulsar-cluster
                              - name: WebsocketURL
                                value: ws://localhost:8000
                              - name: BrokerProxyURL
                                value: http://pulsar-cluster-broker:8080
                              - name: FunctionProxyURL
                                value: http://pulsar-cluster-function:6750
                              - name: SuperRoles
                                value: "superuser-backup,create-tenant,admin"
                              - name: StreamingAPIKey
                                valueFrom:
                                  secretKeyRef:
                                    key: streaming-api-key.txt
                                    name: api-key
                              - name: PulsarToken
                                valueFrom:
                                  secretKeyRef:
                                    key: superuser.jwt
                                    name: token-superuser
                              - name: HTTPAuthImpl
                              - name: PulsarURL
                                value: pulsar://pulsar-cluster-broker:6650
                              - name: PulsarPublicKey
                                value: /pulsar/token-public-key/pulsar-public.key
                              - name: PulsarPrivateKey
                                value: /pulsar/token-private-key/pulsar-private.key
                              - name: CertFile
                                value: /pulsar/certs/tls.crt
                              - name: KeyFile
                                value: /pulsar/certs/tls.key
                              - name: TrustStore
                                value: /etc/ssl/certs/ca-bundle.crt
                              - name: FEDERATED_PROM_URL
                                value: http://pulsar-cluster-kub-prometheus:9090/federate
                              - name: FederatedPromURL
                                value: http://pulsar-cluster-kub-prometheus:9090/federate
                              - name: TenantsUsageDisabled
                              - name: TenantManagmentTopic
                                value: persistent://public/default/tenant-management
                              - name: LogLevel
                                value: info
                              - name: LogServerPort
                                value: :4040
                              - name: AdminRestPrefix
                                value: /admin/v2
                              - name: FunctionWorkerDomain
                                value: .pulsar-cluster-function.pulsar.svc.cluster.local
                              image: pulsar:latest
                              imagePullPolicy: IfNotPresent
                              name: pulsar-cluster-proxy-burnell
                              ports:
                              - containerPort: 8964
                                name: burnell
                                protocol: TCP
                              - containerPort: 9090
                                name: metrics
                                protocol: TCP
                              resources:
                                requests:
                                  cpu: 100m
                                  memory: 128Mi
                              terminationMessagePath: /dev/termination-log
                              terminationMessagePolicy: File
                              volumeMounts:
                              - mountPath: /pulsar/certs
                                name: certs
                                readOnly: true
                              - mountPath: /pulsar/token-private-key
                                name: token-private-key
                                readOnly: true
                              - mountPath: /pulsar/token-public-key
                                name: token-public-key
                                readOnly: true
                            initContainers:
                            - args:
                              - |-
                                until nslookup pulsar-cluster-bookkeeper-2.pulsar-cluster-bookkeeper.pulsar; do
                                  sleep 3;
                                done;
                              command:
                              - sh
                              - -c
                              image: pulsar:latest
                              imagePullPolicy: IfNotPresent
                              name: wait-bookkeeper-ready
                              resources: {}
                              terminationMessagePath: /dev/termination-log
                              terminationMessagePolicy: File
                            config:
                              PULSAR_EXTRA_CLASSPATH: /jars/pulsar-libs/*
                              PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                              PULSAR_GC: -XX:+UseG1GC
                              PULSAR_LOG_LEVEL: info
                              PULSAR_LOG_ROOT_LEVEL: info
                              PULSAR_MEM: -Xms400m -Xmx400m -XX:MaxDirectMemorySize=400m
                              authenticateMetricsEndpoint: "false"
                              authenticationEnabled: "true"
                              authenticationProviders: org.apache.pulsar.broker.authentication.AuthenticationProviderToken
                              authorizationEnabled: "true"
                              brokerClientAuthenticationParameters: file:///pulsar/token-proxy/proxy.jwt
                              brokerClientAuthenticationPlugin: org.apache.pulsar.client.impl.auth.AuthenticationToken
                              brokerClientTrustCertsFilePath: /etc/ssl/certs/ca-certificates.crt
                              brokerServicePortTls: 6651
                              brokerServiceURL: pulsar://pulsar-cluster-broker:6650
                              brokerServiceURLTLS: pulsar+ssl://pulsar-cluster-broker:6651
                              brokerWebServiceURL: http://pulsar-cluster-broker:8080
                              brokerWebServiceURLTLS: https://pulsar-cluster-broker:8443
                              configurationStoreServers: pulsar-cluster-zookeeper-ca:2181
                              functionWorkerWebServiceURL: http://pulsar-cluster-function-ca:6750
                              numHttpServerThreads: 8
                              servicePortTls: 6651
                              superUserRoles: "superuser-backup,create-tenant,admin"
                              tlsCertificateFilePath: /pulsar/certs/tls.crt
                              tlsEnabledInProxy: "true"
                              tlsEnabledWithBroker: "false"
                              tlsKeyFilePath: /pulsar/tls-pk8.key
                              tlsProtocols: "TLSv1.3,TLSv1.2"
                              tlsTrustCertsFilePath: /etc/ssl/certs/ca-certificates.crt
                              tokenPublicKey: file:///pulsar/token-public-key/pulsar-public.key
                              webServicePortTls: 8443
                              zookeeperServers: pulsar-cluster-zookeeper-ca:2181
                            probes:
                              readiness:
                                enabled: true
                                timeoutSeconds: 2
                                initialDelaySeconds: 10
                                periodSeconds: 30
                                failureThreshold: 3
                                successThreshold: 1
                              liveness:
                                enabled: true
                                timeoutSeconds: 2
                                initialDelaySeconds: 10
                                periodSeconds: 30
                                failureThreshold: 3
                                successThreshold: 1
                            updateStrategy:
                              rollingUpdate:
                                maxSurge: 1
                                maxUnavailable: 0
                              type: RollingUpdate
                            gracePeriod: 60
                            resources:
                              requests:
                                cpu: 100m
                                memory: 400Mi
                            service:
                              annotations:
                                external-dns.alpha.kubernetes.io/hostname: pulsar-gcp-useast4.dev.streaming.datastax.com
                                projectcontour.io/upstream-protocol.tls: "https,8964"
                              additionalPorts:
                              - name: wsstoken
                                nodePort: 30323
                                port: 8500
                                protocol: TCP
                                targetPort: 8500
                              - name: pulsarbeam
                                nodePort: 32041
                                port: 8085
                                protocol: TCP
                                targetPort: 8085
                              - name: burnell
                                nodePort: 30847
                                port: 8964
                                protocol: TCP
                                targetPort: 8964
                              - name: tokenserver
                                nodePort: 32480
                                port: 3000
                                protocol: TCP
                                targetPort: 3000
                              type: LoadBalancer
                              enablePlainTextWithTLS: true
                            webSocket:
                              enabled: true
                              resources:
                                requests:
                                  cpu: 100m
                                  memory: 400Mi
                              config:
                                PULSAR_EXTRA_CLASSPATH: /jars/pulsar-libs/*
                                PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                                PULSAR_GC: -XX:+UseG1GC
                                PULSAR_LOG_LEVEL: info
                                PULSAR_LOG_ROOT_LEVEL: info
                                PULSAR_MEM: -Xms400m -Xmx400m -XX:MaxDirectMemorySize=400m
                                authenticateMetricsEndpoint: "false"
                                authenticationEnabled: "true"
                                authenticationProviders: "org.apache.pulsar.broker.authentication.AuthenticationProviderToken,org.apache.pulsar.broker.authentication.AuthenticationProviderTls"
                                authorizationEnabled: "true"
                                brokerClientAuthenticationParameters: file:///pulsar/token-websocket/websocket.jwt
                                brokerClientAuthenticationPlugin: org.apache.pulsar.client.impl.auth.AuthenticationToken
                                brokerClientTlsEnabled: "true"
                                brokerServiceUrl: pulsar://pulsar-cluster-broker:6650
                                brokerServiceUrlTls: pulsar+ssl://pulsar-cluster-broker:6651
                                clusterName: pulsar-cluster
                                configurationStoreServers: pulsar-cluster-zookeeper-ca:2181
                                numHttpServerThreads: 8
                                serviceUrl: http://pulsar-cluster-broker:8080
                                serviceUrlTls: https://pulsar-cluster-broker:8443
                                superUserRoles: "superuser-backup,create-tenant,admin"
                                tlsCertificateFilePath: /pulsar/certs/tls.crt
                                tlsEnabled: "true"
                                tlsKeyFilePath: /pulsar/tls-pk8.key
                                tlsTrustCertsFilePath: /etc/ssl/certs/ca-certificates.crt
                                tokenPublicKey: file:///pulsar/token-public-key/pulsar-public.key
                                webServicePort: 8000
                                webServicePortTls: 8001
                                zookeeperServers: pulsar-cluster-zookeeper-ca:2181
                              probes:
                                readiness:
                                  enabled: false
                                liveness:
                                  enabled: false
                            kafka:
                              enabled: false
                              exposePorts: true
                            setsUpdateStrategy: RollingUpdate
                          autorecovery:
                            image: pulsar:latest
                            imagePullPolicy: IfNotPresent
                            nodeSelectors: {}
                            config:
                              BOOKIE_GC: -XX:+UseG1GC
                              BOOKIE_MEM: -Xms2000m -Xmx2000m -XX:+ExitOnOutOfMemoryError
                              PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                              PULSAR_LOG_LEVEL: info
                              PULSAR_LOG_ROOT_LEVEL: info
                              zkServers: pulsar-cluster-zookeeper-ca:2181
                            replicas: 1
                            annotations: {}
                            podAnnotations:
                              prometheus.io/port: 8000
                              prometheus.io/scrape: "true"
                            labels:
                              app: pulsar
                              app.kubernetes.io/managed-by: Helm
                              chart: pulsar-1.0.32
                              cluster: pulsar-cluster
                              component: autorecovery
                              heritage: Helm
                              release: pulsar-cluster
                            podLabels:
                              app: pulsar
                              cluster: pulsar-cluster
                              component: autorecovery
                              release: pulsar-cluster
                            matchLabels:
                              app: pulsar
                              cluster: ""
                              component: autorecovery
                              release: pulsar-cluster
                            imagePullSecrets: []
                            gracePeriod: 60
                            resources:
                              requests:
                                cpu: 500m
                                memory: 2000Mi
                            tolerations: []
                            antiAffinity:
                              host:
                                enabled: true
                                required: false
                              zone:
                                enabled: false
                            env: []
                            sidecars: []
                            initContainers: []
                          bastion:
                            image: pulsar:latest
                            imagePullPolicy: IfNotPresent
                            nodeSelectors: {}
                            config:
                              PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                              PULSAR_GC: -XX:+UseG1GC
                              PULSAR_LOG_LEVEL: info
                              PULSAR_LOG_ROOT_LEVEL: info
                              PULSAR_MEM: -XX:+ExitOnOutOfMemoryError
                              authParams: file:///pulsar/token-superuser-stripped.jwt
                              authPlugin: org.apache.pulsar.client.impl.auth.AuthenticationToken
                              tlsTrustCertsFilePath: /etc/ssl/certs/ca-certificates.crt
                            replicas: 1
                            annotations: {}
                            podAnnotations: {}
                            labels:
                              app: pulsar
                              app.kubernetes.io/managed-by: Helm
                              chart: pulsar-1.0.32
                              cluster: pulsar-cluster
                              component: bastion
                              heritage: Helm
                              release: pulsar-cluster
                            podLabels:
                              app: pulsar
                              cluster: pulsar-cluster
                              component: bastion
                              release: pulsar-cluster
                            matchLabels:
                              app: pulsar
                              cluster: ""
                              component: bastion
                              release: pulsar-cluster
                            imagePullSecrets: []
                            gracePeriod: 60
                            resources:
                              requests:
                                cpu: 100m
                                memory: 256Mi
                            tolerations: []
                            antiAffinity:
                              host:
                                enabled: false
                              zone:
                                enabled: false
                            env:
                            - name: webServiceUrl
                              value: https://pulsar-cluster-proxy:8443/
                            - name: brokerServiceUrl
                              value: pulsar://pulsar-cluster-broker:6650/
                            sidecars: []
                            initContainers: []
                          functionsWorker:
                            image: pulsar:latest
                            imagePullPolicy: IfNotPresent
                            nodeSelectors: {}
                            replicas: 1
                            pdb:
                              enabled: true
                              maxUnavailable: 1
                            tolerations: []
                            antiAffinity:
                              host:
                                enabled: true
                                required: true
                              zone:
                                enabled: false
                            annotations: {}
                            podAnnotations:
                              prometheus.io/port: 6750
                              prometheus.io/scrape: "true"
                            labels:
                              app: pulsar
                              app.kubernetes.io/managed-by: Helm
                              chart: pulsar-1.0.32
                              cluster: pulsar-cluster
                              component: function
                              heritage: Helm
                              release: pulsar-cluster
                            skipVolumeClaimLabels: true
                            podLabels:
                              app: pulsar
                              cluster: pulsar-cluster
                              component: function
                              release: pulsar-cluster
                            matchLabels:
                              app: pulsar
                              cluster: ""
                              component: function
                              release: pulsar-cluster
                            imagePullSecrets: []
                            env: []
                            sidecars: []
                            initContainers: []
                            config:
                              PF_authenticateMetricsEndpoint: "false"
                              PULSAR_EXTRA_OPTS: -Dpulsar.log.root.level=info
                              PULSAR_GC: -XX:+UseG1GC -XX:MaxGCPauseMillis=10
                              PULSAR_LOG_LEVEL: info
                              PULSAR_LOG_ROOT_LEVEL: info
                              PULSAR_MEM: -Xms500m -Xmx500m -XX:MaxDirectMemorySize=500m -Dio.netty.leakDetectionLevel=disabled -Dio.netty.recycler.linkCapacity=1024 -XX:+ParallelRefProcEnabled -XX:+UnlockExperimentalVMOptions -XX:+AggressiveOpts -XX:+DoEscapeAnalysis -XX:ParallelGCThreads=32 -XX:ConcGCThreads=32 -XX:G1NewSizePercent=50 -XX:+DisableExplicitGC -XX:-ResizePLAB -XX:+ExitOnOutOfMemoryError -XX:+PerfDisableSharedMem
                              assignmentWriteMaxRetries: 60
                              authenticationEnabled: "true"
                              authenticationProviders:
                              - org.apache.pulsar.broker.authentication.AuthenticationProviderToken
                              - org.apache.pulsar.broker.authentication.AuthenticationProviderTls
                              authorizationEnabled: "true"
                              authorizationProvider: org.apache.pulsar.broker.authorization.PulsarAuthorizationProvider
                              brokerClientAuthenticationParameters: file:///pulsar/token-superuser/superuser.jwt
                              brokerClientAuthenticationPlugin: org.apache.pulsar.client.impl.auth.AuthenticationToken
                              brokerClientTrustCertsFilePath: /etc/ssl/certs/ca-certificates.crt
                              clientAuthenticationParameters: file:///pulsar/token-superuser/superuser.jwt
                              clientAuthenticationPlugin: org.apache.pulsar.client.impl.auth.AuthenticationToken
                              clusterCoordinationTopicName: coordinate
                              configurationStoreServers: pulsar-cluster-zookeeper-ca:2181
                              connectorsDirectory: ./connectors
                              downloadDirectory: /tmp/pulsar_functions
                              failureCheckFreqMs: 30000
                              functionAssignmentTopicName: assignments
                              functionAuthProviderClassName: org.apache.pulsar.functions.auth.KubernetesSecretsTokenAuthProvider
                              functionInstanceMinResources:
                                cpu: 0.1
                                disk: 10737418240
                                ram: 307200000
                              functionMetadataTopicName: metadata
                              includeStandardPrometheusMetrics: "true"
                              initialBrokerReconnectMaxRetries: 60
                              installUserCodeDependencies: "true"
                              instanceLivenessCheckFreqMs: 30000
                              kubernetesContainerFactory:
                                jobNamespace: pulsar
                                percentMemoryPadding: 50
                                pulsarAdminUrl: http://pulsar-cluster-function.pulsar:6750/
                                pulsarDockerImageName: pulsar:latest
                                pulsarRootDir: /pulsar
                                pulsarServiceUrl: pulsar://pulsar-cluster-broker.pulsar:6650/
                                submittingInsidePod: true
                              numFunctionPackageReplicas: 2
                              numHttpServerThreads: 16
                              properties:
                                tokenPublicKey: file:///pulsar/token-public-key/pulsar-public.key
                              pulsarFunctionsCluster: pulsar-cluster
                              pulsarFunctionsNamespace: public/functions
                              pulsarServiceUrl: pulsar://pulsar-cluster-broker:6650
                              pulsarWebServiceUrl: http://pulsar-cluster-broker:8080
                              rescheduleTimeoutMs: 60000
                              runtimeCustomizerClassName: com.datastax.astrastreaming.FunctionsKubernetesManifestCustomizer
                              runtimeCustomizerConfig:
                                extraAnnotations:
                                  prometheus.io/path: /metrics
                                  prometheus.io/port: 9094
                                  prometheus.io/scrape: "true"
                                jobNamespace: pulsar
                                nodeSelectorLabels:
                                  astra-node: functionworker
                                tolerations:
                                - effect: NoSchedule
                                  key: tier
                                  value: funcworker
                              schedulerClassName: org.apache.pulsar.functions.worker.scheduler.RoundRobinScheduler
                              secretsProviderConfiguratorClassName: org.apache.pulsar.functions.secretsproviderconfigurator.KubernetesSecretsProviderConfigurator
                              superUserRoles:
                              - superuser-backup
                              - create-tenant
                              - admin
                              tlsCertificateFilePath: /pulsar/certs/tls.crt
                              tlsKeyFilePath: /pulsar/tls-pk8.key
                              tlsTrustCertsFilePath: /etc/ssl/certs/ca-certificates.crt
                              tokenPublicKey: file:///pulsar/token-public-key/pulsar-public.key
                              topicCompactionFrequencySec: 1800
                              uploadBuiltinSinksSources: "false"
                              workerHostname: pulsar-cluster-function
                              workerId: pulsar-cluster-function
                              workerPort: 6750
                              workerPortTls: 6751
                              zooKeeperSessionTimeoutMillis: 30000
                              zookeeperServers: pulsar-cluster-zookeeper-ca:2181
                            probes:
                              readiness:
                                enabled: true
                                timeoutSeconds: 10
                                initialDelaySeconds: 180
                                periodSeconds: 60
                                failureThreshold: 3
                                successThreshold: 1
                              liveness:
                                enabled: true
                                timeoutSeconds: 10
                                initialDelaySeconds: 180
                                periodSeconds: 60
                                failureThreshold: 3
                                successThreshold: 1
                            updateStrategy:
                              type: RollingUpdate
                            podManagementPolicy: OrderedReady
                            gracePeriod: 60
                            resources:
                              requests:
                                cpu: 100m
                                memory: 1Gi
                            service:
                              annotations:
                                meta.helm.sh/release-name: pulsar-cluster
                                meta.helm.sh/release-namespace: pulsar
                              additionalPorts: []
                              type: ClusterIP
                            logsVolume:
                              name: logs
                              size: 5Gi
                              existingStorageClassName: pulsar-cluster-function-logs
                            runtime: kubernetes
                            rbac:
                              create: false
                              namespaced: true
                        status:
                          conditions: []
                        """
        );
    }

    private PulsarCluster getPulsarClusterFromOutputdir(File tmpDir) throws IOException {
        final String asString = Files.readString(SpecGenerator.getGeneratedPulsarClusterFileFromDir(tmpDir));
        final PulsarCluster pulsarCluster = SerializationUtil.readYaml(
                asString, PulsarCluster.class
        );
        return pulsarCluster;
    }


    @SneakyThrows
    private DiffCollectorOutputWriter generate(MockKubernetesClient client, Path tempDir) {
        InputClusterSpecs inputClusterSpecs = new InputClusterSpecs();
        inputClusterSpecs.setClusterName(CLUSTER_NAME);
        inputClusterSpecs.setNamespace(NAMESPACE);
        inputClusterSpecs.setContext(CONTEXT);

        new SpecGenerator(tempDir.toFile().getAbsolutePath(), inputClusterSpecs)
                .generate(client.getClient());
        final File outputDir = new File(tempDir.toFile(), CONTEXT);

        Collection<Pair<File, Map<String, Object>>> originalResources =
                DiffChecker.readResourcesDirectory(SpecGenerator.getOriginalResourcesFileFromDir(outputDir));
        Collection<Pair<File, Map<String, Object>>> generatedResources =
                DiffChecker.readResourcesDirectory(SpecGenerator.getGeneratedResourcesFileFromDir(outputDir));


        final DiffCollectorOutputWriter diffOutputWriter = new DiffCollectorOutputWriter();
        DiffChecker diffChecker = new DiffChecker(diffOutputWriter);
        diffChecker.checkDiffsFromMaps(originalResources, generatedResources);
        return diffOutputWriter;
    }
}
