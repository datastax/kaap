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

import com.datastax.oss.pulsaroperator.common.json.JSONComparator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class DiffCollectorOutputWriter implements DiffOutputWriter {

    @Getter
    private final List<JSONComparator.FieldComparisonDiff> all = new ArrayList<>();

    @Override
    public void diffOk(Pair<DiffChecker.Resource, DiffChecker.Resource> resources) {
    }

    @Override
    public void diffFailed(Pair<DiffChecker.Resource, DiffChecker.Resource> resources, List<JSONComparator.FieldComparisonDiff> failures,
                           Map<String, Object> genJson, Map<String, Object> originalJson) {
        all.addAll(failures);
    }

    @Override
    public void missingResources(Collection<DiffChecker.Resource> missingResources) {
    }

    @Override
    public void newResources(Collection<DiffChecker.Resource> newResources) {
    }

    @Override
    public void flush() {
    }
}