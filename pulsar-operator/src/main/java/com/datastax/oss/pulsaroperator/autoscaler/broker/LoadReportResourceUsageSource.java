package com.datastax.oss.pulsaroperator.autoscaler.broker;

import com.datastax.oss.pulsaroperator.autoscaler.AutoscalerUtils;
import com.datastax.oss.pulsaroperator.common.SerializationUtil;
import com.datastax.oss.pulsaroperator.controllers.broker.BrokerResourcesFactory;
import com.datastax.oss.pulsaroperator.crds.GlobalSpec;
import com.datastax.oss.pulsaroperator.crds.broker.BrokerSetSpec;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.StringUtils;

@JBossLog
public class LoadReportResourceUsageSource implements BrokerResourceUsageSource {

    private final KubernetesClient client;
    private final String namespace;
    private final Map<String, String> podSelector;
    private final String brokerSet;
    private final BrokerSetSpec brokerSetSpec;
    private final GlobalSpec globalSpec;

    public LoadReportResourceUsageSource(KubernetesClient client, String namespace,
                                         Map<String, String> podSelector,
                                         String brokerSet,
                                         BrokerSetSpec brokerSetSpec,
                                         GlobalSpec globalSpec) {
        this.client = client;
        this.namespace = namespace;
        this.podSelector = podSelector;
        this.brokerSet = brokerSet;
        this.brokerSetSpec = brokerSetSpec;
        this.globalSpec = globalSpec;
    }

    @Override
    @SneakyThrows
    public List<ResourceUsage> getBrokersResourceUsages() {
        final List<Pod> pods = client.pods()
                .inNamespace(namespace)
                .withLabels(podSelector)
                .list()
                .getItems();


        List<ResourceUsage> result = new ArrayList<>();
        String webServicePort = getWebServicePort();
        final String brokerUrl =
                "http://localhost:%s/admin/v2/broker-stats/load-report/".formatted(String.valueOf(webServicePort));
        final String curlAuthHeader = BrokerResourcesFactory.computeCurlAuthHeader(globalSpec);
        final String curlCommand = StringUtils.isBlank(curlAuthHeader) ?
                "curl %s".formatted(brokerUrl) :
                "curl %s %s".formatted(curlAuthHeader, brokerUrl);

        final String containerName =
                BrokerResourcesFactory.getMainContainerName(BrokerResourcesFactory.getResourceName(globalSpec.getName(),
                        globalSpec.getComponents().getBrokerBaseName(), brokerSet,
                        brokerSetSpec.getOverrideResourceName()));
        for (Pod pod : pods) {
            final String podName = pod.getMetadata().getName();

            final String jsonOut = AutoscalerUtils.execInPod(client, namespace, podName, containerName, curlCommand)
                    .get(30, TimeUnit.SECONDS);

            final Map<String, Object> json = SerializationUtil.readJson(jsonOut, Map.class);
            if (!json.containsKey("cpu")) {
                throw new IllegalStateException(
                        "Broker %s didn't exposed valid report usage, expected 'cpu', found: %s".formatted(podName,
                                jsonOut));
            }
            final LoadReportResourceUsage loadReportResourceUsage =
                    SerializationUtil.convertValue(json.get("cpu"), LoadReportResourceUsage.class);
            final float percentUsage = loadReportResourceUsage.percentUsage();

            final float rounded = new BigDecimal(percentUsage).setScale(2, RoundingMode.HALF_UP)
                    .floatValue();


            log.infof("Broker %s cpu usage: %f %%", podName, rounded * 100);
            result.add(new ResourceUsage(podName, rounded));
        }
        return result;
    }

    private String getWebServicePort() {
        Object webServicePort =
                brokerSetSpec.getConfig() != null
                        ? brokerSetSpec.getConfig().get("webServicePort")
                        : null;
        if (webServicePort == null) {
            webServicePort = BrokerResourcesFactory.DEFAULT_HTTP_PORT;
        }
        return String.valueOf(webServicePort);
    }

    @Data
    @NoArgsConstructor
    public static class LoadReportResourceUsage {
        double usage;
        double limit;

        public float percentUsage() {
            float proportion = 0;
            if (limit > 0) {
                proportion = ((float) usage) / ((float) limit);
            }
            return proportion;
        }
    }
}
