package com.nicoloboschi;

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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@ControllerConfiguration(namespaces = Constants.WATCH_CURRENT_NAMESPACE, name = "pulsaroperatorapp")
public class PulsarOperatorReconciler implements Reconciler<PulsarOperator> {

    private final KubernetesClient client;

    @SneakyThrows
    public PulsarOperatorReconciler(KubernetesClient client) {
        this.client = client;
        final MainTask task = new MainTask();
        new Timer().scheduleAtFixedRate(task,
                0, TimeUnit.SECONDS.toMillis(30));


    }

    private class MainTask extends TimerTask {

        private final PulsarAdmin admin;
        private final boolean tlsEnabledWithBroker;
        private final Map<String, PulsarAdmin> directAdminBrokers = new HashMap<>();

        @SneakyThrows
        public MainTask() {
            try {
                // ENV injected by the configMap
                final String brokerWebServiceURL = System.getenv("brokerWebServiceURL");
                final String brokerWebServiceURLTLS = System.getenv("brokerWebServiceURLTLS");
                tlsEnabledWithBroker = Boolean.parseBoolean(System.getenv("tlsEnabledWithBroker"));
                System.out.println("brokerWebServiceURL=" + brokerWebServiceURL);
                System.out.println("brokerWebServiceURLTLS=" + brokerWebServiceURLTLS);
                System.out.println("tlsEnabledWithBroker=" + tlsEnabledWithBroker);
                if (StringUtils.isBlank(brokerWebServiceURL)) {
                    throw new IllegalArgumentException("empty brokerWebServiceURL");
                }
                final String url = tlsEnabledWithBroker ? brokerWebServiceURLTLS : brokerWebServiceURL;
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
            final DeploymentList list = client
                    .apps()
                    .deployments()
                    .list(new ListOptionsBuilder()
                            .withLabelSelector("component=broker")
                            .build());


            System.out.println("Discovered " + list.getItems().size() + " broker deployments");
            for (Deployment deployment : list.getItems()) {
                System.out.println("Discovered broker deployment: " + deployment.getFullResourceName());
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
                                final String proto = tlsEnabledWithBroker ? "https" : "http";
                                return PulsarAdmin.builder()
                                        .serviceHttpUrl(proto + "://" + brokerAddress)
                                        .build();
                            }
                        });
                final String metricsForBroker = directBrokerAdmin.brokerStats().getMetrics();
                System.out.println("metrics for broker: " + brokerAddress + ": " + metricsForBroker);
            }
            System.out.println("done..");
        }
    }


    @Override
    public UpdateControl<PulsarOperator> reconcile(PulsarOperator resource, Context context) {
        System.out.println("reconcile called" + resource + context);

        return UpdateControl.noUpdate();
    }
}

