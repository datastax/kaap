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
package com.datastax.oss.pulsaroperator.tests;

import static com.datastax.oss.pulsaroperator.controllers.utils.TokenAuthProvisioner.parsePrivateKeyFromSecretValue;
import com.datastax.oss.pulsaroperator.controllers.utils.TokenAuthProvisioner;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.configs.AuthConfig;
import io.fabric8.kubernetes.api.model.Secret;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
@Test(groups = "misc")
public class TokenProvisionerTest extends BasePulsarClusterTest {

    @Test
    public void test() throws Exception {
        final TokenAuthProvisioner provisioner = new TokenAuthProvisioner(client, namespace);

        final GlobalSpec globalSpec = GlobalSpec.builder()
                .auth(AuthConfig.builder()
                        .enabled(true)
                        .build()
                ).build();
        globalSpec.applyDefaults(null);
        final AuthConfig.TokenAuthenticationConfig config = globalSpec.getAuth().getToken();
        provisioner.generateSecretsIfAbsent(config);

        final List<Secret> secrets = client.secrets().inNamespace(namespace)
                .list().getItems();
        Assert.assertEquals(secrets.size(), 6);
        Assert.assertNotNull(getSecretByName(secrets, "token-public-key")
                .getData()
                .get("my-public.key"));
        final String privateKey = getSecretByName(secrets, "token-private-key")
                .getData()
                .get("my-private.key");

        for (String superUserRole : config.getSuperUserRoles()) {
            verifyRoleToken(secrets, privateKey, superUserRole);
        }
    }

    private void verifyRoleToken(List<Secret> secrets, String privateKey, String role) {
        final String jwt64 = getSecretByName(secrets, "token-" + role)
                .getData()
                .get(role + ".jwt");
        final String jwt = new String(Base64.getDecoder().decode(jwt64), StandardCharsets.UTF_8);
        final PrivateKey pKey = parsePrivateKeyFromSecretValue(privateKey);
        Jwts.parserBuilder()
                .setSigningKey(pKey)
                .requireSubject(role)
                .build()
                .parse(jwt);
    }

    private Secret getSecretByName(List<Secret> secrets, String secret) {
        for (Secret s : secrets) {
            if (s.getMetadata().getName().equals(secret)) {
                return s;
            }
        }
        return null;
    }

}
