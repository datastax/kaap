package com.datastax.oss.pulsaroperator.crd;

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
public abstract class BaseComponentSpec {

    protected String image;
    protected Map<String, String> nodeSelectors;

    public void mergeGlobalSpec(GlobalSpec globalSpec) {
        if (image == null) {
            image = globalSpec.getImage();
        }
        if (nodeSelectors == null) {
            nodeSelectors = globalSpec.getNodeSelectors();
        }
    }


}
