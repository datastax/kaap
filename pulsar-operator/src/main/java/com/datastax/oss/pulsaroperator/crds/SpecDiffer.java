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
package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import com.datastax.oss.pulsaroperator.common.json.JSONAssertComparator;
import com.datastax.oss.pulsaroperator.common.json.JSONComparator;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class SpecDiffer {

    private static final JSONComparator.Result WAS_NULL_RESULT = new JSONComparator.Result() {
        @Override
        public boolean areEquals() {
            return false;
        }

        @Override
        public List<JSONComparator.FieldComparisonDiff> diffs() {
            throw new IllegalStateException("one of the spec (or both) was null");
        }
    };

    private SpecDiffer() {
    }

    public static JSONComparator.Result generateDiff(String json1, String json2) {
        if (json1 == null && json2 == null) {
            return JSONComparator.RESULT_EQUALS;
        }
        if (json1 == null) {
            return WAS_NULL_RESULT;
        }
        if (json2 == null) {
            return WAS_NULL_RESULT;
        }
        return new JSONAssertComparator()
                .compare(json1, json2);

    }

    public static JSONComparator.Result generateDiff(Object spec1, Object spec2) {
        if (spec1 == null && spec2 == null) {
            return JSONComparator.RESULT_EQUALS;
        }
        if (spec1 == null) {
            return WAS_NULL_RESULT;
        }
        if (spec2 == null) {
            return WAS_NULL_RESULT;
        }
        final String expectedStr = SerializationUtil.writeAsJson(spec1);
        final String actualStr = SerializationUtil.writeAsJson(spec2);
        return generateDiff(expectedStr, actualStr);
    }

    public static JSONComparator.Result generateDiff(Object spec1, String json2) {
        if (spec1 == null && json2 == null) {
            return JSONComparator.RESULT_EQUALS;
        }
        if (spec1 == null) {
            return WAS_NULL_RESULT;
        }
        if (json2 == null) {
            return WAS_NULL_RESULT;
        }
        final String expectedStr = SerializationUtil.writeAsJson(spec1);
        return generateDiff(expectedStr, json2);
    }

    public static JSONComparator.Result generateDiff(String json1, Object spec2) {
        if (json1 == null && spec2 == null) {
            return JSONComparator.RESULT_EQUALS;
        }
        if (json1 == null) {
            return WAS_NULL_RESULT;
        }
        if (spec2 == null) {
            return WAS_NULL_RESULT;
        }
        final String actualStr = SerializationUtil.writeAsJson(spec2);
        return generateDiff(json1, actualStr);
    }


    public static void logDetailedSpecDiff(JSONComparator.Result diff) {
        logDetailedSpecDiff(diff, null, null);
    }

    public static void logDetailedSpecDiff(JSONComparator.Result diff, String currentAsJson, String newSpecAsJson) {
        if (currentAsJson != null || newSpecAsJson != null) {
            log.infof("logging detailed diff: \nwas: %s\nnow: %s", currentAsJson, newSpecAsJson);
        }
        for (JSONComparator.FieldComparisonDiff failure : diff.diffs()) {
            final String actualValue = failure.actual();
            final String completeField = failure.field();
            final String expectedValue = failure.expected();
            if (actualValue == null) {
                log.infof("""
                        was: '%s=%s', now removed
                        """.formatted(completeField, expectedValue));
            } else if (expectedValue == null) {
                log.infof("""
                        was empty, now: '%s=%s'
                        """.formatted(completeField, actualValue));
            } else {
                log.infof("""
                        '%s' value differs:
                            was: %s
                            now: %s
                        """.formatted(
                        completeField,
                        expectedValue,
                        actualValue
                ));
            }
        }
    }

}
