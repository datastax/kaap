package com.datastax.oss.pulsaroperator.crds;

import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseComponentSpec<T> extends ValidableSpec<T> implements WithDefaults {

    @JsonPropertyDescription("Pulsar image to use for this component.")
    protected String image;
    @JsonPropertyDescription("Pulsar image pull policy to use for this component.")
    private String imagePullPolicy;
    @JsonPropertyDescription("Additional node selectors for this component.")
    protected Map<String, String> nodeSelectors;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (image == null) {
            image = globalSpec.getImage();
        }
        nodeSelectors = mergeMaps(globalSpec.getNodeSelectors(), nodeSelectors);
        if (imagePullPolicy == null) {
            imagePullPolicy = globalSpec.getImagePullPolicy();
        }
    }

    private Map<String, String> mergeMaps(Map<String, String> parent, Map<String, String> child) {
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
}
