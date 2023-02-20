package com.datastax.oss.pulsaroperator.migrationtool.diff;

import com.datastax.oss.pulsaroperator.migrationtool.json.JSONComparator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

public interface DiffOutputWriter {

    void diffOk(Pair<DiffChecker.Resource, DiffChecker.Resource> resources);

    void diffFailed(Pair<DiffChecker.Resource, DiffChecker.Resource> resources, List<JSONComparator.FieldComparisonFailure> fieldFailures,
                    Map<String, Object> genJson,
                    Map<String, Object> originalJson);

    void flush();

}
