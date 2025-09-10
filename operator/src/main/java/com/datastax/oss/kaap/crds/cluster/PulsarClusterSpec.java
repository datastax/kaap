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
package com.datastax.oss.kaap.crds.cluster;

import com.datastax.oss.kaap.crds.FullSpecWithDefaults;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.WithDefaults;
import com.datastax.oss.kaap.crds.autorecovery.AutorecoverySpec;
import com.datastax.oss.kaap.crds.bastion.BastionSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSetSpec;
import com.datastax.oss.kaap.crds.broker.BrokerSpec;
import com.datastax.oss.kaap.crds.configs.RackConfig;
import com.datastax.oss.kaap.crds.configs.ResourceSetConfig;
import com.datastax.oss.kaap.crds.function.FunctionsWorkerSpec;
import com.datastax.oss.kaap.crds.proxy.ProxySetSpec;
import com.datastax.oss.kaap.crds.proxy.ProxySpec;
import com.datastax.oss.kaap.crds.validation.ValidSpec;
import com.datastax.oss.kaap.crds.validation.ValidableSpec;
import com.datastax.oss.kaap.crds.zookeeper.ZooKeeperSpec;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Valid;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Valid
@ValidSpec
public class PulsarClusterSpec extends ValidableSpec<PulsarClusterSpec> implements FullSpecWithDefaults {

    @ValidSpec
    @Valid
    private GlobalSpec global;

    @ValidSpec
    @Valid
    private ZooKeeperSpec zookeeper;

    @ValidSpec
    @Valid
    private BookKeeperSpec bookkeeper;

    @ValidSpec
    @Valid
    private BrokerSpec broker;

    @ValidSpec
    @Valid
    private ProxySpec proxy;

    @ValidSpec
    @Valid
    private AutorecoverySpec autorecovery;

    @ValidSpec
    @Valid
    private BastionSpec bastion;

    @ValidSpec
    @Valid
    private FunctionsWorkerSpec functionsWorker;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        initDefaultsForComponent(GlobalSpec.class, "global", globalSpec);
        initDefaultsForComponent(ZooKeeperSpec.class, "zookeeper", globalSpec);
        initDefaultsForComponent(BookKeeperSpec.class, "bookkeeper", globalSpec);
        initDefaultsForComponent(BrokerSpec.class, "broker", globalSpec);
        initDefaultsForComponent(ProxySpec.class, "proxy", globalSpec);
        initDefaultsForComponent(AutorecoverySpec.class, "autorecovery", globalSpec);
        initDefaultsForComponent(BastionSpec.class, "bastion", globalSpec);
        initDefaultsForComponent(FunctionsWorkerSpec.class, "functionsWorker", globalSpec);
    }

    @SneakyThrows
    private <T extends WithDefaults> void initDefaultsForComponent(Class<T> componentClass, String fieldName,
                                                                   GlobalSpec globalSpec) {
        final Field field = PulsarClusterSpec.class.getDeclaredField(fieldName);
        T current = (T) field.get(this);
        if (current == null) {
            current = componentClass.getConstructor().newInstance();
        }
        current.applyDefaults(globalSpec);
        field.set(this, current);
    }

    @Override
    public GlobalSpec getGlobalSpec() {
        return global;
    }

    @Override
    public boolean isValid(PulsarClusterSpec value, ConstraintValidatorContext context) {
        return value.getGlobal().isValid(value.getGlobal(), context)
                && value.getZookeeper().isValid(value.getZookeeper(), context)
                && value.getBookkeeper().isValid(value.getBookkeeper(), context)
                && value.getBroker().isValid(value.getBroker(), context)
                && value.getProxy().isValid(value.getProxy(), context)
                && value.getAutorecovery().isValid(value.getAutorecovery(), context)
                && value.getBastion().isValid(value.getBastion(), context)
                && validateResourceSets(value, context);
    }

    private boolean validateResourceSets(PulsarClusterSpec spec, ConstraintValidatorContext context) {
        Map<String, ResourceSetConfig> resourceSets = spec.getGlobal().getResourceSets();
        if (resourceSets == null) {
            resourceSets = Collections.emptyMap();
        }

        return validateResourceSetsRacks(resourceSets, spec.getGlobal().getRacks(), context)
                && validateBrokerResourceSets(resourceSets, spec.getBroker(), context)
                && validateProxyResourceSets(resourceSets, spec.getProxy(), context)
                && validateBookKeeperResourceSets(resourceSets, spec.getBookkeeper(), context);

    }

    private boolean validateResourceSetsRacks(Map<String, ResourceSetConfig> resourceSets,
                                              Map<String, RackConfig> racks, ConstraintValidatorContext context) {
        for (Map.Entry<String, ResourceSetConfig> set : resourceSets.entrySet()) {
            final ResourceSetConfig value = set.getValue();
            if (value != null && value.getRack() != null) {
                if (racks == null || !racks.containsKey(value.getRack())) {
                    context.buildConstraintViolationWithTemplate(
                                    ("Resource set %s references a rack %s that does not exist. You must define racks "
                                            + "in .global.racks")
                                            .formatted(set.getKey(), value.getRack()))
                            .addConstraintViolation();
                    return false;
                }
            }
        }
        return true;
    }

    private boolean validateBrokerResourceSets(Map<String, ResourceSetConfig> declaredResourceSets,
                                               BrokerSpec spec, ConstraintValidatorContext context) {
        final Map<String, BrokerSetSpec> sets = spec.getSets();
        if (sets == null || sets.isEmpty()) {
            return true;
        }
        return validateResourceSetNames(sets.keySet(), declaredResourceSets, "broker", context);
    }

    private boolean validateProxyResourceSets(Map<String, ResourceSetConfig> declaredResourceSets,
                                              ProxySpec spec, ConstraintValidatorContext context) {
        final Map<String, ProxySetSpec> sets = spec.getSets();
        if (sets == null || sets.isEmpty()) {
            return true;
        }
        return validateResourceSetNames(sets.keySet(), declaredResourceSets, "proxy", context);
    }

    private boolean validateBookKeeperResourceSets(Map<String, ResourceSetConfig> declaredResourceSets,
                                              BookKeeperSpec spec, ConstraintValidatorContext context) {
        final Map<String, BookKeeperSetSpec> sets = spec.getSets();
        if (sets == null || sets.isEmpty()) {
            return true;
        }
        return validateResourceSetNames(sets.keySet(), declaredResourceSets, "bookkeeper", context);
    }

    private boolean validateResourceSetNames(Set<String> sets,
                                             Map<String, ResourceSetConfig> declaredResourceSets,
                                             String nameForError,
                                             ConstraintValidatorContext context) {
        for (String s : sets) {
            if (!declaredResourceSets.containsKey(s)) {
                context.buildConstraintViolationWithTemplate(
                                ("%s resource set %s is not defined in global resource sets (.global.resourceSets)"
                                        + ", only %s")
                                        .formatted(nameForError, s, declaredResourceSets.keySet()))
                        .addConstraintViolation();
                return false;
            }
        }
        return true;
    }
}
