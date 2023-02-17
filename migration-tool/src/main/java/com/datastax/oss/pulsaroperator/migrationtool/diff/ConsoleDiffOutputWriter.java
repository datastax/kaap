package com.datastax.oss.pulsaroperator.migrationtool.diff;

import com.datastax.oss.pulsaroperator.migrationtool.json.JSONComparator;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsoleDiffOutputWriter extends BaseDiffOutputWriter {


    @Override
    public void diffOk(String fqname) {
        log.info("{}: OK", fqname);

    }

    @Override
    public void diffFailed(String fqname, List<JSONComparator.FieldComparisonFailure> failures,
                           Map<String, Object> genJson, Map<String, Object> originalJson) {
        log.info("{}: FAILED", fqname);
        super.diffFailed(fqname, failures, genJson, originalJson);
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
                    """.formatted(completeField, completeField, actualValue));
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
    public void flush() {
    }
}
