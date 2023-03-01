package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.common.json.JSONComparator;
import com.datastax.oss.pulsaroperator.crds.configs.RackConfig;
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