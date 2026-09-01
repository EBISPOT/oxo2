{{/*
The path the stack is served under, with any trailing slash removed.

Every consumer concatenates it with a path that already starts with "/" — the ingress rules, the
frontend's OXO_PUBLIC_URL and the backend's servlet context path — so a trailing slash in the value
would yield "//api". Trim it here rather than trusting whoever sets it.
*/}}
{{- define "oxo2.basePath" -}}
{{- required "basePath is required (e.g. /oxo2)" .Values.basePath | trimSuffix "/" -}}
{{- end -}}
