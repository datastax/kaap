package com.datastax.oss.pulsaroperator.crds.proxy;

import com.datastax.oss.pulsaroperator.crds.FullSpecWithDefaults;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.validation.ValidSpec;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProxyFullSpec implements FullSpecWithDefaults {
    @Valid
    @ValidSpec
    GlobalSpec global;

    @Valid
    @ValidSpec
    ProxySpec proxy;


    @Override
    public GlobalSpec getGlobalSpec() {
        return global;
    }

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        if (proxy == null) {
            proxy = new ProxySpec();
        }
        proxy.applyDefaults(globalSpec);
    }
}
