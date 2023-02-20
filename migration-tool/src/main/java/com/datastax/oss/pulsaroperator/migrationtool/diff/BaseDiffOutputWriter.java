package com.datastax.oss.pulsaroperator.migrationtool.diff;

import com.datastax.oss.pulsaroperator.migrationtool.json.JSONComparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

public abstract class BaseDiffOutputWriter implements DiffOutputWriter {

    protected abstract void formatFailure(String complete, String expectedValue, String actualValue);

    @Override
    public void diffFailed(Pair<DiffChecker.Resource, DiffChecker.Resource> resources, List<JSONComparator.FieldComparisonFailure> failures,
                           Map<String, Object> genJson, Map<String, Object> originalJson) {

        for (JSONComparator.FieldComparisonFailure fieldFailure : failures) {
            final Object actual = fieldFailure.actual();
            final Object expected = fieldFailure.expected();
            final String field = fieldFailure.field();

            if (expected == null && actual == null) {
                throw new IllegalStateException();
            }

            if (actual == null) {
                final String quotedExpected = "\"" + expected + "\"";
                final String completeField = "%s.%s".formatted(field, quotedExpected);
                formatFailure(completeField, getValueByDotNotation(originalJson, completeField), null);
            } else if (expected == null) {
                final Object quotedActual = "\"" + actual + "\"";
                final String completeField = "%s.%s".formatted(field, quotedActual);
                formatFailure(completeField, null, getValueByDotNotation(genJson, completeField));
            } else {
                formatFailure(field, expected.toString(), actual.toString());
            }
        }

    }


    private static String getValueByDotNotation(final Map<String, Object> map, String key) {
        if (key.startsWith("data.")) {
            // ConfigMap data, need to handle nested maps with key with dots
            final Map<String, Object> data = (Map<String, Object>) map.get("data");
            if (data != null) {
                for (String dataKey : data.keySet()) {
                    key = key.replace("data." + dataKey, "data.\"" + dataKey + "\"");
                }
            }
        }
        List<String> words = new ArrayList<>();
        String currentWord = "";
        boolean openQuote = false;
        for (char c : key.toCharArray()) {
            if (c == '"') {
                openQuote = !openQuote;
                continue;
            }
            if (c == '.') {
                if (currentWord.isEmpty()) {
                    throw new IllegalArgumentException("invalid key: " + key);
                }
                if (!openQuote) {
                    words.add(currentWord);
                    currentWord = "";
                    continue;
                }
            }
            currentWord += c;
        }
        words.add(currentWord);
        Map<String, Object> currentMap = map;
        for (int i = 0; i < words.size() - 1; i++) {
            final String word = words.get(i);
            if (word.endsWith("]")) {
                int index = Integer.parseInt(word.substring(word.indexOf("[") + 1, word.indexOf("]")));
                final List<Object> list = (List<Object>) currentMap.get(word.replace("[" + index + "]", ""));
                currentMap = (Map<String, Object>) list.get(index);
            } else {
                currentMap = (Map<String, Object>) currentMap.get(word);
            }
        }

        return currentMap.get(words.get(words.size() - 1)).toString();
    }
}
