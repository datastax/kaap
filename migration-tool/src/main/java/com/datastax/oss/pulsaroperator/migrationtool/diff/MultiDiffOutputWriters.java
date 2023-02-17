package com.datastax.oss.pulsaroperator.migrationtool.diff;

import com.datastax.oss.pulsaroperator.migrationtool.json.JSONComparator;
import java.util.List;
import java.util.Map;

public class MultiDiffOutputWriters implements DiffChecker.DiffOutputWriter {
    private final List<DiffChecker.DiffOutputWriter> writers;

    public MultiDiffOutputWriters(
            List<DiffChecker.DiffOutputWriter> writers) {
        this.writers = writers;
    }

    @Override
    public void diffOk(String fqname) {
        writers.forEach(w -> w.diffOk(fqname));
    }

    @Override
    public void diffFailed(String fqname, List<JSONComparator.FieldComparisonFailure> fieldFailures,
                           Map<String, Object> genJson, Map<String, Object> originalJson) {
        writers.forEach(w -> w.diffFailed(fqname, fieldFailures, genJson, originalJson));
    }

    @Override
    public void flush() {
        writers.forEach(DiffChecker.DiffOutputWriter::flush);
    }
}
