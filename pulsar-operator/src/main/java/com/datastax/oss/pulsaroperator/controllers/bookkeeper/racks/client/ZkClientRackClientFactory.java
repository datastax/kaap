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
package com.datastax.oss.pulsaroperator.controllers.bookkeeper.racks.client;

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperAutoRackConfig;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperFullSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.LaunchMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ZkClientRackClientFactory implements BkRackClientFactory{

    private final Map<String, ZkClientRackClient> zkClients = new ConcurrentHashMap<>();
    private final KubernetesClient client;

    public ZkClientRackClientFactory(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public BkRackClient newBkRackClient(String namespace, BookKeeperFullSpec newSpec,
                                               BookKeeperAutoRackConfig autoRackConfig) {
        final String zkConnectString = getZkServers(namespace, newSpec);

        if (!autoRackConfig.getEnabled()) {
            final ZkClientRackClient zkClientRackClient = zkClients.remove(zkConnectString);
            if (zkClientRackClient != null) {
                zkClientRackClient.close();
            }
        }

        final ZkClientRackClient zkClient =
                zkClients.computeIfAbsent(zkConnectString,
                        (k) -> newZkRackClient(k, newSpec.getGlobalSpec(), namespace));
        return zkClient;
    }


    private ZkClientRackClient newZkRackClient(String zkConnectString, GlobalSpec globalSpec, String namespace) {
        final boolean tlsEnabledOnZooKeeper = BaseResourcesFactory.isTlsEnabledOnZooKeeper(globalSpec);
        if (!tlsEnabledOnZooKeeper) {
            return ZkClientRackClient.plainClient(zkConnectString);
        }
        final String tlsSecretNameForZookeeper = BaseResourcesFactory.getTlsSecretNameForZookeeper(globalSpec);
        final Secret secret = client.secrets()
                .inNamespace(namespace)
                .withName(tlsSecretNameForZookeeper)
                .get();
        if (secret == null) {
            throw new IllegalStateException(
                    "Cannot create ssl client for Zookeeper, secret '" + tlsSecretNameForZookeeper + "' not found");
        }

        final String privateKey =
                new String(Base64.getDecoder().decode(secret.getData().get("tls.key")), StandardCharsets.UTF_8);
        final String serverCert =
                new String(Base64.getDecoder().decode(secret.getData().get("tls.crt")), StandardCharsets.UTF_8);
        String caCert = secret.getData().get("ca.crt");
        if (caCert != null) {
            caCert = new String(Base64.getDecoder().decode(caCert), StandardCharsets.UTF_8);
        }
        return ZkClientRackClient.sslClient(zkConnectString, privateKey, serverCert, caCert);
    }

    protected String getZkServers(String namespace, BookKeeperFullSpec newSpec) {
        if (LaunchMode.current() == LaunchMode.DEVELOPMENT) {
            // tls can't work in dev mode because in order to perform hostname validation you have to connect to the
            // real hostname
            return "localhost:2181";
        }
        return BaseResourcesFactory.getZkServers(newSpec.getGlobal(), namespace);
    }


    @Override
    public void close() throws Exception {
        zkClients.values().forEach(ZkClientRackClient::close);
    }
}
