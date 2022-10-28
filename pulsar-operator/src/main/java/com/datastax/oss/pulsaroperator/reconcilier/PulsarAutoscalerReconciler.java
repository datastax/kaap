package com.datastax.oss.pulsaroperator.reconcilier;

import com.datastax.oss.pulsaroperator.crd.PulsarAutoscaler;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.admin.PulsarAdmin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Function;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsar-autoscaler-app")
public class PulsarAutoscalerReconciler implements Reconciler<PulsarAutoscaler> {

    private final KubernetesClient client;
    private Timer currentTimer;

    @SneakyThrows
    public PulsarAutoscalerReconciler(KubernetesClient client) {
        this.client = client;
    }

    private class MainTask extends TimerTask {

        private final PulsarAdmin admin;
        private final PulsarAutoscalerConfig config;
        private final Map<String, PulsarAdmin> directAdminBrokers = new HashMap<>();

        @SneakyThrows
        public MainTask(PulsarAutoscalerConfig config) {
            this.config = config;
            try {
                System.out.println("Starting operator with config " + config);
                if (StringUtils.isBlank(config.getBrokerWebServiceURL())) {
                    throw new IllegalArgumentException("empty brokerWebServiceURL");
                }
                final String url = config.isTlsEnabledWithBroker() ?
                        config.getBrokerWebServiceURLTLS() : config.getBrokerWebServiceURL();
                admin = PulsarAdmin.builder()
                        .serviceHttpUrl(url)
                        .build();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }


        @Override
        @SneakyThrows
        public void run() {
            try {
                final DeploymentList list = client
                        .apps()
                        .deployments()
                        .list(new ListOptionsBuilder()
                                .withLabelSelector("component=broker")
                                .build());


                System.out.println("Discovered " + list.getItems().size() + " broker deployments");
                for (Deployment deployment : list.getItems()) {
                    System.out.println("Discovered broker deployment: "
                            + deployment.getFullResourceName() + " " + deployment.getMetadata().getName());
                }

                final List<String> activeBrokers = new ArrayList<>();
                for (String cluster : admin.clusters().getClusters()) {
                    activeBrokers.addAll(admin.brokers()
                            .getActiveBrokers(cluster));
                }

                for (String brokerAddress : activeBrokers) {
                    final PulsarAdmin directBrokerAdmin =
                            directAdminBrokers.computeIfAbsent(brokerAddress, new Function<String, PulsarAdmin>() {
                                @Override
                                @SneakyThrows
                                public PulsarAdmin apply(String s) {
                                    final String proto = config.isTlsEnabledWithBroker() ? "https" : "http";
                                    return PulsarAdmin.builder()
                                            .serviceHttpUrl(proto + "://" + brokerAddress)
                                            .build();
                                }
                            });
                    final String metricsForBroker = directBrokerAdmin.brokerStats().getMetrics();
                    System.out.println("metrics for broker: " + brokerAddress + ": " + metricsForBroker);
                }


                int scaleTo;
                if (activeBrokers.size() % 2 == 0) {
                    scaleTo = activeBrokers.size() + 1;
                } else {
                    scaleTo = activeBrokers.size() - 1;
                }
                client.apps().deployments().withName("pulsar-broker").scale(scaleTo, false);
                System.out.println("Scaled brokers to " + scaleTo);
            } catch (Throwable tr) {
                System.out.println("Error: " + tr);
                System.out.println(tr);
                throw new RuntimeException(tr);
            }
        }
    }

    @Override
    public UpdateControl<PulsarAutoscaler> reconcile(PulsarAutoscaler resource, Context context) {
        System.out.println("reconcile called" + resource + context + " modified");
        final PulsarAutoscalerSpec spec = resource.getSpec();

        if (resource.getStatus().getCurrentSpec() != null) {
            System.out.println("Upgrading RC");
            currentTimer.cancel();
        } else {
            System.out.println("Creating the RC for the first time");
        }
        final MainTask task = new MainTask(spec.getAutoscaler());
        currentTimer = new Timer();
        /*currentTimer.scheduleAtFixedRate(task,
                0, spec.getAutoscaler().getScaleIntervalMs());*/
        resource.getStatus().setCurrentSpec(spec);
        return UpdateControl.updateStatus(resource);
    }
}

