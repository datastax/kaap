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
package com.datastax.oss.kaap.controllers.bookkeeper.racks.client;

import com.datastax.oss.kaap.common.SerializationUtil;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.security.auth.x500.X500Principal;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryUntilElapsed;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.util.PemReader;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

@JBossLog
public class ZkClientRackClient implements BkRackClient {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    public static final String BOOKIES_PATH = "/bookies";
    private final CuratorFramework zkClient;

    public static ZkClientRackClient plainClient(String zkConnectString, Map<String, String> additionalZkClientConfig) {
        return new ZkClientRackClient(zkConnectString, null, null, null, additionalZkClientConfig);
    }
    public static ZkClientRackClient sslClient(String zkConnectString, String privateKey, String serverCertificate,
                              String caCertificate, Map<String, String> additionalZkClientConfig) {
        return new ZkClientRackClient(zkConnectString, privateKey, serverCertificate, caCertificate, additionalZkClientConfig);
    }

    public ZkClientRackClient(String zkConnectString, String privateKey, String serverCertificate,
                              String caCertificate,
                              Map<String, String> additionalZkClientConfig) {
        final ZKClientConfig zkClientConfig = new ZKClientConfig();
        if (privateKey != null) {
            log.infof("Creating new zookeeper client for %s (ssl)", zkConnectString);
            final Path keyStore = Paths.get(zkConnectString.replace(":", "_") + ".keystore.jks");
            final Path trustStore = Paths.get(zkConnectString.replace(":", "_") + ".truststore.jks");
            final String keystorePass = genKeyStore(privateKey, serverCertificate, caCertificate, keyStore);
            final String truststorePass = genTrustStore(serverCertificate, trustStore);

            zkClientConfig.setProperty("zookeeper.clientCnxnSocket", "org.apache.zookeeper.ClientCnxnSocketNetty");
            zkClientConfig.setProperty("zookeeper.client.secure", "true");
            zkClientConfig.setProperty("zookeeper.ssl.keyStore.location", keyStore.toFile().getAbsolutePath());
            zkClientConfig.setProperty("zookeeper.ssl.keyStore.password", keystorePass);
            zkClientConfig.setProperty("zookeeper.ssl.trustStore.location", trustStore.toFile().getAbsolutePath());
            zkClientConfig.setProperty("zookeeper.ssl.trustStore.password", truststorePass);
            zkClientConfig.setProperty("zookeeper.ssl.hostnameVerification", "true");
            zkClientConfig.setProperty("zookeeper.ssl.protocol", "TLSv1.2");
            zkClientConfig.setProperty("zookeeper.ssl.enabledProtocols", "TLSv1.3,TLSv1.2");
        } else {
            log.infof("Creating new zookeeper client for %s (plain)", zkConnectString);
        }
        if (additionalZkClientConfig != null) {
            additionalZkClientConfig.forEach(zkClientConfig::setProperty);
        }
        this.zkClient = CuratorFrameworkFactory
                .newClient(zkConnectString, 60_000, 15_000,
                        new RetryUntilElapsed(30_000, 5000),
                        zkClientConfig);
        zkClient.start();
    }


    @SneakyThrows
    private static String genKeyStore(String privateKey, String serverCertificate,
                                    String caCertificate, Path toFile) {
        final KeyStore keyStore = KeyStore.getInstance("JKS");

        final List<X509Certificate> x509Certificates = PemReader.readCertificateChain(serverCertificate);
        if (caCertificate != null) {
            x509Certificates.addAll(PemReader.readCertificateChain(caCertificate));
        }
        keyStore.load(null, null);

        final PrivateKey privateKeyObj =
                PemReader.loadPrivateKey(privateKey, Optional.empty());

        final String password = UUID.randomUUID().toString();
        keyStore.setKeyEntry("key", privateKeyObj, password.toCharArray(),
                x509Certificates.toArray(new X509Certificate[0]));

        try (FileOutputStream fos = new FileOutputStream(toFile.toFile())) {
            keyStore.store(fos, password.toCharArray());
        }
        return password;
    }

    @SneakyThrows
    private static String genTrustStore(String serverCertificate, Path toFile) {
        final List<X509Certificate> x509Certificates = PemReader.readCertificateChain(serverCertificate);

        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, null);

        for (X509Certificate certificate : x509Certificates) {
            X500Principal principal = certificate.getSubjectX500Principal();
            trustStore.setCertificateEntry(principal.getName("RFC2253"), certificate);
        }
        final String password = UUID.randomUUID().toString();
        try (FileOutputStream fos = new FileOutputStream(toFile.toFile())) {
            trustStore.store(fos, password.toCharArray());
        }
        return password;
    }

    private class ZkNodeOp implements BookiesRackOp {

        private Stat stat;

        @Override
        @SneakyThrows
        public BookiesRackConfiguration get() {
            if (stat != null) {
                throw new IllegalStateException();
            }
            stat = new Stat();
            final byte[] data = zkClient.getData().storingStatIn(stat).forPath(BOOKIES_PATH);
            if (data.length == 0) {
                log.infof("No bookies rack configuration found");
                return new BookiesRackConfiguration();
            } else {
                final String strData = new String(data, StandardCharsets.UTF_8);
                return SerializationUtil.readJson(strData, BookiesRackConfiguration.class);
            }
        }

        @Override
        @SneakyThrows
        public void update(BookiesRackConfiguration newConfig) {
            try {
                zkClient.setData().withVersion(stat.getVersion())
                        .forPath(BOOKIES_PATH, SerializationUtil.writeAsJsonBytes(newConfig));
            } catch (KeeperException.BadVersionException e) {
                // hide stacktrace
                throw new RuntimeException(e.getMessage(), null, false, false) {
                };
            }
        }
    }

    @Override
    public BookiesRackOp newBookiesRackOp() {
        return new ZkNodeOp();
    }

    @Override
    public void close() {
        zkClient.close();
    }
}
