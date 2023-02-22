package com.datastax.oss.pulsaroperator.crds;

import static org.testng.Assert.*;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerWithSetsSpec;
import org.testng.annotations.Test;

public class ConfigUtilTest {

    @Test
    public void test() {
        final BrokerSpec brokerSpec = new BrokerSpec();
        final BrokerWithSetsSpec brokerWithSetsSpec = new BrokerWithSetsSpec();
        ConfigUtil.applyDefaultsWithReflection(brokerSpec, () -> brokerWithSetsSpec);
    }

}