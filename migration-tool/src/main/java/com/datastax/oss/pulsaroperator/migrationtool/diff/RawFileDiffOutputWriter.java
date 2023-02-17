package com.datastax.oss.pulsaroperator.migrationtool.diff;

import com.datastax.oss.pulsaroperator.migrationtool.json.JSONComparator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RawFileDiffOutputWriter extends BaseDiffOutputWriter {

    private final StringBuilder builder = new StringBuilder();
    private final Path outputFile;

    public RawFileDiffOutputWriter(Path outputFile) {
        this.outputFile = outputFile;
    }

    @Override
    public void diffOk(String fqname) {
        builder.append(fqname + ": OK\n");

    }


    @Override
    public void diffFailed(String fqname, List<JSONComparator.FieldComparisonFailure> failures,
                           Map<String, Object> genJson, Map<String, Object> originalJson) {
        builder.append(fqname + ": FAILED\n");
        super.diffFailed(fqname, failures, genJson, originalJson);
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
                    """.formatted(completeField, completeField, actualValue));
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
    @SneakyThrows
    public void flush() {
        Files.write(outputFile, builder.toString().getBytes(StandardCharsets.UTF_8));
        log.info("Exported diff to {}", outputFile.toAbsolutePath());
    }
}
