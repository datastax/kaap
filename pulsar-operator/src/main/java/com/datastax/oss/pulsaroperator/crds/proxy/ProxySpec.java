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
package com.datastax.oss.pulsaroperator.crds.proxy;

import com.datastax.oss.pulsaroperator.controllers.proxy.ProxyResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;
import javax.validation.ConstraintValidatorContext;
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
    @JsonPropertyDescription("Proxy sets.")
    private Map<String, ProxySetSpec> sets;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        super.applyDefaults(globalSpec);
    }

    @Override
    public boolean isValid(ProxySetSpec value, ConstraintValidatorContext context) {
        final Map<String, ProxySetSpec> sets = ((ProxySpec) value).getSets();
        if (!isValid(sets, context)) {
            return false;
        }
        return super.isValid(value, context);
    }

    private boolean isValid(Map<String, ProxySetSpec> sets, ConstraintValidatorContext context) {
        if (sets == null || sets.isEmpty()) {
            return true;
        }
        if (sets.containsKey(ProxyResourcesFactory.PROXY_DEFAULT_SET)) {
            context.buildConstraintViolationWithTemplate(
                            "Proxy set name '" + ProxyResourcesFactory.PROXY_DEFAULT_SET + "' is reserved.")
                    .addConstraintViolation();
            return false;
        }
        return true;

    }
}
