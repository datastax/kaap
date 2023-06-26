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
package com.datastax.oss.k8saap.crds;

import com.datastax.oss.k8saap.common.json.JSONComparator;
import com.datastax.oss.k8saap.crds.configs.RackConfig;
import java.util.Map;
import org.testng.annotations.Test;

public class SpecDifferTest {

    @Test
    public void testDiffMapKeysOrder() {
        assertNoDiffs(GlobalSpec.builder()
                        .racks(Map.of("rack1", RackConfig.builder().build(), "rack2", RackConfig.builder().build()))
                        .build(),
                GlobalSpec.builder()
                        .racks(Map.of("rack2", RackConfig.builder().build(), "rack1", RackConfig.builder().build()))
                        .build()
        );
    }

    @Test
    public void testDiffNull() {
        assertNoDiffs(null, null);
        assertDiffs(Map.of(), null);
        assertDiffs(null, Map.of());
    }

    private static void assertNoDiffs(Object spec1, Object spec2) {
        final JSONComparator.Result result = SpecDiffer.generateDiff(spec1, spec2);
        if (!result.areEquals()) {
            throw new AssertionError("expected no diffs but got: " + result.diffs());
        }
    }

    private static void assertDiffs(Object spec1, Object spec2) {
        final JSONComparator.Result result = SpecDiffer.generateDiff(spec1, spec2);
        if (result.areEquals()) {
            throw new AssertionError("expected diffs but considered equal");
        }
    }

}