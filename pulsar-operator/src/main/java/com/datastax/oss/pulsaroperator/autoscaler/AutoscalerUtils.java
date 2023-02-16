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
package com.datastax.oss.pulsaroperator.autoscaler;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.compress.utils.IOUtils;

@JBossLog
public class AutoscalerUtils {

    public static boolean isStsReadyToScale(KubernetesClient client, Long stabilizationWindowMs,
                                            String namespace, String statefulsetName,
                                            Map<String, String> podSelector,
                                            int currentExpectedReplicas) {
        final StatefulSet statefulSet = client.apps().statefulSets()
                .inNamespace(namespace)
                .withName(statefulsetName)
                .get();
        if (statefulSet == null) {
            log.warnf("Statefulset not found %s", statefulsetName);
            return false;
        }

        final int readyReplicas = Objects.requireNonNullElse(statefulSet.getStatus().getReadyReplicas(), 0);
        if (readyReplicas != currentExpectedReplicas) {
            log.infof("Not all sts replicas ready for %s, expected %d, got %d",
                    statefulsetName,
                    currentExpectedReplicas,
                    readyReplicas);
            return false;
        }

        final PodList allTargetPods = client.pods()
                .inNamespace(namespace)
                .withLabels(podSelector)
                .list();

        if (allTargetPods.getItems().size() != currentExpectedReplicas) {
            log.infof("Sts %s not in ready state", statefulsetName);
            return false;
        }
        final Instant now = Instant.now();
        Instant maxStartTime = now.minusMillis(stabilizationWindowMs);
        for (Pod pod : allTargetPods.getItems()) {
            final ContainerStatus containerStatus = pod.getStatus().getContainerStatuses().get(0);
            final Boolean ready = containerStatus.getReady();
            if (ready != null && !ready) {
                log.infof("Pod %s is not ready", pod.getMetadata().getName());
                return false;
            }

            final Instant podStartTime = Instant.parse(pod.getStatus().getStartTime());
            if (podStartTime.isAfter(maxStartTime)) {
                log.infof("Pod %s age is %d seconds, waiting at least %d s (stabilizationWindowMs)",
                        pod.getMetadata().getName(),
                        Duration.between(podStartTime, now).getSeconds(),
                        stabilizationWindowMs / 1000);
                return false;
            }
        }
        return true;
    }

    public static CompletableFuture<String> execInPod(KubernetesClient client,
                                                      String namespace, String podName, String containerName,
                                                      String... cmds) {

        final String cmd = Arrays.stream(cmds).collect(Collectors.joining(" "));
        if (log.isDebugEnabled()) {
            log.debugf("Executing in pod %s: %s",
                    containerName == null ? podName : podName + "/" + containerName, cmd);
        }
        final AtomicBoolean completed = new AtomicBoolean(false);
        final CompletableFuture<String> response = new CompletableFuture<>();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream error = new ByteArrayOutputStream();

        final ExecListener listener = new ExecListener() {
            @Override
            public void onOpen() {
                if (log.isDebugEnabled()) {
                    log.debugf("Shell was opened for %s", cmd);
                }
            }

            @Override
            public void onFailure(Throwable t, Response failureResponse) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                log.warnf("Error executing %s encountered; \ncode: %s\n stderr: %s\nstdout: %s",
                        cmd,
                        error.toString(StandardCharsets.UTF_8),
                        out.toString(StandardCharsets.UTF_8),
                        failureResponse == null ? "(null)" : failureResponse.code(),
                        t);
                if (log.isDebugEnabled() && failureResponse != null) {
                    try {
                        log.debugf("Failure response details for %s: code: %s, body: %s",
                                cmd, failureResponse.code(), failureResponse.body());
                    } catch (IOException e) {
                        log.debugf("Can't get failureResponse.body() for %s", cmd, e);
                    }
                }
                response.completeExceptionally(t);
            }

            @Override
            public void onClose(int rc, String reason) {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                if (log.isDebugEnabled()) {
                    log.debugf("Shell closed for %s; rc = %s; reason: %s", cmd, rc, reason);
                }
                // rc is not bash's return code, it is client's code so no point checking it
                response.complete(out.toString(StandardCharsets.UTF_8));
            }
        };

        ExecWatch exec = null;

        try {
            exec = client
                    .pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .inContainer(containerName)
                    .writingOutput(out)
                    .writingError(error)
                    .usingListener(listener)
                    .exec("bash", "-c", cmd);
        } catch (Throwable t) {
            log.errorf("Execution failed for %s", cmd, t);
            completed.set(true);
            response.completeExceptionally(t);
        }

        final ExecWatch execToClose = exec;
        response.whenComplete((s, ex) -> {
            IOUtils.closeQuietly(execToClose);
            IOUtils.closeQuietly(out);
            IOUtils.closeQuietly(error);
        });

        return response;
    }

    private AutoscalerUtils() {
    }

}
