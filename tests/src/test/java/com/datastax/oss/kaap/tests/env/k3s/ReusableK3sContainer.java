/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.kaap.tests.env.k3s;

import com.dajudge.kindcontainer.K3sContainer;
import com.dajudge.kindcontainer.K3sContainerVersion;
import com.dajudge.kindcontainer.KubernetesImageSpec;
import com.dajudge.kindcontainer.helm.Helm3Container;
import com.github.dockerjava.api.command.InspectContainerResponse;
import java.util.function.Consumer;
import lombok.Getter;
import org.testcontainers.utility.DockerImageName;

class ReusableK3sContainer<T extends ReusableK3sContainer<T>> extends K3sContainer<T> {

    Boolean reused;
    private Consumer<Helm3Container> helm3ContainerConsumer;
    @Getter
    private Helm3Container helm3;

    public ReusableK3sContainer(KubernetesImageSpec<K3sContainerVersion> imageSpec) {
        super(imageSpec);
        withReuse(true);
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo, boolean reused) {
        this.reused = reused;
        super.containerIsStarting(containerInfo, reused);
    }

    public void beforeStartHelm3(Consumer<Helm3Container> consumer) {
        if (helm3 != null) {
            throw new IllegalStateException("helm already started!");
        }
        helm3ContainerConsumer = consumer;
    }

    @Override
    public synchronized Helm3Container<?> helm3() {
        if (this.helm3 == null) {
            this.helm3 = (Helm3Container) (new Helm3Container(DockerImageName.parse("alpine/helm:3.17.2"),
                    this::getInternalKubeconfig))
                    .withNetworkMode("container:" + this.getContainerId());
            if (helm3ContainerConsumer != null) {
                helm3ContainerConsumer.accept(helm3);
            }
            this.helm3.start();
        }

        return this.helm3;
    }

    @Override
    public void stop() {
        if (helm3 != null) {
            helm3.stop();
        }
        super.stop();
    }

    public void stopHelm3() {
        if (helm3 != null) {
            helm3.stop();
            helm3 = null;
        }
    }


}
