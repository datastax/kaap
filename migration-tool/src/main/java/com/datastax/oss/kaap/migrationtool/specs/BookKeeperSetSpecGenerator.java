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
package com.datastax.oss.kaap.migrationtool.specs;

import com.datastax.oss.kaap.controllers.bookkeeper.BookKeeperResourcesFactory;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSetSpec;
import com.datastax.oss.kaap.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.kaap.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.kaap.crds.configs.tls.TlsConfig;
import com.datastax.oss.kaap.migrationtool.InputClusterSpecs;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodDNSConfig;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BookKeeperSetSpecGenerator extends BaseSpecGenerator<BookKeeperSetSpec> {

    public static final String SPEC_NAME = "BookKeeper";
    private final String resourceName;
    private BookKeeperSetSpec generatedSpec;
    private PodDNSConfig podDNSConfig;
    private boolean isRestartOnConfigMapChange;
    private String priorityClassName;
    private Map<String, Object> config;
    private TlsConfig.TlsEntryConfig tlsEntryConfig;

    public BookKeeperSetSpecGenerator(InputClusterSpecs inputSpecs,
                                      InputClusterSpecs.BookKeeperSpecs.BookKeeperSetSpecs setSpec,
                                      KubernetesClient client) {
        super(inputSpecs, client);
        final String clusterName = inputSpecs.getClusterName();
        resourceName = BookKeeperResourcesFactory.getResourceName(clusterName,
                inputSpecs.getBookkeeper().getBaseName(),
                setSpec.getName(), setSpec.getOverrideName());
        internalGenerateSpec();
    }

    @Override
    public String getSpecName() {
        return SPEC_NAME;
    }

    @Override
    public BookKeeperSetSpec generateSpec() {
        return generatedSpec;
    }

    @Override
    public boolean isEnabled() {
        return generatedSpec.getReplicas() > 0;
    }

    public void internalGenerateSpec() {
        final PodDisruptionBudget podDisruptionBudget = getPodDisruptionBudget(resourceName);
        final ConfigMap configMap = requireConfigMap(resourceName);
        final Service mainService = requireService(resourceName);
        final StatefulSet statefulSet = requireStatefulSet(resourceName);

        verifyLabelsEquals(podDisruptionBudget, statefulSet, configMap);


        final StatefulSetSpec statefulSetSpec = statefulSet.getSpec();
        final PodSpec spec = statefulSetSpec.getTemplate()
                .getSpec();
        final Container container = spec
                .getContainers()
                .get(0);
        verifyProbeCompatible(container.getReadinessProbe());
        verifyProbeCompatible(container.getLivenessProbe());
        PodDisruptionBudgetConfig podDisruptionBudgetConfig =
                createPodDisruptionBudgetConfig(podDisruptionBudget);


        podDNSConfig = spec.getDnsConfig();
        isRestartOnConfigMapChange = isPodDependantOnConfigMap(statefulSetSpec.getTemplate());
        priorityClassName = spec.getPriorityClassName();
        config = convertConfigMapData(configMap);
        tlsEntryConfig = createTlsEntryConfig(statefulSet);

        final NodeAffinity nodeAffinity = spec.getAffinity() == null ? null : spec.getAffinity().getNodeAffinity();
        final Map<String, String> matchLabels = getMatchLabels(statefulSetSpec);
        final List<PersistentVolumeClaim> volumeClaimTemplates = statefulSetSpec.getVolumeClaimTemplates();
        final PersistentVolumeClaim journalPvc = requirePvc("%s-journal".formatted(resourceName), volumeClaimTemplates);
        final PersistentVolumeClaim ledgersPvc = requirePvc("%s-ledgers".formatted(resourceName), volumeClaimTemplates);
        generatedSpec = BookKeeperSetSpec.builder()
                .annotations(cleanupAnnotations(statefulSet.getMetadata().getAnnotations()))
                .podAnnotations(cleanupAnnotations(statefulSetSpec.getTemplate().getMetadata().getAnnotations()))
                .image(container.getImage())
                .imagePullPolicy(container.getImagePullPolicy())
                .nodeSelectors(spec.getNodeSelector())
                .replicas(statefulSetSpec.getReplicas())
                .probes(createProbeConfig(container))
                .nodeAffinity(nodeAffinity)
                .pdb(podDisruptionBudgetConfig)
                .labels(statefulSet.getMetadata().getLabels())
                .skipVolumeClaimLabels(true)
                .podLabels(statefulSetSpec.getTemplate().getMetadata().getLabels())
                .matchLabels(matchLabels)
                .config(config)
                .podManagementPolicy(statefulSetSpec.getPodManagementPolicy())
                .updateStrategy(statefulSetSpec.getUpdateStrategy())
                .gracePeriod(spec.getTerminationGracePeriodSeconds() == null ? null :
                        spec.getTerminationGracePeriodSeconds().intValue())
                .resources(container.getResources())
                .tolerations(spec.getTolerations())
                .volumes(BookKeeperSpec.Volumes.builder()
                        .journal(createVolumeConfig(resourceName, journalPvc))
                        .ledgers(createVolumeConfig(resourceName, ledgersPvc))
                        .build())
                .service(createServiceConfig(mainService))
                .imagePullSecrets(spec.getImagePullSecrets())
                .antiAffinity(createAntiAffinityConfig(spec))
                .env(container.getEnv())
                .initContainers(getInitContainers(spec,
                        BookKeeperResourcesFactory.getInitContainerNames(inputSpecs.getClusterName(),
                                inputSpecs.getBookkeeper().getBaseName())))
                .sidecars(getSidecars(spec, BookKeeperResourcesFactory.getContainerNames(inputSpecs.getClusterName(),
                        inputSpecs.getBookkeeper().getBaseName())))
                .build();
    }


    @Override
    public PodDNSConfig getPodDnsConfig() {
        return podDNSConfig;
    }

    @Override
    public boolean isRestartOnConfigMapChange() {
        return isRestartOnConfigMapChange;
    }

    @Override
    public String getDnsName() {
        return null;
    }

    @Override
    public String getPriorityClassName() {
        return priorityClassName;
    }

    @Override
    public Map<String, Object> getConfig() {
        return config;
    }

    @Override
    public TlsConfig.TlsEntryConfig getTlsEntryConfig() {
        return tlsEntryConfig;
    }

    @Override
    public String getTlsCaPath() {
        if (tlsEntryConfig != null) {
            return Objects.requireNonNull((String) getConfig().get("bookkeeperTLSTrustCertsFilePath"));
        }
        return null;
    }

    @Override
    public String getAuthPublicKeyFile() {
        return null;
    }

    private BookKeeperSpec.ServiceConfig createServiceConfig(Service service) {
        Map<String, String> annotations = service.getMetadata().getAnnotations();
        if (annotations == null) {
            annotations = new HashMap<>();
        }
        return BookKeeperSpec.ServiceConfig.builder()
                .annotations(annotations)
                .additionalPorts(removeServicePorts(service.getSpec().getPorts(),
                        BookKeeperResourcesFactory.DEFAULT_BK_PORT))
                .build();
    }

    private void verifyProbeCompatible(Probe probe) {
        if (probe.getHttpGet() == null) {
            throw new IllegalStateException("current probe is not compatible, must be httpGet");
        }
    }

    private TlsConfig.TlsEntryConfig createTlsEntryConfig(StatefulSet statefulSet) {
        final String tlsProvider = (String) getConfig().get("tlsProvider");
        if (!"OpenSSL".equals(tlsProvider)) {
            return null;
        }
        final Volume certs = statefulSet.getSpec().getTemplate().getSpec().getVolumes().stream()
                .filter(v -> "certs".equals(v.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tls volume with name 'certs' found"));
        final String secretName = certs.getSecret().getSecretName();

        return TlsConfig.TlsEntryConfig.builder()
                .enabled(true)
                .secretName(secretName)
                .build();
    }

}
