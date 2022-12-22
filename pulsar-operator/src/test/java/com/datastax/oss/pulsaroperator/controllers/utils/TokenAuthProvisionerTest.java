package com.datastax.oss.pulsaroperator.controllers.utils;

import com.datastax.oss.pulsaroperator.MockKubernetesClient;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TokenAuthProvisionerTest {
    private static final String NAMESPACE = "ns";

    @Test
    public void testDefaults() throws Exception {
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE);
        generateSecrets(mockKubernetesClient, AuthConfig.TokenAuthenticationConfig.builder().build());

        final Map<String, Secret> secretsMap = getGeneratedSecretsMap(mockKubernetesClient);
        Assert.assertEquals(secretsMap.size(), 6);
        Assert.assertEquals(secretsMap.keySet(),
                List.of("token-admin", "token-private-key", "token-proxy", "token-public-key", "token-superuser",
                        "token-websocket"));

        final String privateKeyVal = secretsMap.get("token-private-key")
                .getData()
                .get("my-private.key");
        Assert.assertNotNull(privateKeyVal);
        Assert.assertNotNull(secretsMap.get("token-public-key")
                .getData()
                .get("my-public.key"));

        assertJwtInSecret(secretsMap.get("token-admin"), "admin", privateKeyVal);
        assertJwtInSecret(secretsMap.get("token-superuser"), "superuser", privateKeyVal);
        assertJwtInSecret(secretsMap.get("token-websocket"), "websocket", privateKeyVal);
        assertJwtInSecret(secretsMap.get("token-proxy"), "proxy", privateKeyVal);

    }

    @Test
    public void testExistingSecrets() throws Exception {
        final KeyPair keyPair = TokenAuthProvisioner.genKeyPair();
        final MockKubernetesClient mockKubernetesClient = new MockKubernetesClient(NAMESPACE,
                new MockKubernetesClient.ResourcesResolver() {
                    @Override
                    public Secret secretWithName(String name) {
                        switch (name) {
                            case "token-private-key": {
                                return new SecretBuilder()
                                        .withNewMetadata()
                                        .withName(name)
                                        .endMetadata()
                                        .withData(Map.of("ext-private.key",
                                                TokenAuthProvisioner.encodePrivateKey(keyPair.getPrivate())))
                                        .build();
                            }
                            case "token-public-key": {
                                return new SecretBuilder()
                                        .withNewMetadata()
                                        .withName(name)
                                        .endMetadata()
                                        .withData(Map.of("ext-public.key",
                                                TokenAuthProvisioner.encodePublicKey(keyPair.getPublic())))
                                        .build();
                            }
                            default:
                                return null;
                        }
                    }
                });
        generateSecrets(mockKubernetesClient, AuthConfig.TokenAuthenticationConfig.builder()
                .privateKeyFile("ext-private.key")
                .publicKeyFile("ext-public.key")
                .build());

        final Map<String, Secret> secretsMap = getGeneratedSecretsMap(mockKubernetesClient);
        Assert.assertEquals(secretsMap.size(), 4);
        Assert.assertEquals(secretsMap.keySet(),
                List.of("token-admin", "token-proxy", "token-superuser",
                        "token-websocket"));

        assertJwtInSecret(secretsMap.get("token-admin"), "admin", keyPair.getPrivate());
        assertJwtInSecret(secretsMap.get("token-superuser"), "superuser", keyPair.getPrivate());
        assertJwtInSecret(secretsMap.get("token-websocket"), "websocket", keyPair.getPrivate());
        assertJwtInSecret(secretsMap.get("token-proxy"), "proxy", keyPair.getPrivate());
    }

    private void assertJwtInSecret(Secret secret, String subject, String rawPrivateKey) {
        assertJwtInSecret(secret, subject, TokenAuthProvisioner.parsePrivateKeyFromSecretValue(rawPrivateKey));
    }

    private void assertJwtInSecret(Secret secret, String subject, PrivateKey privateKey) {
        final String jwtBase64 = secret
                .getData()
                .get("%s.jwt".formatted(subject));
        Assert.assertNotNull(jwtBase64);
        final String jwt = new String(Base64.getDecoder().decode(jwtBase64), StandardCharsets.UTF_8);
        Jwts.parserBuilder()
                .requireSubject(subject)
                .setSigningKey(privateKey)
                .build()
                .parse(jwt);
    }

    private Map<String, Secret> getGeneratedSecretsMap(MockKubernetesClient mockKubernetesClient) {
        return new TreeMap<>(mockKubernetesClient.getCreatedResources(Secret.class)
                .stream()
                .map(r -> r.getResource())
                .collect(Collectors.toMap(r -> r.getMetadata().getName(), Function.identity()))
        );
    }

    private void generateSecrets(MockKubernetesClient mockKubernetesClient, AuthConfig.TokenAuthenticationConfig tokenConfig) {
        final TokenAuthProvisioner tokenAuthProvisioner =
                new TokenAuthProvisioner(mockKubernetesClient.getClient(), NAMESPACE);

        final GlobalSpec globalSpec = GlobalSpec.builder()
                .auth(AuthConfig.builder()
                        .enabled(true)
                        .token(tokenConfig)
                        .build())
                .build();
        globalSpec.applyDefaults(null);
        tokenAuthProvisioner.generateSecretsIfAbsent(globalSpec.getAuth().getToken());
    }
}