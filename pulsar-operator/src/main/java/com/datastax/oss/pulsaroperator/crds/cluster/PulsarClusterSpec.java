package com.datastax.oss.pulsaroperator.crds.cluster;

import com.datastax.oss.pulsaroperator.crds.FullSpecWithDefaults;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.WithDefaults;
import com.datastax.oss.pulsaroperator.crds.autorecovery.AutorecoverySpec;
import com.datastax.oss.pulsaroperator.crds.bastion.BastionSpec;
import com.datastax.oss.pulsaroperator.crds.bookkeeper.BookKeeperSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.function.FunctionsWorkerSpec;
import com.datastax.oss.pulsaroperator.crds.proxy.ProxySpec;
import com.datastax.oss.pulsaroperator.crds.validation.ValidSpec;
import com.datastax.oss.pulsaroperator.crds.validation.ValidableSpec;
import com.datastax.oss.pulsaroperator.crds.zookeeper.ZooKeeperSpec;
import java.lang.reflect.Field;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

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
    private ZooKeeperSpec zookeeper;

    @ValidSpec
    @Valid
    private BookKeeperSpec bookkeeper;

    @ValidSpec
    @Valid
    private BrokerSpec broker;

    @ValidSpec
    @Valid
    private ProxySpec proxy;

    @ValidSpec
    @Valid
    private AutorecoverySpec autorecovery;

    @ValidSpec
    @Valid
    private BastionSpec bastion;

    @ValidSpec
    @Valid
    private FunctionsWorkerSpec functionsWorker;

    @Override
    public void applyDefaults(GlobalSpec globalSpec) {
        initDefaultsForComponent(GlobalSpec.class, "global", globalSpec);
        initDefaultsForComponent(ZooKeeperSpec.class, "zookeeper", globalSpec);
        initDefaultsForComponent(BookKeeperSpec.class, "bookkeeper", globalSpec);
        initDefaultsForComponent(BrokerSpec.class, "broker", globalSpec);
        initDefaultsForComponent(ProxySpec.class, "proxy", globalSpec);
        initDefaultsForComponent(AutorecoverySpec.class, "autorecovery", globalSpec);
        initDefaultsForComponent(BastionSpec.class, "bastion", globalSpec);
        initDefaultsForComponent(FunctionsWorkerSpec.class, "functionsWorker", globalSpec);
    }

    @SneakyThrows
    private <T extends WithDefaults> void initDefaultsForComponent(Class<T> componentClass, String fieldName, GlobalSpec globalSpec) {
        final Field field = PulsarClusterSpec.class.getDeclaredField(fieldName);
        T current = (T) field.get(this);
        if (current == null) {
            current = componentClass.getConstructor().newInstance();
        }
        current.applyDefaults(globalSpec);
        field.set(this, current);
    }

    @Override
    public GlobalSpec getGlobalSpec() {
        return global;
    }

    @Override
    public boolean isValid(PulsarClusterSpec value, ConstraintValidatorContext context) {
        return global.isValid(value.getGlobal(), context)
                && zookeeper.isValid(value.getZookeeper(), context)
                && bookkeeper.isValid(value.getBookkeeper(), context)
                && broker.isValid(value.getBroker(), context)
                && proxy.isValid(value.getProxy(), context)
                && autorecovery.isValid(value.getAutorecovery(), context)
                && bastion.isValid(value.getBastion(), context);
    }
}
