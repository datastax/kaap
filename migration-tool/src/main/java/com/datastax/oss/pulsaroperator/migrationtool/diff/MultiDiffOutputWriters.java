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
package com.datastax.oss.pulsaroperator.migrationtool.diff;

import com.datastax.oss.pulsaroperator.migrationtool.json.JSONComparator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

public class MultiDiffOutputWriters implements DiffOutputWriter {
    private final List<DiffOutputWriter> writers;

    public MultiDiffOutputWriters(
            List<DiffOutputWriter> writers) {
        this.writers = writers;
    }

    @Override
    public void diffOk(Pair<DiffChecker.Resource, DiffChecker.Resource> resources) {
        writers.forEach(w -> w.diffOk(resources));
    }

    @Override
    public void diffFailed(Pair<DiffChecker.Resource, DiffChecker.Resource> resources,
                           List<JSONComparator.FieldComparisonFailure> fieldFailures, Map<String, Object> genJson,
                           Map<String, Object> originalJson) {
        writers.forEach(w -> w.diffFailed(resources, fieldFailures, genJson, originalJson));
    }

    @Override
    public void flush() {
        writers.forEach(DiffOutputWriter::flush);
    }
}
