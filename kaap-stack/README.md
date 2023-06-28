# kaap-stack

![Version: 0.1.0](https://img.shields.io/badge/Version-0.1.0-informational?style=flat-square)

Kubernetes Autoscaling for Apache Pulsar Stack

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| file://../kaap | kaap | 0.1.x |
| https://charts.bitnami.com/bitnami | external-dns | v6.13.x |
| https://charts.bitnami.com/bitnami | keycloak | 9.x.x |
| https://charts.jetstack.io | cert-manager | v1.11.x |
| https://datastax.github.io/charts | pulsar-admin-console | 0.1.x |
| https://diennea.github.io/bookkeeper-visual-manager | bkvm | 0.1.x |
| https://prometheus-community.github.io/helm-charts | kube-prometheus-stack | 41.x.x |

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| bkvm.enabled | bool | `false` |  |
| bkvm.server.env[0].name | string | `"BKVM_topology.enabled"` |  |
| bkvm.server.env[0].value | string | `"true"` |  |
| bkvm.server.env[1].name | string | `"BKVM_user.1.username"` |  |
| bkvm.server.env[1].value | string | `"admin"` |  |
| bkvm.server.env[2].name | string | `"BKVM_user.1.password"` |  |
| bkvm.server.env[2].value | string | `"pulsar"` |  |
| bkvm.server.env[3].name | string | `"BKVM_user.1.role"` |  |
| bkvm.server.env[3].value | string | `"Admin"` |  |
| bkvm.server.metadataServiceUri | string | `"zk://pulsar-zookeeper-ca:2181/ledgers"` |  |
| cert-manager.enabled | bool | `false` |  |
| cert-manager.installCRDs | bool | `false` |  |
| external-dns.enabled | bool | `false` |  |
| external-dns.provider | string | `""` |  |
| external-dns.sources[0] | string | `"service"` |  |
| external-dns.txtOwnerId | string | `"kaap-stack"` |  |
| kaap.enabled | bool | `true` |  |
| keycloak.auth.tls.enabled | bool | `false` |  |
| keycloak.enabled | bool | `false` |  |
| keycloak.extraStartupArgs | string | `"-Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=/realm/pulsar-realm.json -Dkeycloak.migration.strategy=IGNORE_EXISTING"` |  |
| keycloak.extraVolumeMounts | string | `"- name: realm-config\n  mountPath: \"/realm/\"\n  readOnly: true\n"` |  |
| keycloak.extraVolumes | string | `"- name: realm-config\n  configMap:\n    name: realm-config\n"` |  |
| keycloak.realm | string | `"pulsar"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].name | string | `"exp-counts-rates"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[0].alert | string | `"producers-high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[0].annotations.description | string | `"Producer count is high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[0].annotations.identifier | string | `"{{ $labels.topic }}"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[0].expr | string | `"pulsar_producers_count > 500"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[0].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[0].labels.severity | string | `"warning"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[1].alert | string | `"consumers-high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[1].annotations.description | string | `"Consumer count is high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[1].annotations.identifier | string | `"{{ $labels.topic }}"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[1].expr | string | `"pulsar_consumers_count > 500"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[1].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[1].labels.severity | string | `"warning"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[2].alert | string | `"in-rate-high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[2].annotations.description | string | `"Incoming message rate is high on broker"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[2].annotations.identifier | string | `"{{ $labels.topic }}"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[2].expr | string | `"pulsar_rate_in > 5000"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[2].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[2].labels.severity | string | `"warning"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[3].alert | string | `"out-rate-high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[3].annotations.description | string | `"Outgoing message rate is high on broker"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[3].annotations.identifier | string | `"{{ $labels.topic }}"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[3].expr | string | `"pulsar_rate_out > 5000"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[3].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.cluster-normal.groups[0].rules[3].labels.severity | string | `"warning"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].name | string | `"acks"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[0].alert | string | `"unacked-message-high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[0].annotations.description | string | `"Unacked messages on subscription high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[0].annotations.identifier | string | `"{{ $labels.topic }}:{{ $labels.subscription }}"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[0].expr | string | `"pulsar_subscription_unacked_messages > 100"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[0].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[0].labels.severity | string | `"critical"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[1].alert | string | `"subscription-blocked-on-unacked-messages"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[1].annotations.description | string | `"Subscription is blocked on unacked messages"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[1].annotations.identifier | string | `"{{ $labels.topic }}:{{ $labels.subscription }}"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[1].expr | string | `"pulsar_subscription_blocked_on_unacked_messages > 1"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[1].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[0].rules[1].labels.severity | string | `"critical"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].name | string | `"components"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[0].alert | string | `"zookeeper-write-latency-high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[0].annotations.description | string | `"Zookeeper write latency is high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[0].annotations.identifier | string | `"{{ $labels.kubernetes_pod_name }}"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[0].expr | string | `"zookeeper_server_requests_latency_ms > 500"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[0].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[0].labels.severity | string | `"warning"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[1].alert | string | `"bookkeeper-add-latency-high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[1].annotations.description | string | `"Add latency to BookKeeper is high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[1].annotations.identifier | string | `"{{ $labels.kubernetes_pod_name }}"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[1].expr | string | `"bookkeeper_server_ADD_ENTRY_REQUEST > 1500"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[1].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[1].labels.severity | string | `"warning"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[2].alert | string | `"bookkeeper-read-latency-high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[2].annotations.description | string | `"Read latency from BookKeeper is high"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[2].annotations.identifier | string | `"{{ $labels.kubernetes_pod_name }}"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[2].expr | string | `"bookkeeper_server_READ_ENTRY_REQUEST > 1000"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[2].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[2].labels.severity | string | `"warning"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[3].alert | string | `"bookkeeper-bookie-readonly"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[3].annotations.description | string | `"Bookie is read-only"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[3].annotations.identifier | string | `"{{ $labels.kubernetes_pod_name }}"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[3].expr | string | `"bookie_SERVER_STATUS == 0"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[3].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.general-rules.groups[1].rules[3].labels.severity | string | `"warning"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.volumes.groups[0].name | string | `"volumes-filling"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.volumes.groups[0].rules[0].alert | string | `"KubePersistentVolumeFillingUp"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.volumes.groups[0].rules[0].annotations.message | string | `"The PersistentVolume claimed by {{ $labels.persistentvolumeclaim }} in Namespace {{ $labels.namespace }} is only {{ $value | humanizePercentage }} free."` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.volumes.groups[0].rules[0].expr | string | `"kubelet_volume_stats_available_bytes{job=\"kubelet\",metrics_path=\"/metrics\",namespace=~\".*\"} / kubelet_volume_stats_capacity_bytes{job=\"kubelet\",metrics_path=\"/metrics\",namespace=~\".*\"} < 0.10"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.volumes.groups[0].rules[0].for | string | `"1m"` |  |
| kube-prometheus-stack.additionalPrometheusRulesMap.volumes.groups[0].rules[0].labels.severity | string | `"critical"` |  |
| kube-prometheus-stack.alertmanager.enabled | bool | `false` |  |
| kube-prometheus-stack.coreDns.enabled | bool | `true` |  |
| kube-prometheus-stack.defaultRules.create | bool | `false` |  |
| kube-prometheus-stack.defaultRules.rules.alertmanager | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.etcd | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.general | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.k8s | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.kubeApiserver | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.kubePrometheusNodeAlerting | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.kubePrometheusNodeRecording | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.kubeScheduler | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.kubernetesAbsent | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.kubernetesApps | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.kubernetesResources | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.kubernetesStorage | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.kubernetesSystem | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.node | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.prometheus | bool | `true` |  |
| kube-prometheus-stack.defaultRules.rules.prometheusOperator | bool | `true` |  |
| kube-prometheus-stack.enabled | bool | `false` |  |
| kube-prometheus-stack.grafana."grafana.ini".security.allow_embedding | bool | `true` |  |
| kube-prometheus-stack.grafana."grafana.ini".security.cookie_samesite | string | `"lax"` |  |
| kube-prometheus-stack.grafana."grafana.ini".server.root_url | string | `"http://localhost:3000"` |  |
| kube-prometheus-stack.grafana.adminPassword | string | `nil` |  |
| kube-prometheus-stack.grafana.dashboardProviders."dashboardproviders.yaml".apiVersion | int | `1` |  |
| kube-prometheus-stack.grafana.dashboardProviders."dashboardproviders.yaml".providers[0].disableDeletion | bool | `true` |  |
| kube-prometheus-stack.grafana.dashboardProviders."dashboardproviders.yaml".providers[0].editable | bool | `false` |  |
| kube-prometheus-stack.grafana.dashboardProviders."dashboardproviders.yaml".providers[0].folder | string | `""` |  |
| kube-prometheus-stack.grafana.dashboardProviders."dashboardproviders.yaml".providers[0].name | string | `"default"` |  |
| kube-prometheus-stack.grafana.dashboardProviders."dashboardproviders.yaml".providers[0].options.path | string | `"/var/lib/grafana/dashboards/default"` |  |
| kube-prometheus-stack.grafana.dashboardProviders."dashboardproviders.yaml".providers[0].orgId | int | `1` |  |
| kube-prometheus-stack.grafana.dashboardProviders."dashboardproviders.yaml".providers[0].type | string | `"file"` |  |
| kube-prometheus-stack.grafana.dashboards.default.operator-metrics.datasource | string | `"Prometheus"` |  |
| kube-prometheus-stack.grafana.dashboards.default.operator-metrics.gnetId | int | `14370` |  |
| kube-prometheus-stack.grafana.dashboards.default.operator-metrics.revision | int | `6` |  |
| kube-prometheus-stack.grafana.dashboards.default.zookeeper.datasource | string | `"Prometheus"` |  |
| kube-prometheus-stack.grafana.dashboards.default.zookeeper.gnetId | int | `10465` |  |
| kube-prometheus-stack.grafana.dashboards.default.zookeeper.revision | int | `4` |  |
| kube-prometheus-stack.grafana.defaultDashboardsEnabled | bool | `true` |  |
| kube-prometheus-stack.grafana.enabled | bool | `true` |  |
| kube-prometheus-stack.grafana.ingress.enabled | bool | `false` |  |
| kube-prometheus-stack.grafana.ingress.hosts[0] | string | `"grafana.example.com"` |  |
| kube-prometheus-stack.grafana.ingress.path | string | `"/"` |  |
| kube-prometheus-stack.grafana.service.port | int | `3000` |  |
| kube-prometheus-stack.grafana.service.type | string | `"LoadBalancer"` |  |
| kube-prometheus-stack.grafana.testFramework.enabled | bool | `false` |  |
| kube-prometheus-stack.kubeApiServer.enabled | bool | `true` |  |
| kube-prometheus-stack.kubeControllerManager.enabled | bool | `false` |  |
| kube-prometheus-stack.kubeDns.enabled | bool | `false` |  |
| kube-prometheus-stack.kubeEtcd.enabled | bool | `true` |  |
| kube-prometheus-stack.kubeProxy.enabled | bool | `false` |  |
| kube-prometheus-stack.kubeScheduler.enabled | bool | `false` |  |
| kube-prometheus-stack.kubeStateMetrics.enabled | bool | `true` |  |
| kube-prometheus-stack.kubelet.enabled | bool | `true` |  |
| kube-prometheus-stack.nodeExporter.enabled | bool | `true` |  |
| kube-prometheus-stack.prometheus-node-exporter | string | `nil` |  |
| kube-prometheus-stack.prometheus.enabled | bool | `true` |  |
| kube-prometheus-stack.prometheus.ingress.enabled | bool | `false` |  |
| kube-prometheus-stack.prometheus.ingress.hosts[0] | string | `"prometheus.example.com"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].honor_labels | bool | `true` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].job_name | string | `"pulsar-pods"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].kubernetes_sd_configs[0].role | string | `"pod"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[0].action | string | `"keep"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[0].regex | bool | `true` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[0].source_labels[0] | string | `"__meta_kubernetes_pod_annotation_prometheus_io_scrape"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[1].action | string | `"replace"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[1].regex | string | `"(.+)"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[1].source_labels[0] | string | `"__meta_kubernetes_pod_annotation_prometheus_io_path"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[1].target_label | string | `"__metrics_path__"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[2].action | string | `"replace"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[2].regex | string | `"([^:]+)(?::\\d+)?;(\\d+)"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[2].replacement | string | `"$1:$2"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[2].source_labels[0] | string | `"__address__"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[2].source_labels[1] | string | `"__meta_kubernetes_pod_annotation_prometheus_io_port"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[2].target_label | string | `"__address__"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[3].action | string | `"labelmap"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[3].regex | string | `"__meta_kubernetes_pod_label_(.+)"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[4].action | string | `"replace"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[4].source_labels[0] | string | `"__meta_kubernetes_namespace"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[4].target_label | string | `"kubernetes_namespace"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[5].action | string | `"replace"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[5].source_labels[0] | string | `"__meta_kubernetes_pod_label_component"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[5].target_label | string | `"job"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[6].action | string | `"replace"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[6].source_labels[0] | string | `"__meta_kubernetes_pod_name"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.additionalScrapeConfigs[0].relabel_configs[6].target_label | string | `"kubernetes_pod_name"` |  |
| kube-prometheus-stack.prometheus.prometheusSpec.retention | string | `"10d"` |  |
| kube-prometheus-stack.prometheusOperator.enabled | bool | `true` |  |
| pulsar-admin-console.config.cluster_name | string | `"pulsar"` |  |
| pulsar-admin-console.config.server_config.ssl.ca_path | string | `"/pulsar/certs/ca.crt"` |  |
| pulsar-admin-console.config.server_config.ssl.cert_path | string | `"/pulsar/certs/tls.crt"` |  |
| pulsar-admin-console.config.server_config.ssl.enabled | bool | `false` |  |
| pulsar-admin-console.config.server_config.ssl.key_path | string | `"/pulsar/certs/tls.key"` |  |
| pulsar-admin-console.config.server_config.token_options.algorithm | string | `"RS256"` |  |
| pulsar-admin-console.enabled | bool | `false` |  |
| pulsarGrafanaDashboards.enabled | bool | `false` |  |

----------------------------------------------
Autogenerated from chart metadata using [helm-docs v1.11.0](https://github.com/norwoodj/helm-docs/releases/v1.11.0)
