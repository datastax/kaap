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
package com.datastax.oss.kaap.crds.bookkeeper;

import com.datastax.oss.kaap.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.kaap.crds.ConfigUtil;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.function.Supplier;
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
public class BookKeeperSpec extends BookKeeperSetSpec {
    private static final Supplier<BookKeeperAutoRackConfig> DEFAULT_AUTO_RACK_CONFIG =
            () -> BookKeeperAutoRackConfig.builder()
                    .enabled(true)
                    .periodMs(60000L)
                    .build();

    public enum BookKeeperSetsUpdateStrategy {
        RollingUpdate,
        Parallel
    }

    @JsonPropertyDescription("Bookie sets.")
    private LinkedHashMap<String, BookKeeperSetSpec> sets;
    @JsonPropertyDescription("Sets update strategy. 'RollingUpdate' or 'Parallel'. Default is 'RollingUpdate'.")
    private String setsUpdateStrategy;
    @JsonPropertyDescription("Configuration for the rack auto configuration.")
    private BookKeeperAutoRackConfig autoRackConfig;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (setsUpdateStrategy == null) {
            setsUpdateStrategy = BookKeeperSetsUpdateStrategy.RollingUpdate.toString();
        }
        if (autoRackConfig == null) {
            autoRackConfig = DEFAULT_AUTO_RACK_CONFIG.get();
        } else {
            autoRackConfig = ConfigUtil.applyDefaultsWithReflection(autoRackConfig, DEFAULT_AUTO_RACK_CONFIG);
        }

        super.applyDefaults(globalSpec);
    }

    @Override
    public boolean isValid(BookKeeperSetSpec value, ConstraintValidatorContext context) {
        if (!isBookKeeperSpecValid((BookKeeperSpec) value, context)) {
            return false;
        }
        return super.isValid(value, context);
    }

    private boolean isBookKeeperSpecValid(BookKeeperSpec value, ConstraintValidatorContext context) {
        if (Arrays.stream(BookKeeperSetsUpdateStrategy.values())
                .noneMatch(s -> s.toString().equals(value.getSetsUpdateStrategy()))) {
            context.buildConstraintViolationWithTemplate(
                            "Invalid sets update strategy: %s, only %s".formatted(value.getSetsUpdateStrategy(),
                                    Arrays.toString(BookKeeperSetsUpdateStrategy.values())))
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    @JsonIgnore
    public BookKeeperSetSpec getDefaultBookKeeperSpecRef() {
        if (sets == null || !sets.containsKey(BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET)) {
            return this;
        }
        return sets.get(BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET);
    }

    @JsonIgnore
    public BookKeeperSetSpec getBookKeeperSetSpecRef(String set) {
        if (set.equals(BookKeeperResourcesFactory.BOOKKEEPER_DEFAULT_SET)) {
            return getDefaultBookKeeperSpecRef();
        }
        return sets.get(set);
    }

}
