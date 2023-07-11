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
package com.datastax.oss.kaap.crds.broker;

import com.datastax.oss.kaap.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.LinkedHashMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class BrokerSpec extends BrokerSetSpec {

    public enum BrokerSetsUpdateStrategy {
        RollingUpdate,
        Parallel
    }

    @JsonPropertyDescription("Broker sets.")
    private LinkedHashMap<String, BrokerSetSpec> sets;
    @JsonPropertyDescription("Sets update strategy. 'RollingUpdate' or 'Parallel'. Default is 'RollingUpdate'.")
    private String setsUpdateStrategy;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        super.applyDefaults(globalSpec);
        if (setsUpdateStrategy == null) {
            setsUpdateStrategy = BrokerSetsUpdateStrategy.RollingUpdate.toString();
        }
    }

    @Override
    public boolean isValid(BrokerSetSpec value, ConstraintValidatorContext context) {
        final BrokerSpec brokerSpec = (BrokerSpec) value;
        if (!isBrokerSpecValid(context, brokerSpec)) {
            return false;
        }
        return super.isValid(value, context);
    }

    private boolean isBrokerSpecValid(ConstraintValidatorContext context, BrokerSpec brokerSpec) {
        if (Arrays.stream(BrokerSetsUpdateStrategy.values())
                .noneMatch(s -> s.toString().equals(brokerSpec.getSetsUpdateStrategy()))) {
            context.buildConstraintViolationWithTemplate(
                            "Invalid sets update strategy: %s, only %s".formatted(brokerSpec.getSetsUpdateStrategy(),
                                    Arrays.toString(BrokerSetsUpdateStrategy.values())))
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    @JsonIgnore
    public BrokerSetSpec getDefaultBrokerSpecRef() {
        if (sets == null || !sets.containsKey(BrokerResourcesFactory.BROKER_DEFAULT_SET)) {
            return this;
        }
        return sets.get(BrokerResourcesFactory.BROKER_DEFAULT_SET);
    }
}
