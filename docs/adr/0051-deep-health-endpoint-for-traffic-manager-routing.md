# ADR-0051: Deep health endpoint for traffic-manager routing

- **Status**: Accepted
- **Date**: 2026-09-01

## Context

ADR-0050 put the same chart on two clusters — production and failover — with the EBI traffic
manager deciding which one receives `www.ebi.ac.uk` traffic. The traffic manager's monitor probes
one HTTP path per node, requires a 2xx status, and additionally regex-matches the response body
against a configured sentence; a single failed probe takes the node out of rotation. The backend
had nothing for it to probe: no health endpoint, no actuator, and the backend Deployments in every
chart ran without probes of any kind.

What "up" must mean here is shaped by two failure modes this project has already been burned by:

- **A Solr whose cores failed to load answers 200 on `/admin/info/system` while every query 500s**
  (the Solr 10 start-mode flip, the stale-binary drift on wwwdev). Every existing check in the repo
  — the Solr readinessProbe, docker-compose's healthcheck, `startSolr.sh` — therefore probes all
  three cores' `/select`, never an admin endpoint.
- **A reachable but empty index.** Cores freshly created by `copySolrConfig.sh`, a dataload that
  died before indexing, or an NFS tarball that failed to unpack all answer queries perfectly and
  serve an empty site. A traffic manager choosing between two clusters must not route users to it.

A shallow "the JVM answers" check would pass in both cases.

## Decision

A plain controller, `GET /api/v2/health`, answers **200 with the exact sentence
`All systems are operational.`** in the body when each of the three Solr cores (`oxo2-mappings`,
`oxo2-mappingsets`, `oxo2-entities`) answers a `*:*` `rows=0` query **and** reports
`numFound > 0`; otherwise **503** with per-core document counts and errors, and the sentence
absent. Living under `/api` means the existing ingress rule already routes it — no ingress change.

The healthy sentence is a **contract with the traffic manager's monitor configuration**
(`http_body_regex`), maintained outside this repository. It is a constant on `HealthController`;
rewording it without changing the monitor config takes production out of rotation.

The same endpoint becomes the backend's **readinessProbe** in all three charts (`k8chart/`,
`k8chart-dev/`, `k8chart-local/`), so in-cluster readiness and cross-cluster routing agree on what
"up" means. No livenessProbe, mirroring the Solr deployment's reasoning: the plausible failures are
all Solr-side, and restarting the backend fixes none of them.

Spring Boot Actuator was rejected: the depth needed is one bespoke check, so the framework buys
nothing; `/actuator` sits outside the `/api` ingress rule; and it is a new dependency through the
shade plugin, whose `spring.factories`/auto-config merging has already been a silent-breakage
source in the Jackson 3 migration (ADR-0046).

Probe traffic is **deliberately not suppressed from the request log for now**. With
`org.springframework.web` at `trace` plus `spring.mvc.log-request-details=true`, every kubelet
probe (10 s) and traffic-manager probe (30 s) writes DispatcherServlet request/response lines —
noise in steady state, but exactly the visibility wanted while the endpoint, the probes, and the
traffic-manager integration are bedding in. Once the check has proven itself in production, the
noise can be revisited. A working suppression design exists if it is: a servlet filter setting an
MDC mark for the duration of a health request, plus a logback `TurboFilter` denying
`org.springframework.web` events while the mark is set — request-scoped rather than a message-text
match, because DispatcherServlet's `Completed 200 OK` lines never mention the URI (a text match was
tried and leaked them).

## Consequences

**An empty or half-loaded deployment takes itself out of rotation.** Data presence is a serving
requirement, not an operational nicety: a cluster that lost its index fails over instead of serving
an empty site. The flip side: a deliberately empty environment (a fresh minikube before its first
dataload) reads as down until data is loaded — correct, if occasionally surprising.

**Backend readiness now tracks Solr.** Backend pods leave the Service whenever Solr is down,
loading, or empty. On a fresh rollout the backend sits unready for as long as Solr's cores take to
open (Solr's own probe budgets 10 minutes on a full-size index), so deploy-time gates that wait for
readiness now genuinely wait for a servable stack.

**The check is three `rows=0` queries per probe** — `numFound` reads, nothing scaling with corpus
size, arriving every 10 s from the kubelet and every 30 s from the traffic manager. Negligible.

**A hung Solr surfaces as a probe timeout, not a fast 503.** The check runs through the same
`OxOSolrClient` timeouts as user queries (10 s connect, 60 s socket), and both the traffic
manager's monitor and the readinessProbe time out at 10 s. Down either way; the 503 path is for
Solr answering with a failure or an empty core.
