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
package com.datastax.oss.pulsaroperator.migrationtool;

import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.migrationtool.json.JSONAssertComparator;
import com.datastax.oss.pulsaroperator.migrationtool.json.JSONComparator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.BaseSpecGenerator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.kubernetes.api.model.HasMetadata;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiffChecker {

    public static void main(String[] args) {
        diffFromDirectory(new File(
                "/var/folders/0z/h11c9jxx76g640q6pc1984480000gp/T/tmp.vsX4bxPb/outputs/pulsaroperator-local-k3s"));
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @SneakyThrows
    public static void diffFromDirectory(File inputDirectory) {
        Collection<Map<String, Object>> existingResources = new ArrayList<>();
        Collection<Map<String, Object>> generatedResources = new ArrayList<>();
        log.info("checking files at {}", inputDirectory.getAbsolutePath());
        Files.list(inputDirectory.toPath())
                .filter(p -> p.toFile().getName().endsWith(".json"))
                .forEach(p -> {
                    final String filename = p.toFile().getName();
                    if (filename.startsWith("generated-")) {
                        try {
                            generatedResources.add(MAPPER.readValue(p.toFile(), Map.class));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (filename.startsWith("original-")) {
                        try {
                            existingResources.add(MAPPER.readValue(p.toFile(), Map.class));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
        DiffChecker diffChecker = new DiffChecker();
        diffChecker.checkDiffsFromMaps(existingResources, generatedResources);
    }


    private void checkDiffsFromMaps(
            Collection<Map<String, Object>> existingResources, Collection<Map<String, Object>> generatedResources) {

        checkDiffs(existingResources
                        .stream()
                        .map(this::mapToResource)
                        .collect(Collectors.toList()),
                generatedResources
                        .stream()
                        .map(this::mapToResource)
                        .collect(Collectors.toList())
        );
    }


    private void checkDiffsFromResources(
            Collection<HasMetadata> existingResources, Collection<HasMetadata> generatedResources) {

        checkDiffs(existingResources
                        .stream()
                        .map(this::hasMetadataToResource)
                        .collect(Collectors.toList()),
                generatedResources
                        .stream()
                        .map(this::hasMetadataToResource)
                        .collect(Collectors.toList())
        );
    }

    private Resource hasMetadataToResource(HasMetadata metadata) {
        return new Resource() {
            @Override
            public String getName() {
                return metadata.getMetadata().getName();
            }

            @Override
            public String getKind() {
                return metadata.getFullResourceName();
            }

            @Override
            @SneakyThrows
            public Map<String, Object> toMap() {
                return MAPPER.convertValue(metadata, Map.class);
            }
        };

    }

    private Resource mapToResource(Map<String, Object> map) {
        return new Resource() {
            @Override
            public String getName() {
                return ((Map<String, Object>) (map.get("metadata"))).get("name").toString();
            }

            @Override
            public String getKind() {
                return map.get("kind").toString();
            }

            @Override
            @SneakyThrows
            public Map<String, Object> toMap() {
                return map;
            }
        };

    }

    private interface Resource {
        String getName();

        String getKind();

        Map<String, Object> toMap();

    }

    @SneakyThrows
    private void checkDiffs(Collection<Resource> existingResources, Collection<Resource> generatedResources) {
        Map<String, String> results = new HashMap<>();
        for (Resource generatedResource : generatedResources) {
            final String name = generatedResource.getName();

            final Resource equivalent = findEquivalent(generatedResource, existingResources);
            if (equivalent == null) {
                reportDiff("generated resouce %s not found in existing resources".formatted(name));
                continue;
            }
            final String fqn = generatedResource.getKind() + "/" + name;

            log.info("converting generated resource {} to json", fqn);
            final Map<String, Object> genJson = toJson(generatedResource);
            log.info("converting original resource {} to json", fqn);
            final Map<String, Object> existingJson = toJson(equivalent);
            log.info("checking diff for {}", fqn);
            try {
                compareOrThrow(genJson, existingJson);
                log.info("{} OK!", fqn);

                results.put(fqn, "OK");
            } catch (AssertionError assertionError) {
                log.warn(assertionError.getMessage());
                results.put(fqn, assertionError.getMessage());
            }
        }
        String resultStr = "*** RESULTS ***\n\n";
        for (Map.Entry<String, String> stringStringEntry : results.entrySet()) {
            resultStr += stringStringEntry.getKey() + ": " + stringStringEntry.getValue() + "\n";
        }
        log.info(resultStr);
    }

    private void compareOrThrow(Map<String, Object> genJson, Map<String, Object> existingJson)
            throws JsonProcessingException {
        final String genStr = MAPPER.writeValueAsString(genJson);
        final String originalStr = MAPPER.writeValueAsString(existingJson);


        JSONComparator comparator = new JSONAssertComparator();
        final JSONComparator.Result result = comparator.compare(originalStr, genStr);
        if (result.passed()) {
            return;
        }

        StringBuilder err = new StringBuilder("Comparison failed: \n");
        result.failures()
                .stream()
                .sorted(Comparator.comparing(JSONComparator.FieldComparisonFailure::field))
                .map(field -> formatFieldComparisonFailure(field, genJson, existingJson))
                .forEach(err::append);
        throw new AssertionError(err.toString());
    }

    private String formatFieldComparisonFailure(JSONComparator.FieldComparisonFailure fieldFailure,
                                                Map<String, Object> genJson,
                                                Map<String, Object> originalJson) {
        final Object actual = fieldFailure.actual();
        final Object expected = fieldFailure.expected();
        final String field = fieldFailure.field();
        if (actual == null) {
            final String quotedExpected = "\"" + expected + "\"";
            return """
                    - expected: '%s.%s=%s' but none found
                    """.formatted(
                    field,
                    quotedExpected,
                    getValueByDotNotation(originalJson, "%s.%s".formatted(field,
                            quotedExpected))
            );
        }
        if (expected == null) {
            final Object quotedActual = "\"" + actual + "\"";
            return """
                    - unexpected: '%s.%s=%s'
                    """.formatted(
                    field,
                    quotedActual,
                    getValueByDotNotation(genJson, "%s.%s".formatted(field,
                            quotedActual))
            );
        }
        return """
                - '%s' value differs:
                    Original:  %s
                    Generated: %s
                """.formatted(
                field,
                expected,
                actual
        );
    }

    private Resource findEquivalent(Resource as, Collection<Resource> from) {
        for (Resource hasMetadata : from) {
            if (!Objects.equals(as.getName(), hasMetadata.getName())) {
                continue;
            }

            if (Objects.equals(as.getKind(), hasMetadata.getKind())) {
                return hasMetadata;
            }
        }
        return null;
    }


    private void reportDiff(String message) {
        log.error(message);
    }

    @SneakyThrows
    private static Map<String, Object> toJson(Resource resource) {
        final Map<String, Object> asJson = resource.toMap();
        adjustResource(asJson);
        return asJson;
    }

    private static void adjustResource(Map<String, Object> exiJson) {
        adjustMetadata(getPropAsMap(exiJson, "metadata"));
        adjustSpec(getPropAsMap(exiJson, "spec"));
        adjustConfigMapData(exiJson);
        exiJson.remove("status");
    }

    private static void adjustMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        metadata.remove("managedFields");
        metadata.remove("generation");
        metadata.remove("creationTimestamp");
        metadata.remove("resourceVersion");
        metadata.remove("uid");
        metadata.remove("revisionHistoryLimit");
        metadata.remove("namespace");
        final Map<String, Object> annotations = getPropAsMap(metadata, "annotations");
        if (annotations != null) {
            BaseSpecGenerator.HELM_ANNOTATIONS.forEach(annotations::remove);
            if (annotations.isEmpty()) {
                metadata.remove("annotations");
            }
        }
    }

    private static void adjustSpec(Map<String, Object> spec) {
        if (spec == null) {
            return;
        }
        spec.remove("revisionHistoryLimit");
        adjustSpecSelector(spec);
        adjustTemplate(getPropAsMap(spec, "template"));
        adjustVolumeClaimTemplates(spec);
        removeIfMatch(spec, "clusterIPs", List.of("None"));
        removeIfMatch(spec, "internalTrafficPolicy", "Cluster");
        removeIfMatch(spec, "ipFamilies", List.of("IPv4"));
        removeIfMatch(spec, "ipFamilyPolicy", "SingleStack");
        removeIfMatch(spec, "sessionAffinity", "None");
        removeIfMatch(spec, "type", "ClusterIP");
        removeIfMatch(spec, "type", "ClusterIP");
        getPropAsList(spec, "ports").forEach(port -> {
            // https://kubernetes.io/docs/reference/networking/service-protocols/
            removeIfMatch(port, "protocol", "TCP");
            removeIfMatch(port, "targetPort", port.get("port"));
        });
        final Map<String, Object> selector = getPropAsMap(spec, "selector");
        BaseSpecGenerator.HELM_LABELS.forEach(selector::remove);

    }

    private static void adjustConfigMapData(Map<String, Object> spec) {
        if (spec == null) {
            return;
        }
        spec.put("data", BaseResourcesFactory.handleConfigPulsarPrefix(getPropAsMap(spec, "data")));
    }

    private static void adjustSpecSelector(Map<String, Object> spec) {
        final Map<String, Object> selector = getPropAsMap(spec, "selector");
        final Map<String, Object> matchLabels = getPropAsMap(selector, "matchLabels");
        BaseSpecGenerator.HELM_LABELS.forEach(matchLabels::remove);
    }

    private static void adjustVolumeClaimTemplates(Map<String, Object> spec) {
        getPropAsList(spec, "volumeClaimTemplates").forEach(vct -> {
            vct.remove("status");
            final Map<String, Object> vctSpec = getPropAsMap(vct, "spec");
            // https://kubernetes.io/docs/concepts/storage/persistent-volumes/#volume-mode
            removeIfMatch(vctSpec, "volumeMode", "Filesystem");
        });
    }

    private static void adjustTemplate(Map<String, Object> template) {
        if (template == null) {
            return;
        }
        adjustMetadata(getPropAsMap(template, "metadata"));
        adjustTemplateSpec(getPropAsMap(template, "spec"));
    }

    private static void adjustTemplateSpec(Map<String, Object> templateSpec) {
        if (templateSpec == null) {
            return;
        }
        // https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/#pod-s-dns-policy
        removeIfMatch(templateSpec, "dnsPolicy", "ClusterFirst");
        // https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#restart-policy
        removeIfMatch(templateSpec, "restartPolicy", "Always");
        // https://kubernetes.io/docs/tasks/extend-kubernetes/configure-multiple-schedulers/#specify-schedulers-for-pods
        removeIfMatch(templateSpec, "schedulerName", "default-scheduler");
        adjustContainers(getPropAsList(templateSpec, "containers"));
    }

    private static void adjustContainers(List<Map<String, Object>> containers) {
        if (containers == null) {
            return;
        }
        for (Map<String, Object> container : containers) {
            adjustContainer(container);
        }

    }

    private static void adjustContainer(Map<String, Object> container) {
        if (container == null) {
            return;
        }
        // https://kubernetes.io/docs/tasks/debug/debug-application/determine-reason-pod-failure/#customizing-the-termination-message
        removeIfMatch(container, "terminationMessagePath", "/dev/termination-log");
        // https://kubernetes.io/docs/tasks/debug/debug-application/determine-reason-pod-failure/#customizing-the-termination-message
        removeIfMatch(container, "terminationMessagePolicy", "File");

        adjustContainerProbes(container);

        getPropAsList(container, "ports").forEach(port -> {
            // https://kubernetes.io/docs/reference/networking/service-protocols/
            removeIfMatch(port, "protocol", "TCP");
        });
    }

    private static void adjustContainerProbes(Map<String, Object> container) {
        // https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/#configure-probes
        final Map<String, Object> livenessProbe = getPropAsMap(container, "livenessProbe");
        if (livenessProbe != null) {
            removeIfMatch(livenessProbe, "failureThreshold", 3);
            removeIfMatch(livenessProbe, "successThreshold", 1);
            adjustHttpGetProbe(livenessProbe);
        }
        final Map<String, Object> readinessProbe = getPropAsMap(container, "readinessProbe");
        if (readinessProbe != null) {
            removeIfMatch(readinessProbe, "failureThreshold", 3);
            removeIfMatch(readinessProbe, "successThreshold", 1);
            adjustHttpGetProbe(readinessProbe);
        }
    }

    private static void adjustHttpGetProbe(Map<String, Object> probe) {
        if (probe == null) {
            return;
        }
        final Map<String, Object> httpGet = getPropAsMap(probe, "httpGet");
        if (httpGet != null) {
            removeIfMatch(httpGet, "scheme", "HTTP");
        }
    }

    private static void removeIfMatch(Map<String, Object> map, String prop, Object matchValue) {
        final Object value = map.get(prop);
        if (value == null) {
            return;
        }
        if (Objects.equals(value, matchValue)) {
            map.remove(prop);
        }
    }

    private static Map<String, Object> getPropAsMap(Map<String, Object> exiJson, String name) {
        return exiJson.get(name) == null ? new HashMap<>() : (Map<String, Object>) exiJson.get(name);
    }

    private static List<Map<String, Object>> getPropAsList(Map<String, Object> exiJson, String name) {
        return exiJson.get(name) == null ? Collections.emptyList() : (List<Map<String, Object>>) exiJson.get(name);
    }

    private static String getValueByDotNotation(final Map<String, Object> map, String key) {
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



