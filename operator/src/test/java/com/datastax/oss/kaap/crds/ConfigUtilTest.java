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
package com.datastax.oss.kaap.crds;

import com.datastax.oss.kaap.common.SerializationUtil;
import com.datastax.oss.kaap.crds.broker.BrokerFullSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSetSpec;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ConfigUtilTest {

    @Test
    public void testList() {
        String spec = """
                broker:
                    nodeAffinity:
                        requiredDuringSchedulingIgnoredDuringExecution:
                            nodeSelectorTerms:
                                - matchExpressions:
                                    - key: nodepool
                                      operator: In
                                      values:
                                      - pulsar
                """;

        final BrokerFullSpec brokerFullSpec = SerializationUtil.readYaml(spec, BrokerFullSpec.class);


        final BrokerSetSpec res =
                ConfigUtil.applyDefaultsWithReflection(new BrokerSetSpec(), () -> brokerFullSpec.getBroker());
        System.out.println(SerializationUtil.writeAsJson(res));
        Assert.assertEquals(SerializationUtil.writeAsYaml(res),
                """
                        ---
                        nodeAffinity:
                          requiredDuringSchedulingIgnoredDuringExecution:
                            nodeSelectorTerms:
                            - matchExpressions:
                              - key: nodepool
                                operator: In
                                values:
                                - pulsar
                        """);
    }

}