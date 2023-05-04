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
package com.datastax.oss.pulsaroperator.controllers;

import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.tls.TlsConfig;
import org.junit.Test;
import org.testng.Assert;

public class BaseResourcesFactoryTest {

    @Test
    public void testTlsTrustStoreArgs() throws Exception {
        final GlobalSpec globalSpec = GlobalSpec.builder()
                .tls(TlsConfig.builder()
                        .enabled(true)
                        .zookeeper(TlsConfig.TlsEntryConfig.builder()
                                .enabled(true)
                                .build())
                        .build())
                .build();

        BaseResourcesFactory factory = getFactory(globalSpec);
        String script = factory.generateCertConverterScript();
        Assert.assertEquals(script,
                """
                        certconverter() {
                            local name=pulsar
                            local crtFile=/pulsar/certs/tls.crt
                            local keyFile=/pulsar/certs/tls.key
                            caFile=/etc/ssl/certs/ca-certificates.crt
                            p12File=/pulsar/tls.p12
                            keyStoreFile=/pulsar/tls.keystore.jks
                            trustStoreFile=/pulsar/tls.truststore.jks
                                                
                            head /dev/urandom | base64 | head -c 24 > /pulsar/keystoreSecret.txt
                            export tlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                            export PF_tlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                            export tlsKeyStorePassword=$(cat /pulsar/keystoreSecret.txt)
                            export PF_tlsKeyStorePassword=$(cat /pulsar/keystoreSecret.txt)
                            export PULSAR_PREFIX_brokerClientTlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                                                
                            openssl pkcs12 \\
                                -export \\
                                -in ${crtFile} \\
                                -inkey ${keyFile} \\
                                -out ${p12File} \\
                                -name ${name} \\
                                -passout "file:/pulsar/keystoreSecret.txt"
                                                
                            keytool -importkeystore \\
                                -srckeystore ${p12File} \\
                                -srcstoretype PKCS12 -srcstorepass:file "/pulsar/keystoreSecret.txt" \\
                                -alias ${name} \\
                                -destkeystore ${keyStoreFile} \\
                                -deststorepass:file "/pulsar/keystoreSecret.txt"
                                                
                            keytool -import \\
                                -file ${caFile} \\
                                -storetype JKS \\
                                -alias ${name} \\
                                -keystore ${trustStoreFile} \\
                                -storepass:file "/pulsar/keystoreSecret.txt" \\
                                -trustcacerts -noprompt
                        } &&
                        certconverter &&
                        (
                        cat >> conf/pulsar_env.sh << EOF
                                                
                        PULSAR_EXTRA_OPTS="\\${PULSAR_EXTRA_OPTS} -Dzookeeper.clientCnxnSocket=org.apache.zookeeper.ClientCnxnSocketNetty -Dzookeeper.client.secure=true -Dzookeeper.ssl.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.keyStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.trustStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.hostnameVerification=true -Dzookeeper.sslQuorum=true -Dzookeeper.serverCnxnFactory=org.apache.zookeeper.server.NettyServerCnxnFactory -Dzookeeper.ssl.quorum.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.quorum.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.quorum.hostnameVerification=true"
                        EOF
                        ) && (
                        cat >> conf/bkenv.sh << EOF
                                                
                        BOOKIE_EXTRA_OPTS="\\${BOOKIE_EXTRA_OPTS} -Dzookeeper.clientCnxnSocket=org.apache.zookeeper.ClientCnxnSocketNetty -Dzookeeper.client.secure=true -Dzookeeper.ssl.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.keyStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.trustStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.hostnameVerification=true -Dzookeeper.ssl.quorum.keyStore.passwordPath=/pulsar/keystoreSecret.txt -Dzookeeper.ssl.quorum.trustStore.passwordPath=/pulsar/keystoreSecret.txt"
                        EOF
                        ) &&
                        echo ''""");
        globalSpec.setZookeeperPlainSslStorePassword(true);
        factory = getFactory(globalSpec);
        script = factory.generateCertConverterScript();
        Assert.assertEquals(script,
                """
                        certconverter() {
                            local name=pulsar
                            local crtFile=/pulsar/certs/tls.crt
                            local keyFile=/pulsar/certs/tls.key
                            caFile=/etc/ssl/certs/ca-certificates.crt
                            p12File=/pulsar/tls.p12
                            keyStoreFile=/pulsar/tls.keystore.jks
                            trustStoreFile=/pulsar/tls.truststore.jks
                                                
                            head /dev/urandom | base64 | head -c 24 > /pulsar/keystoreSecret.txt
                            export tlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                            export PF_tlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                            export tlsKeyStorePassword=$(cat /pulsar/keystoreSecret.txt)
                            export PF_tlsKeyStorePassword=$(cat /pulsar/keystoreSecret.txt)
                            export PULSAR_PREFIX_brokerClientTlsTrustStorePassword=$(cat /pulsar/keystoreSecret.txt)
                                                
                            openssl pkcs12 \\
                                -export \\
                                -in ${crtFile} \\
                                -inkey ${keyFile} \\
                                -out ${p12File} \\
                                -name ${name} \\
                                -passout "file:/pulsar/keystoreSecret.txt"
                                                
                            keytool -importkeystore \\
                                -srckeystore ${p12File} \\
                                -srcstoretype PKCS12 -srcstorepass:file "/pulsar/keystoreSecret.txt" \\
                                -alias ${name} \\
                                -destkeystore ${keyStoreFile} \\
                                -deststorepass:file "/pulsar/keystoreSecret.txt"
                                                
                            keytool -import \\
                                -file ${caFile} \\
                                -storetype JKS \\
                                -alias ${name} \\
                                -keystore ${trustStoreFile} \\
                                -storepass:file "/pulsar/keystoreSecret.txt" \\
                                -trustcacerts -noprompt
                        } &&
                        certconverter &&
                        (
                        cat >> conf/pulsar_env.sh << EOF
                                                
                        PULSAR_EXTRA_OPTS="\\${PULSAR_EXTRA_OPTS} -Dzookeeper.clientCnxnSocket=org.apache.zookeeper.ClientCnxnSocketNetty -Dzookeeper.client.secure=true -Dzookeeper.ssl.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.keyStore.password=$(cat /pulsar/keystoreSecret.txt) -Dzookeeper.ssl.trustStore.password=$(cat /pulsar/keystoreSecret.txt) -Dzookeeper.ssl.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.hostnameVerification=true -Dzookeeper.sslQuorum=true -Dzookeeper.serverCnxnFactory=org.apache.zookeeper.server.NettyServerCnxnFactory -Dzookeeper.ssl.quorum.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.quorum.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.quorum.hostnameVerification=true"
                        EOF
                        ) && (
                        cat >> conf/bkenv.sh << EOF
                                                
                        BOOKIE_EXTRA_OPTS="\\${BOOKIE_EXTRA_OPTS} -Dzookeeper.clientCnxnSocket=org.apache.zookeeper.ClientCnxnSocketNetty -Dzookeeper.client.secure=true -Dzookeeper.ssl.keyStore.location=${keyStoreFile} -Dzookeeper.ssl.keyStore.password=$(cat /pulsar/keystoreSecret.txt) -Dzookeeper.ssl.trustStore.password=$(cat /pulsar/keystoreSecret.txt) -Dzookeeper.ssl.trustStore.location=${trustStoreFile} -Dzookeeper.ssl.hostnameVerification=true -Dzookeeper.ssl.quorum.keyStore.password=$(cat /pulsar/keystoreSecret.txt) -Dzookeeper.ssl.quorum.trustStore.password=$(cat /pulsar/keystoreSecret.txt)"
                        EOF
                        ) &&
                        echo ''""");
    }

    private BaseResourcesFactory getFactory(GlobalSpec globalSpec) {
        globalSpec.applyDefaults(null);
        return new BaseResourcesFactory<>(null, "ns", "test", null, globalSpec, null) {
            @Override
            protected String getComponentBaseName() {
                return "test";
            }

            @Override
            protected boolean isComponentEnabled() {
                return true;
            }
        };
    }

}