package com.datastax.oss.pulsaroperator.crd;

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

    public void mergeGlobalSpec(GlobalSpec globalSpec) {
        if (image == null) {
            image = globalSpec.getImage();
        }
    }


}
