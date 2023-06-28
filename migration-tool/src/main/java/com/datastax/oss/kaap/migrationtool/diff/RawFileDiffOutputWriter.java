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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class RawFileDiffOutputWriter extends BaseDiffOutputWriter {

    private final StringBuilder builder = new StringBuilder();
    private final Path outputFile;

    public RawFileDiffOutputWriter(Path outputFile) {
        this.outputFile = outputFile;
    }

    @Override
    public void diffOk(Pair<DiffChecker.Resource, DiffChecker.Resource> resources) {
        builder.append(resources.getLeft().getFullQualifedName() + ": OK\n");

    }


    @Override
    public void diffFailed(Pair<DiffChecker.Resource, DiffChecker.Resource> resources, List<JSONComparator.FieldComparisonDiff> failures,
                           Map<String, Object> genJson, Map<String, Object> originalJson) {
        builder.append(resources.getLeft().getFullQualifedName() + ": FAILED\n");
        super.diffFailed(resources, failures, genJson, originalJson);
    }


    @Override
    protected void formatFailure(String completeField, String expectedValue, String actualValue) {

        if (actualValue == null) {
            builder.append("""
                        - expected: '%s=%s' but none found
                    """.formatted(completeField, expectedValue));
        } else if (expectedValue == null) {
            builder.append("""
                        - unexpected: '%s=%s'
                    """.formatted(completeField, actualValue));
        } else {
            builder.append("""
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
        builder.append("Missing resources:");
        for (DiffChecker.Resource resource : missingResources) {
            builder.append(" - %s".formatted(resource.getFullQualifedName()));
        }
    }

    @Override
    public void newResources(Collection<DiffChecker.Resource> newResources) {
        builder.append("New resources:");
        for (DiffChecker.Resource resource : newResources) {
            builder.append(" - %s".formatted(resource.getFullQualifedName()));
        }
    }

    @Override
    @SneakyThrows
    public void flush() {
        Files.write(outputFile, builder.toString().getBytes(StandardCharsets.UTF_8));
        log.info("Exported diff to {}", outputFile.toAbsolutePath());
    }
}
