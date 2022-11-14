package com.datastax.oss.pulsaroperator.crds.cluster;

import com.datastax.oss.pulsaroperator.controllers.autoscaler.AutoscalerSpec;
import com.datastax.oss.pulsaroperator.crds.FullSpecWithDefaults;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.validation.ValidSpec;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Valid;
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
    private AutoscalerSpec autoscaler;

    @ValidSpec
    @Valid
    private ZooKeeperSpec zookeeper;

    @ValidSpec
    @Valid
    private BookKeeperSpec bookkeeper;

    @ValidSpec
    @Valid
    private BrokerSpec broker;

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
                && autoscaler.isValid(value.getAutoscaler(), context)
                && zookeeper.isValid(value.getZookeeper(), context)
                && bookkeeper.isValid(value.getBookkeeper(), context)
                && broker.isValid(value.getBroker(), context);
    }
}
