package com.datastax.oss.pulsaroperator.tests;

import com.dajudge.kindcontainer.K3sContainer;
import com.dajudge.kindcontainer.K3sContainerVersion;
import com.dajudge.kindcontainer.KubernetesImageSpec;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalK8sEnvironment extends BaseK8sEnvironment {

    public static void main(String[] args) throws Exception {
        final KubernetesImageSpec<K3sContainerVersion> k3sImage =
                new KubernetesImageSpec<>(K3sContainerVersion.VERSION_1_25_0)
                        .withImage("rancher/k3s:v1.25.3-k3s1");
        try (final K3sContainer container = new K3sContainer(k3sImage);){
            createAndMountImageDigest(PULSAR_IMAGE, container);
            container.start();
            container.kubectl().create.namespace.run("ns");
            log.info("To see k3s logs: docker logs {}", container.getContainerName());
            log.info("You can now access the K8s cluster, namespace 'ns'.");
            log.info("Now paste this in a new terminal:\nexport KUBECONFIG={} && kubectl config set-context --current --namespace=ns "
                    + "&& mvn quarkus:dev -pl pulsar-operator", getTmpKubeConfig(container));

            log.info("To install a sample cluster open another terminal and paste:\nexport KUBECONFIG={} && kubectl config set-context --current --namespace=ns "
                    + "&& kubectl apply -f helm/examples/cluster.yaml", getTmpKubeConfig(container));
            restoreDockerImageInK3s(PULSAR_IMAGE, container);
            Thread.sleep(Integer.MAX_VALUE);
        }
    }

}
