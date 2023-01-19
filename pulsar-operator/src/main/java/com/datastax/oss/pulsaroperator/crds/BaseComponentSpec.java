package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.crds.configs.PodDisruptionBudgetConfig;
import com.datastax.oss.pulsaroperator.crds.configs.ProbeConfig;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.ObjectUtils;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseComponentSpec<T> extends ValidableSpec<T> implements WithDefaults {

    @JsonPropertyDescription(CRDConstants.DOC_IMAGE)
    protected String image;
    @JsonPropertyDescription(CRDConstants.DOC_IMAGE_PULL_POLICY)
    private String imagePullPolicy;
    @JsonPropertyDescription(CRDConstants.DOC_NODE_SELECTORS)
    protected Map<String, String> nodeSelectors;
    @JsonPropertyDescription(CRDConstants.DOC_REPLICAS)
    protected Integer replicas;
    @JsonPropertyDescription("Liveness and readiness probe values.")
    private ProbeConfig probe;
    @JsonPropertyDescription("Pod disruption budget configuration.")
    private PodDisruptionBudgetConfig pdb;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (image == null) {
            image = globalSpec.getImage();
        }
        nodeSelectors = mergeMaps(globalSpec.getNodeSelectors(), nodeSelectors);
        if (imagePullPolicy == null) {
            imagePullPolicy = globalSpec.getImagePullPolicy();
        }
        applyProbeDefault();
        applyPdbDefault();
    }

    private void applyProbeDefault() {
        final ProbeConfig defaultProbe = getDefaultProbeConfig();
        if (probe == null) {
            probe = defaultProbe;
        } else if (defaultProbe != null) {
            boolean enabled = probe.getEnabled() == null
                    ? defaultProbe.getEnabled() : probe.getEnabled();
            if (!enabled) {
                probe = ProbeConfig.builder()
                        .enabled(false)
                        .build();
            } else {
                probe = ProbeConfig.builder()
                        .enabled(true)
                        .initial(ObjectUtils.firstNonNull(probe.getInitial(),
                                defaultProbe.getInitial()))
                        .period(ObjectUtils.firstNonNull(probe.getPeriod(),
                                defaultProbe.getPeriod()))
                        .timeout(ObjectUtils.firstNonNull(probe.getTimeout(),
                                defaultProbe.getTimeout()))
                        .build();
            }
        }
    }

    public void applyPdbDefault() {
        PodDisruptionBudgetConfig defaultPdb = getDefaultPdb();
        if (pdb != null) {
            pdb.setEnabled(ObjectUtils.firstNonNull(pdb.getEnabled(), defaultPdb.getEnabled()));
            pdb.setMaxUnavailable(ObjectUtils.firstNonNull(pdb.getMaxUnavailable(),
                    defaultPdb.getMaxUnavailable()));
        } else {
            pdb = defaultPdb;
        }
    }

    public static Map<String, String> mergeMaps(Map<String, String> parent, Map<String, String> child) {
        if (parent == null) {
            return child;
        }
        if (child == null) {
            return parent;
        }
        Map<String, String> result = new HashMap<>(parent);
        result.putAll(child);
        return result;
    }


    protected abstract ProbeConfig getDefaultProbeConfig();

    protected abstract PodDisruptionBudgetConfig getDefaultPdb();
}
