package com.datastax.oss.pulsaroperator.tests;

import com.dajudge.kindcontainer.K3sContainer;

public class K3sContainerWithDocker<SELF extends K3sContainerWithDocker<SELF>> extends K3sContainer<SELF> {

    public void start0() {
        this.withCommand(new String[]{"server", "--docker", "--disable=traefik", "--tls-san=" + this.getHost(), String.format("--service-node-port-range=%d-%d", 30000, 32767)});
        super.start();
    }

}
