package com.datastax.oss.pulsaroperator.crds.cluster;

import com.datastax.oss.pulsaroperator.crds.FullSpecWithDefaults;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.validation.ValidSpec;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PulsarClusterSpec extends ValidableSpec<PulsarClusterSpec> implements FullSpecWithDefaults {

    @ValidSpec
    @Valid
    private GlobalSpec global;

    @ValidSpec
    @Valid
    @NotNull
    private ZooKeeperSpec zookeeper;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
    }

    @Override
    public GlobalSpec getGlobalSpec() {
        return global;
    }

    @Override
    public boolean isValid(PulsarClusterSpec value, ConstraintValidatorContext context) {
        return global.isValid(value.getGlobal(), context)
                && zookeeper.isValid(value.getZookeeper(), context);
    }
}
