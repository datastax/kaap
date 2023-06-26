{{/*
Expand the name of the chart.
*/}}
{{- define "k8saap.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "k8saap.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "k8saap.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "k8saap.labels" -}}
{{ include "k8saap.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ include "k8saap.chart" . }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "k8saap.selectorLabels" -}}
app.kubernetes.io/name: {{ include "k8saap.name" . }}
{{- end }}

{{/*
Create the name of the operator role to use
*/}}
{{- define "k8saap.roleName" -}}
{{- default (include "k8saap.fullname" .) .Values.rbac.operatorRole.name }}
{{- end }}

{{/*
Create the name of the operator role binding to use
*/}}
{{- define "k8saap.roleBindingName" -}}
{{- default "default" .Values.rbac.operatorRoleBinding.name }}
{{- end }}

{{/*
Create the name of the operator service account to use
*/}}
{{- define "k8saap.serviceAccountName" -}}
{{- default (include "k8saap.fullname" .) .Values.serviceAccount.name }}
{{- end }}
