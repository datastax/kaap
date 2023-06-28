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
package com.datastax.oss.kaap.migrationtool.diff;

import com.datastax.oss.kaap.common.json.JSONComparator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class HtmlFileDiffOutputWriter extends BaseDiffOutputWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final StringBuilder builder = new StringBuilder();
    private final Path outputFile;
    private boolean ulOpen;

    public HtmlFileDiffOutputWriter(Path outputFile, File pulsarClusterCrd) {
        this.outputFile = outputFile;
        builder.append("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset='utf-8'>
                    <meta http-equiv='X-UA-Compatible' content='IE=edge'>
                    <title>Diff</title>
                    <meta name='viewport' content='width=device-width, initial-scale=1'>
                    <style>
                    body {
                        font-family: Verdana,sans-serif;
                        line-height: 1.5;
                    }
                    code {
                      display: inline-block;
                      white-space: pre-wrap
                    }
                    li {
                        margin-top: 1.2em;
                        margin-bottom: 1.2em;
                        font-size: 15px;
                    }
                    .resource-link {
                        font-size: 10px;
                        line-height: 1.2;
                        margin-top: 0.75em;
                        margin-bottom: 0.75em;
                    }
                    details {
                        padding: 0.5em 0.5em 0;
                    }
                    summary {
                        cursor: pointer;
                        font-weight: bold;
                        margin: -0.5em -0.5em 0;
                        padding: 0.5em;
                    }
                    details[open] {
                        padding: 0.5em;
                    }
                    details[open] summary {
                        margin-bottom: 0.5em;
                    }
                    </style>
                </head>
                <body>
                """);
        if (pulsarClusterCrd != null) {
            addResourceLink("Generated PulsarCluster CRD", pulsarClusterCrd);
        }
    }

    @Override
    public void diffOk(Pair<DiffChecker.Resource, DiffChecker.Resource> resources) {
        final String fqName = resources.getLeft().getFullQualifedName();
        closeULIfOpen();
        builder.append("<h3>%s: OK</h3>\n".formatted(fqName));
        appendResourceLinks(resources);
    }

    private void appendResourceLinks(Pair<DiffChecker.Resource, DiffChecker.Resource> resources) {
        if (resources.getLeft().getFileReference() != null) {
            addResourceLink("Generated resource", resources.getLeft().getFileReference());
            addResourceLink("Original resource", resources.getRight().getFileReference());
        }
    }

    private void addResourceLink(String text, File fileRef) {
        builder.append(genResourceLink(text, fileRef));
    }

    private String genResourceLink(String text, File fileRef) {
        return "<p class=\"resource-link\"><a href=\"%s\">%s</a></p>".formatted(
                fileRef.getAbsolutePath(), text);
    }


    @Override
    public void diffFailed(Pair<DiffChecker.Resource, DiffChecker.Resource> resources,
                           List<JSONComparator.FieldComparisonDiff> failures,
                           Map<String, Object> genJson, Map<String, Object> originalJson) {
        final String fqName = resources.getLeft().getFullQualifedName();
        closeULIfOpen();
        builder.append("<h3>%s: FAILED</h3>\n".formatted(fqName));
        appendResourceLinks(resources);
        builder.append("<ul>");
        ulOpen = true;
        super.diffFailed(resources, failures, genJson, originalJson);
    }


    @Override
    protected void formatFailure(String completeField, String expectedValue, String actualValue) {

        if (actualValue == null) {
            builder.append("""
                    <li>expected but none found: <b>%s</b> - <code>%s</code></li>
                    """.formatted(completeField, prettifyValue(expectedValue)));
        } else if (expectedValue == null) {
            builder.append("""
                    <li>unexpected: <b>%s</b> - <code>%s</code></li>
                    """.formatted(completeField, prettifyValue(actualValue)));
        } else {
            builder.append("""
                    <li><b>%s</b> value differs:
                    <br>
                    <details><summary>Original</summary> <code>%s</code></details>
                    <details><summary>Generated</summary> <code>%s</code></details>
                    </li>
                    """.formatted(
                    completeField,
                    prettifyValue(expectedValue),
                    prettifyValue(actualValue)
            ));
        }
    }

    private static String prettifyValue(String value) {
        if (value == null) {
            return null;
        }
        try {
            final Class<?> clazz;
            if (value.startsWith("[")) {
                clazz = List.class;
            } else if (value.startsWith("{")) {
                clazz = Map.class;
            } else {
                return value;
            }
            return MAPPER.writeValueAsString(MAPPER.readValue(value, clazz));
        } catch (JsonProcessingException e) {
            return value;
        }
    }

    @Override
    public void missingResources(Collection<DiffChecker.Resource> missingResources) {
        closeULIfOpen();
        addResourcesRawList(missingResources, "Untouched resources");
    }

    @Override
    public void newResources(Collection<DiffChecker.Resource> newResources) {
        closeULIfOpen();
        addResourcesRawList(newResources, "New resources");
    }

    private void addResourcesRawList(Collection<DiffChecker.Resource> resources, String title) {
        if (resources.isEmpty()) {
            return;
        }
        builder.append("<h3>%s</h3>\n".formatted(title));

        final Map<String, List<DiffChecker.Resource>> byKind =
                resources.stream().collect(Collectors.groupingBy(r -> r.getKind()));

        for (Map.Entry<String, List<DiffChecker.Resource>> kind : byKind.entrySet()) {
            builder.append("<h4>%s</h4>\n".formatted(kind.getKey()));
            for (DiffChecker.Resource resource : kind.getValue().stream().sorted(Comparator.comparing(s -> s.getName()))
                    .collect(Collectors.toList())) {
                builder.append("%s\n".formatted(
                        genResourceLink(resource.getName(), resource.getFileReference())));
            }
        }
    }

    private void closeULIfOpen() {
        if (ulOpen) {
            builder.append("</ul>\n");
            ulOpen = false;
        }
    }

    @Override
    @SneakyThrows
    public void flush() {
        builder.append("""
                </body>
                </html>
                """);
        Files.write(outputFile, builder.toString().getBytes(StandardCharsets.UTF_8));
        log.info("Exported HTML diff to {}", outputFile.toAbsolutePath());
    }
}
