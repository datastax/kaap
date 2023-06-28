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
package com.datastax.oss.kaap.migrationtool.diff;

import com.datastax.oss.kaap.common.json.JSONComparator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class ConsoleDiffOutputWriter extends BaseDiffOutputWriter {


    @Override
    public void diffOk(Pair<DiffChecker.Resource, DiffChecker.Resource> resources) {
        log.info("{}: OK", resources.getLeft().getFullQualifedName());

    }

    @Override
    public void diffFailed(Pair<DiffChecker.Resource, DiffChecker.Resource> resources, List<JSONComparator.FieldComparisonDiff> failures,
                           Map<String, Object> genJson, Map<String, Object> originalJson) {
        log.info("{}: FAILED", resources.getLeft().getFullQualifedName());
        super.diffFailed(resources, failures, genJson, originalJson);
    }

    @Override
    protected void formatFailure(String completeField, String expectedValue, String actualValue) {

        if (actualValue == null) {
            log.info("""
                    - expected: '%s=%s' but none found
                    """.formatted(completeField, expectedValue));
        } else if (expectedValue == null) {
            log.info("""
                    - unexpected: '%s=%s'
                    """.formatted(completeField, actualValue));
        } else {
            log.info("""
                    - '%s' value differs:
                        Original:  %s
                        Generated: %s
                    """.formatted(
                    completeField,
                    expectedValue,
                    actualValue
            ));
        }
    }

    @Override
    public void missingResources(Collection<DiffChecker.Resource> missingResources) {
        log.info("Missing resources:");
        for (DiffChecker.Resource resource : missingResources) {
            log.info(" - {}", resource.getFullQualifedName());
        }
    }

    @Override
    public void newResources(Collection<DiffChecker.Resource> newResources) {
        log.info("New resources:");
        for (DiffChecker.Resource resource : newResources) {
            log.info(" - {}", resource.getFullQualifedName());
        }
    }

    @Override
    public void flush() {
    }
}
