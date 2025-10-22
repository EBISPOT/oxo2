{{- define "oxo2.name" -}}
{{- default .Chart.Name .Values.nameOverride -}}
{{- end -}}

{{- define "oxo2.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride -}}
{{- else -}}
{{- $name := include "oxo2.name" . -}}
{{- if eq $name .Release.Name -}}
{{- $name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

