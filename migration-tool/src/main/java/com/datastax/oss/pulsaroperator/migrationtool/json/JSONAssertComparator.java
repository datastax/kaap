package com.datastax.oss.pulsaroperator.migrationtool.json;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.JSONCompareResult;
import org.skyscreamer.jsonassert.JSONParser;
import org.skyscreamer.jsonassert.comparator.DefaultComparator;

public class JSONAssertComparator implements JSONComparator {

    @Override
    @SneakyThrows
    public Result compare(String expectedStr, String actualStr) {

        final DefaultComparatorNoGenericFailures comparator =
                new DefaultComparatorNoGenericFailures();

        Object expected = JSONParser.parseJSON(expectedStr);
        Object actual = JSONParser.parseJSON(actualStr);
        final JSONCompareResult jsonCompareResult = new NoGenericFailuresCompareResult();
        if ((expected instanceof JSONObject) && (actual instanceof JSONObject)) {
            comparator.compareJSON("", (JSONObject) expected, (JSONObject) actual, jsonCompareResult);
        } else if ((expected instanceof JSONArray) && (actual instanceof JSONArray)) {
            comparator.compareJSONArray("", (JSONArray) expected, (JSONArray) actual, jsonCompareResult);
        } else {
            throw new IllegalArgumentException();
        }

        return new ResultWrapper(jsonCompareResult);
    }

    private static class NoGenericFailuresCompareResult extends JSONCompareResult {
        int lastFailCallNumOfFailures = 0;

        @Override
        public void fail(String message) {
            int currentFieldFailures =
                    getFieldFailures().size() + getFieldUnexpected().size() + getFieldMissing().size();
            if (currentFieldFailures == lastFailCallNumOfFailures) {
                throw new IllegalArgumentException(
                        "JSONCompareResult.fail() should not be called without the field details");
            }
            lastFailCallNumOfFailures++;
            super.fail(message);

        }
    }


    private static class DefaultComparatorNoGenericFailures extends DefaultComparator {
        public DefaultComparatorNoGenericFailures() {
            super(JSONCompareMode.STRICT_ORDER);
        }

        @Override
        public void compareJSONArray(String prefix, JSONArray expected, JSONArray actual,
                                     JSONCompareResult result) throws JSONException {
            if (expected.length() != actual.length()) {
                result.fail(prefix, expected, actual);
                return;
            }
            super.compareJSONArray(prefix, expected, actual, result);
        }
    }

    private static class ResultWrapper implements Result {
        private final JSONCompareResult jsonCompareResult;

        public ResultWrapper(JSONCompareResult jsonCompareResult) {
            this.jsonCompareResult = jsonCompareResult;
        }

        @Override
        public boolean passed() {
            return jsonCompareResult.passed();
        }

        @Override
        public List<FieldComparisonFailure> failures() {
            if (jsonCompareResult.passed()) {
                return Collections.emptyList();
            }

            return Stream.of(jsonCompareResult.getFieldFailures(), jsonCompareResult.getFieldMissing(),
                            jsonCompareResult.getFieldUnexpected())
                    .flatMap(Collection::stream)
                    .map(f -> new FieldComparisonFailure(f.getField(), f.getExpected(),
                            f.getActual()))
                    .collect(Collectors.toList());
        }
    }
}
