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
package com.datastax.oss.kaap.crds.proxy;

import com.datastax.oss.kaap.crds.GlobalSpec;
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
public class ProxySpec extends ProxySetSpec {
    public enum ProxySetsUpdateStrategy {
        RollingUpdate,
        Parallel
    }

    @JsonPropertyDescription("Proxy sets.")
    private LinkedHashMap<String, ProxySetSpec> sets;
    @JsonPropertyDescription("Sets update strategy. 'RollingUpdate' or 'Parallel'. Default is 'RollingUpdate'.")
    private String setsUpdateStrategy;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (setsUpdateStrategy == null) {
            setsUpdateStrategy = ProxySetsUpdateStrategy.RollingUpdate.toString();
        }
        super.applyDefaults(globalSpec);
    }

    @Override
    public boolean isValid(ProxySetSpec value, ConstraintValidatorContext context) {
        if (!isProxySpecValid((ProxySpec) value, context)) {
            return false;
        }
        return super.isValid(value, context);
    }

    private boolean isProxySpecValid(ProxySpec value, ConstraintValidatorContext context) {
        final ProxySpec proxySpec = value;
        if (Arrays.stream(ProxySetsUpdateStrategy.values())
                .noneMatch(s -> s.toString().equals(proxySpec.getSetsUpdateStrategy()))) {
            context.buildConstraintViolationWithTemplate(
                            "Invalid sets update strategy: %s, only %s".formatted(proxySpec.getSetsUpdateStrategy(),
                                    Arrays.toString(ProxySetsUpdateStrategy.values())))
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
