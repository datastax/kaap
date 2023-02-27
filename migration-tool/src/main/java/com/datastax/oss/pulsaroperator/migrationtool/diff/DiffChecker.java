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
package com.datastax.oss.pulsaroperator.migrationtool.diff;

import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import com.datastax.oss.pulsaroperator.controllers.BaseResourcesFactory;
import com.datastax.oss.pulsaroperator.migrationtool.SpecGenerator;
import com.datastax.oss.pulsaroperator.migrationtool.json.JSONAssertComparator;
import com.datastax.oss.pulsaroperator.migrationtool.json.JSONComparator;
import com.datastax.oss.pulsaroperator.migrationtool.specs.BaseSpecGenerator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.kubernetes.api.model.HasMetadata;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class DiffChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @SneakyThrows
    public static void diffFromDirectory(File inputDirectory) {
        log.info("checking files at {}", inputDirectory.getAbsolutePath());
        Collection<Pair<File, Map<String, Object>>> originalResources =
                readResourcesDirectory(SpecGenerator.getOriginalResourcesFileFromDir(inputDirectory));
        Collection<Pair<File, Map<String, Object>>> generatedResources =
                readResourcesDirectory(SpecGenerator.getGeneratedResourcesFileFromDir(inputDirectory));

        File pulsarClusterCrd = SpecGenerator.getGeneratedPulsarClusterJSONFileFromDir(inputDirectory).toFile();
        DiffChecker diffChecker = new DiffChecker(new MultiDiffOutputWriters(
                List.of(
                        new HtmlFileDiffOutputWriter(Path.of(inputDirectory.getAbsolutePath(), "diff.html"),
                                pulsarClusterCrd),
                        new RawFileDiffOutputWriter(Path.of(inputDirectory.getAbsolutePath(), "diff.txt")),
                        new ConsoleDiffOutputWriter()
                )
        ));
        diffChecker.checkDiffsFromMaps(originalResources, generatedResources);
    }

    public static List<Pair<File, Map<String, Object>>> readResourcesDirectory(List<Path> inputDirectory) {
        return inputDirectory
                .stream()
                .map(f -> Pair.of(f.toFile(), readJson(f.toFile())))
                .collect(Collectors.toList());
    }

    @SneakyThrows
    private static Map<String, Object> readJson(File file) {
        return MAPPER.readValue(file, Map.class);
    }

    private final DiffOutputWriter diffOutputWriter;

    public DiffChecker(DiffOutputWriter diffOutputWriter) {
        this.diffOutputWriter = diffOutputWriter;
    }

    public void checkDiffsFromMaps(
            Collection<Pair<File, Map<String, Object>>> existingResources,
            Collection<Pair<File, Map<String, Object>>> generatedResources) {

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
            public String getFullQualifedName() {
                return getName() + "/" + getKind();
            }

            @Override
            @SneakyThrows
            public Map<String, Object> toMap() {
                return MAPPER.convertValue(metadata, Map.class);
            }

            @Override
            public File getFileReference() {
                return null;
            }
        };

    }

    private Resource mapToResource(Pair<File, Map<String, Object>> map) {
        return new Resource() {
            @Override
            public String getName() {
                return ((Map<String, Object>) (map.getRight().get("metadata"))).get("name").toString();
            }

            @Override
            public String getKind() {
                return map.getRight().get("kind").toString();
            }

            @Override
            public String getFullQualifedName() {
                return getName() + "/" + getKind();
            }

            @Override
            @SneakyThrows
            public Map<String, Object> toMap() {
                return map.getRight();
            }

            @Override
            public File getFileReference() {
                return map.getLeft();
            }
        };

    }

    public interface Resource {
        String getName();

        String getKind();

        String getFullQualifedName();

        Map<String, Object> toMap();

        File getFileReference();

    }

    @SneakyThrows
    private void checkDiffs(Collection<Resource> existingResources,
                            Collection<Resource> generatedResources) {
        final List<Resource> sortedGenResources = generatedResources.stream()
                .sorted(Comparator.comparing(Resource::getName).thenComparing(Resource::getKind))
                .collect(Collectors.toList());
        final List<Resource> newGenResources = new ArrayList<>();
        for (Resource generatedResource : sortedGenResources) {
            final Resource original = findEquivalent(generatedResource, existingResources);
            if (original == null) {
                newGenResources.add(generatedResource);
                continue;
            }
            existingResources.remove(original);
            compare(generatedResource, original);
        }

        diffOutputWriter.missingResources(existingResources);
        diffOutputWriter.newResources(newGenResources);
        diffOutputWriter.flush();
    }

    private void compare(Resource generated, Resource original)
            throws JsonProcessingException {
        final String fqn = generated.getFullQualifedName();
        log.info("converting generated resource {} to json", fqn);
        final Map<String, Object> genJson = toJson(generated);
        log.info("converting original resource {} to json", fqn);
        final Map<String, Object> existingJson = toJson(original);
        log.info("checking diff for {}", fqn);
        final String genStr = MAPPER.writeValueAsString(genJson);
        final String originalStr = MAPPER.writeValueAsString(existingJson);


        final Pair<Resource, Resource> resources = Pair.of(generated, original);

        JSONComparator comparator = new JSONAssertComparator();
        final JSONComparator.Result result = comparator.compare(originalStr, genStr);
        if (result.passed()) {
            diffOutputWriter.diffOk(resources);
            return;
        }

        final List<JSONComparator.FieldComparisonFailure> failures = result.failures()
                .stream()
                .sorted(Comparator.comparing(JSONComparator.FieldComparisonFailure::field))
                .collect(Collectors.toList());

        diffOutputWriter.diffFailed(resources, failures, genJson, existingJson);
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
        metadata.remove("finalizers");
        final Map<String, Object> annotations = getPropAsMap(metadata, "annotations");
        if (annotations != null) {
            BaseSpecGenerator.HELM_ANNOTATIONS.forEach(annotations::remove);
            annotations.remove("deployment.kubernetes.io/revision");
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
        removeIfMatch(spec, "externalTrafficPolicy", "Cluster");
        removeIfMatch(spec, "ipFamilies", List.of("IPv4"));
        removeIfMatch(spec, "ipFamilyPolicy", "SingleStack");
        removeIfMatch(spec, "sessionAffinity", "None");
        removeIfMatch(spec, "type", "ClusterIP");
        removeIfMatch(spec, "allocateLoadBalancerNodePorts", true);
        sortAndReplaceList(spec, "ports", port -> {
            // https://kubernetes.io/docs/reference/networking/service-protocols/
            removeIfMatch(port, "protocol", "TCP");
            removeIfMatch(port, "targetPort", port.get("port"));
            port.remove("nodePort");

            return port;
        }, Comparator.comparing(c -> (Integer) c.get("port")));
        final Map<String, Object> selector = getPropAsMap(spec, "selector");
        BaseSpecGenerator.HELM_LABELS.forEach(selector::remove);

        // https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#progress-deadline-seconds
        removeIfMatch(spec, "progressDeadlineSeconds", 600);
        // https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#strategy
        removeIfMatch(spec, "strategy", Map.of("rollingUpdate",
                Map.of("maxSurge", "25%", "maxUnavailable", "25%"), "type", "RollingUpdate"));
    }

    private static void adjustConfigMapData(Map<String, Object> spec) {
        if (spec == null) {
            return;
        }
        final Map<String, Object> data = getPropAsMap(spec, "data");
        if (data != null) {
            final String functionsWorkerYml = (String) data.get("functions_worker.yml");
            if (functionsWorkerYml != null) {
                // deserialize to better visual comparison
                data.put("functions_worker.yml", SerializationUtil.readYaml(functionsWorkerYml, Map.class));
            }
        }
        spec.put("data", BaseResourcesFactory.handleConfigPulsarPrefix(data));
    }

    private static void adjustSpecSelector(Map<String, Object> spec) {
        final Map<String, Object> selector = getPropAsMap(spec, "selector");
        final Map<String, Object> matchLabels = getPropAsMap(selector, "matchLabels");
        BaseSpecGenerator.HELM_LABELS.forEach(matchLabels::remove);
    }

    private static void adjustVolumeClaimTemplates(Map<String, Object> spec) {
        sortAndReplaceList(spec, "volumeClaimTemplates", vct -> {
            vct.remove("status");
            final Map<String, Object> vctSpec = getPropAsMap(vct, "spec");
            // https://kubernetes.io/docs/concepts/storage/persistent-volumes/#volume-mode
            removeIfMatch(vctSpec, "volumeMode", "Filesystem");
            return vct;
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
        removeIfMatch(templateSpec, "securityContext", Map.of());
        removeIfMatch(templateSpec, "serviceAccount", templateSpec.get("serviceAccountName"));
        sortAndReplaceList(templateSpec, "volumes", v -> {
            final Map<String, Object> secret = getPropAsMap(v, "secret");
            removeIfMatch(secret, "defaultMode", 420);
            return v;
        });
        sortAndReplaceList(templateSpec, "containers", container -> {
            adjustContainer(container);
            return container;
        }, Comparator.comparing(c -> (String) c.get("name")));
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

        sortAndReplaceList(container, "ports", port -> {
            // https://kubernetes.io/docs/reference/networking/service-protocols/
            removeIfMatch(port, "protocol", "TCP");
            return port;
        }, Comparator.comparing(c -> (Integer) c.get("containerPort")));

        sortAndReplaceList(container, "volumeMounts", Function.identity());

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

    private static void sortAndReplaceList(Map<String, Object> exiJson,
                                           String name,
                                           Function<Map<String, Object>, Map<String, Object>> mapper) {
        sortAndReplaceList(exiJson, name, mapper,
                Comparator.comparing(v -> SerializationUtil.writeAsJson(v)));
    }


    private static void sortAndReplaceList(Map<String, Object> exiJson,
                                           String name,
                                           Function<Map<String, Object>, Map<String, Object>> mapper,
                                           Comparator<Map<String, Object>> comparator) {
        final var sortedList = getPropAsList(exiJson, name)
                .stream()
                .map(mapper)
                .sorted(comparator)
                .collect(Collectors.toList());
        if (!sortedList.isEmpty()) {
            exiJson.put(name, sortedList);
        }
    }

    private static List<Map<String, Object>> getPropAsList(Map<String, Object> exiJson, String name) {
        return exiJson.get(name) == null ? Collections.emptyList() : (List<Map<String, Object>>) exiJson.get(name);
    }
}
