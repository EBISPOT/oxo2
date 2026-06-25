# On-demand explanation service (`oxo2-explain`) — implementation spec

**Status:** design spec for [ADR-0021](adr/0021-on-demand-explanation-resident-nemo-engine.md) (Proposed).
Read that ADR first for the decision and its consequences. This document is the concrete
build/deploy plan a future implementation session works from.

## Goal

Let a user, viewing an inferred mapping in the OxO2 frontend, request its derivation chain **on
demand** and get it back in seconds — without the dataload precomputing every explanation (ADR-0020)
and without re-running Nemo's full reasoning per request (the trap ADR-0020 assumed was unavoidable;
see ADR-0021).

## Why this works (one paragraph)

Nemo's `trace` is a cheap backward search over the chase's in-memory result tables + `rule_history`,
**not** a reasoning pass. The reason `nmo --trace-input-file` is slow is that a fresh process
re-imports `assertedCorpus.nq` and re-runs `sssom.rls` before it can trace, and Nemo keeps no on-disk
snapshot to skip that. Keep one `ExecutionEngine` resident in memory: pay the chase once at startup,
then answer many traces cheaply.

## Architecture

```
oxo2-frontend ──HTTP──▶ oxo2-backend (Java) ──HTTP──▶ oxo2-explain  (new K8s Deployment)
  "Explain"             format conclusion as            resident Nemo ExecutionEngine
  on a mapping          a Nemo ground fact;             (chase run ONCE at startup over
                        interpret returned trace        sssom.rls + assertedCorpus.nq),
                        via NemoHelper/NemoInferences   traces one conclusion per request
```

- `oxo2-explain` is a **new, separate** Deployment — not folded into `oxo2-backend` (Java has no Nemo
  bindings) and not into the dataload. It is the only stateful/heavyweight piece.
- `oxo2-backend` gains an explain endpoint that (a) formats the selected inferred mapping as a
  canonical Nemo ground fact, (b) calls `oxo2-explain`, (c) feeds the returned `--trace-output` JSON
  through the **existing** `NemoHelper`/`NemoInferences` interpreter to build the chain/graph the
  frontend already renders (`MappingDetails` → `InferredMappingGraph`).

## Sidecar language: Rust (recommended) vs Python

| | Rust (`nemo` crate) | Python (`nmo_python`) |
|---|---|---|
| Trace JSON | `ExecutionTrace::json(&handles)` emits the **exact** `--trace-output` shape (`ExecutionTraceListOfInferencesJSON`) that `NemoInferences` parses — zero Java change | `NemoTrace.dict()` is a *different* nested shape; needs a small `nmo_python` addition (expose `trace.json()`) or a Java-side adapter |
| Concurrency | dedicated worker task + `mpsc`, no GIL | `unsendable` engine + single ASGI worker |
| Memory / speed | best | fine (GIL irrelevant — one trace at a time anyway) |
| Effort to stand up | higher | **lowest — use this to validate first** |

**Recommendation:** prototype/validate with Python (fastest), ship with Rust (JSON-contract match +
memory). Both hold the engine resident; the architecture is identical.

### Engine API surface (verified against the `nemo` repo)

- Python (`nmo_python`): `eng = NemoEngine(load_file("sssom.rls"))` → `eng.reason()` (the chase, once)
  → `eng.trace("predicate(<s>, <p>, <o>)")` returns a `NemoTrace` (or `None` if not derived);
  `.dict()` / `.subtraces()` / `.rule()` / `.assignement()` walk it. **Import paths resolve relative
  to the process CWD** (the binding uses `ExecutionParameters::default()` with no import base path) —
  run with CWD at the data dir or use absolute paths in `@import`.
- Rust (`nemo` crate): `ExecutionEngine::from_file(RuleFile, ExecutionParameters)` (set the import
  base path via `ExecutionParameters::set_import_manager(ImportManager::new(ResourceProviders::with_base_path(dir)))`)
  → `engine.execute().await` → `engine.trace_facts(vec![fact]).await` → `(trace, handles)` →
  `trace.json(&handles)` for the `--trace-output` JSON. `trace_facts` takes `&mut self`.

## Implementation steps

1. **Pin the Nemo inputs as a runtime artifact.** Have the data release publish `sssom.rls` and
   `assertedCorpus.nq` (versioned with the index) to a PVC / object store the service mounts. Today
   they are dataload-internal (`oxo2-dataload/inferSssomCrossSet.nf`, `oxo2-json2inferences`).
2. **Build the resident service.**
   - Startup: load `sssom.rls`, import `assertedCorpus.nq`, run the chase **once**; mark ready only
     after it completes.
   - `POST /explain { fact }` → trace → emit `--trace-output` JSON → return. `None`/not-derived → a
     clear "no derivation (asserted or absent)" response.
   - Serialise all engine access (one in-flight trace; queue the rest). Cache `(data_version, fact) →
     trace JSON`.
   - `GET /healthz` (process up) and `GET /readyz` (chase done) for probes.
3. **Backend integration.** Add the explain endpoint to `oxo2-backend`: format the conclusion as the
   canonical Nemo ground fact (see contract below), call `oxo2-explain`, run the result through
   `NemoHelper`/`NemoInferences` to build the chain, return it in the shape the frontend expects.
4. **Frontend.** `MappingDetails` already renders `{mapping.explanation && <InferredMappingGraph/>}`;
   wire the "explain" action to fetch on demand and populate it (loading/empty/error states).
5. **Deploy** per § Kubernetes.

## Fact-format contract (get this exactly right)

`trace` looks the conclusion up by **exact value**. The request fact must match the materialised form:
predicate and arity from `sssom.rls`, IRIs vs strings, RDF datatypes/language tags, and the
`urn:uuid` provenance graph ([ADR-0010](adr/0010-carry-mapping-provenance-via-nquads.md)). Derive it
from the indexed mapping's structured fields (`subject_id`, `predicate_id`, `object_id`, …), **not**
from a loosely reconstructed string. Confirm against a fact that `nmo --trace` resolves today, and
add a backend unit test pinning the exact serialisation.

## Kubernetes

- **Memory first.** Size `requests.memory` to the full materialisation (~24 GB per ADR-0020 — measure
  with `nmo <sssom.rls> --report=mem`, i.e. `engine.memory_usage()`), plus headroom; set
  `limits.memory` just above. Under-sizing → OOMKill → cold re-chase.
- **Probes around a slow start.** `startupProbe` on `/readyz` with a `failureThreshold × periodSeconds`
  budget covering the chase (so the kubelet doesn't kill mid-chase); `readinessProbe` on `/readyz`
  (no traffic until ready); `livenessProbe` on `/healthz` (process only), guarded by the startup probe.
- **Minimise restarts** (each re-pays the chase): `PodDisruptionBudget` (`minAvailable: 1`), generous
  `terminationGracePeriodSeconds`, keep off preemptible/spot nodes.
- **Data updates → blue/green.** New data version = new ReplicaSet pointing at the new
  `sssom.rls`+`assertedCorpus.nq`; wait until warm (chase done), then switch the Service selector.
  Tag responses with `data_version`; the frontend requests explanations for the version it displays.
- **Autoscaling.** HPA is fine, but new replicas are expensive to warm — keep `minReplicas` at
  steady-state and scale early. Throughput per replica = one trace at a time (fast), so the cache
  carries most repeat load.

## Validation (do before flipping ADR-0021 to Accepted)

Prove the cost model on the real corpus with the Python binding — smallest possible spike:

1. `pip install` the `nmo_python` wheel (build from `knowsys/nemo`'s `nemo-python`).
2. Script: `eng = NemoEngine(load_file("sssom.rls"))`; time `eng.reason()` (expect the ~10–20 min
   chase, once); then time `eng.trace("<a real inferred conclusion>")` and a few more.
3. **Time the worst case, not just a representative conclusion.** The "sub-second trace" premise is
   load-bearing — it is what makes serving explanations synchronously viable — and it is most likely
   to break on a conclusion whose backward derivation subtree fans out widely even though the chase
   is already done. Deliberately trace the deepest / most highly-connected conclusion (one inside the
   largest `exactMatch` component on the curated corpus) and confirm it still returns fast; if any
   single trace is minutes rather than sub-second–seconds, the synchronous-frontend consequence
   weakens and the service must fall back to async for such conclusions.
4. Confirm: chase paid once; each subsequent trace is sub-second–seconds (including the worst case
   above); resident memory ≈ the `--report=mem` figure. Record the numbers in ADR-0021 and set it
   Accepted (and revise ADR-0020's on-demand paragraph + the `/CONTEXT.md` bullet).

## Open decisions for the team

- Rust sidecar vs Python (JSON-contract + memory vs time-to-ship) — see the table.
- Where the resident `assertedCorpus.nq` + `sssom.rls` live at runtime, and how they're versioned with
  the index.
- Replica count vs pod memory budget for expected concurrent explain load.
- Whether to keep the chain *interpretation* in `oxo2-backend` (reuse `NemoHelper`) or move it into the
  sidecar (ADR-0021 assumes the former, to reuse the retained Java code).
