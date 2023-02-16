package com.datastax.oss.pulsaroperator.migrationtool.json;

import java.util.List;

public interface JSONComparator {

    record FieldComparisonFailure(String field, Object expected, Object actual) {
    }

    interface Result {
        boolean passed();

        List<FieldComparisonFailure> failures();
    }

    Result compare(String json1, String json2);
}
