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
package com.datastax.oss.kaap.controllers;

import com.datastax.oss.kaap.crds.CRDConstants;
import com.datastax.oss.kaap.crds.GlobalSpec;
import com.datastax.oss.kaap.crds.configs.AntiAffinityConfig;
import com.datastax.oss.kaap.crds.configs.RackConfig;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.NodeAffinity;
import io.fabric8.kubernetes.api.model.PodAffinity;
import io.fabric8.kubernetes.api.model.PodAffinityBuilder;
import io.fabric8.kubernetes.api.model.PodAffinityTerm;
import io.fabric8.kubernetes.api.model.PodAffinityTermBuilder;
import io.fabric8.kubernetes.api.model.PodAntiAffinity;
import io.fabric8.kubernetes.api.model.PodAntiAffinityBuilder;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTermBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;

public class AffinityRulesCustomizer {

    public static final String TOPOLOGY_KEY_ZONE = "failure-domain.beta.kubernetes.io/zone";
    public static final String TOPOLOGY_KEY_HOST = "kubernetes.io/hostname";
    private static final int WEIGHT = 100;

    private final GlobalSpec global;
    private final NodeAffinity nodeAffinity;
    private final AntiAffinityConfig overrideGlobalAntiAffinity;
    private final Map<String, String> matchLabels;
    private final String rack;

    private final List<PodAffinityTerm> requiredAntiAffinityTerms = new ArrayList<>();
    private final List<WeightedPodAffinityTerm> preferredAntiAffinityTerms = new ArrayList<>();
    private final List<PodAffinityTerm> requiredAffinityTerms = new ArrayList<>();
    private final List<WeightedPodAffinityTerm> preferredAffinityTerms = new ArrayList<>();

    private Affinity generatedAffinity;

    public AffinityRulesCustomizer(GlobalSpec global, NodeAffinity nodeAffinity,
                                   AntiAffinityConfig overrideGlobalAntiAffinity,
                                   Map<String, String> matchLabels, String rack) {
        this.global = global;
        this.nodeAffinity = nodeAffinity;
        this.overrideGlobalAntiAffinity = overrideGlobalAntiAffinity;
        this.matchLabels = matchLabels;
        this.rack = rack;
    }

    public Affinity generateAffinity() {
        if (generatedAffinity != null) {
            return generatedAffinity;
        }
        final AntiAffinityConfig antiAffinityConfig =
                ObjectUtils.firstNonNull(overrideGlobalAntiAffinity, global.getAntiAffinity());
        if (rack != null) {
            handleRackConfig();
        } else if (antiAffinityConfig != null) {
            handleAntiAffinityTypeConfig(antiAffinityConfig.getHost(), TOPOLOGY_KEY_HOST);
            handleAntiAffinityTypeConfig(antiAffinityConfig.getZone(), TOPOLOGY_KEY_ZONE);
        }

        generatedAffinity = generateAffinityModel();
        return generatedAffinity;
    }

    private Affinity generateAffinityModel() {
        PodAntiAffinity podAntiAffinity = null;
        if (!preferredAntiAffinityTerms.isEmpty() || !requiredAntiAffinityTerms.isEmpty()) {
            podAntiAffinity = new PodAntiAffinityBuilder()
                    .withPreferredDuringSchedulingIgnoredDuringExecution(preferredAntiAffinityTerms)
                    .withRequiredDuringSchedulingIgnoredDuringExecution(requiredAntiAffinityTerms)
                    .build();
        }
        PodAffinity podAffinity = null;
        if (!requiredAffinityTerms.isEmpty() || !preferredAffinityTerms.isEmpty()) {
            podAffinity = new PodAffinityBuilder()
                    .withRequiredDuringSchedulingIgnoredDuringExecution(requiredAffinityTerms)
                    .withPreferredDuringSchedulingIgnoredDuringExecution(preferredAffinityTerms)
                    .build();
        }
        if (podAntiAffinity != null || nodeAffinity != null || podAffinity != null) {
            return new AffinityBuilder()
                    .withPodAffinity(podAffinity)
                    .withNodeAffinity(nodeAffinity)
                    .withPodAntiAffinity(podAntiAffinity)
                    .build();
        }
        return null;
    }

    private void handleRackConfig() {
        final Map<String, RackConfig> racks = global.getRacks();
        if (racks == null || racks.isEmpty()) {
            throw new IllegalArgumentException("Rack is specified but no racks are configured");
        }
        final RackConfig rackConfig = racks.get(rack);
        if (rackConfig == null) {
            throw new IllegalArgumentException("Rack is specified but no rack config is found for rack " + rack);
        }
        handleRackHostConfig(TOPOLOGY_KEY_HOST, rackConfig.getHost());

        handleRackZoneConfig(TOPOLOGY_KEY_ZONE, rackConfig.getZone());
    }

    private void handleAntiAffinityTypeConfig(AntiAffinityConfig.AntiAffinityTypeConfig config, String topologyKey) {
        if (config == null || config.getEnabled() == null || !config.getEnabled()) {
            return;
        }

        final PodAffinityTerm podAffinityTerm = new PodAffinityTermBuilder()
                .withNewLabelSelector()
                .withMatchLabels(matchLabels)
                .endLabelSelector()
                .withTopologyKey(topologyKey)
                .build();

        if (config.getRequired() != null && config.getRequired()) {
            requiredAntiAffinityTerms.add(podAffinityTerm);
        } else {
            final WeightedPodAffinityTerm weightedPodAffinityTerm = new WeightedPodAffinityTermBuilder()
                    .withWeight(WEIGHT)
                    .withPodAffinityTerm(podAffinityTerm)
                    .build();
            preferredAntiAffinityTerms.add(weightedPodAffinityTerm);
        }
    }

    private void handleCommonRackAffinityRules(String topologyKey, boolean requireRackAffinity,
                                               boolean requireRackAntiAffinity) {
        final PodAffinityTerm affinity = new PodAffinityTermBuilder()
                .withNewLabelSelector()
                .addNewMatchExpression()
                .withKey(CRDConstants.LABEL_RACK)
                .withOperator("In")
                .withValues(rack)
                .endMatchExpression()
                .endLabelSelector()
                .withTopologyKey(topologyKey)
                .build();
        if (requireRackAffinity) {
            requiredAffinityTerms.add(affinity);
        } else {
            preferredAffinityTerms.add(new WeightedPodAffinityTermBuilder()
                    .withWeight(WEIGHT)
                    .withPodAffinityTerm(affinity)
                    .build());
        }

        final List<String> otherRacks =
                global.getRacks().keySet().stream().filter(r -> !r.equals(rack)).collect(Collectors.toList());
        final PodAffinityTerm antiAffinity = new PodAffinityTermBuilder()
                .withNewLabelSelector()
                .addNewMatchExpression()
                .withKey(CRDConstants.LABEL_RACK)
                .withOperator("In")
                .withValues(otherRacks)
                .endMatchExpression()
                .endLabelSelector()
                .withTopologyKey(topologyKey)
                .build();
        if (requireRackAntiAffinity) {
            requiredAntiAffinityTerms.add(antiAffinity);
        } else {
            preferredAntiAffinityTerms.add(new WeightedPodAffinityTermBuilder()
                    .withWeight(WEIGHT)
                    .withPodAffinityTerm(antiAffinity)
                    .build());
        }


    }

    private void handleRackHostConfig(String topologyKey, RackConfig.HostRackTypeConfig rackConfig) {
        if (rackConfig == null || !rackConfig.getEnabled()) {
            return;
        }

        handleCommonRackAffinityRules(topologyKey, rackConfig.getRequireRackAffinity(),
                rackConfig.getRequireRackAntiAffinity());
    }

    private void handleRackZoneConfig(String topologyKey, RackConfig.ZoneRackTypeConfig rackConfig) {
        if (rackConfig == null || !rackConfig.getEnabled()) {
            return;
        }
        handleCommonRackAffinityRules(topologyKey, rackConfig.getRequireRackAffinity(),
                rackConfig.getRequireRackAntiAffinity());

        // do not schedule same component of the same rack and resource-set  in the same host
        if (rackConfig.getEnableHostAntiAffinity()) {
            final PodAffinityTerm hostAntiAffinity = new PodAffinityTermBuilder()
                    .withNewLabelSelector()
                    // this config is only per-component
                    .withMatchLabels(matchLabels)
                    .endLabelSelector()
                    .withTopologyKey(TOPOLOGY_KEY_HOST)
                    .build();
            if (rackConfig.getRequireRackHostAntiAffinity()) {
                requiredAntiAffinityTerms.add(hostAntiAffinity);
            } else {
                preferredAntiAffinityTerms.add(new WeightedPodAffinityTermBuilder()
                        .withWeight(WEIGHT)
                        .withPodAffinityTerm(hostAntiAffinity)
                        .build());
            }
        }
    }
}
