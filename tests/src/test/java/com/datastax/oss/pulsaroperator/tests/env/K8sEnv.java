package com.datastax.oss.pulsaroperator.tests.env;

import com.dajudge.kindcontainer.helm.Helm3Container;
import io.fabric8.kubernetes.client.Config;
import java.util.function.Consumer;

public interface K8sEnv {

    void start();

    Config getConfig();

    String getStorageClass();

    Helm3Container withHelmContainer(Consumer<Helm3Container> preInit);

    Helm3Container helmContainer();

    void cleanup();

    void close();
}
