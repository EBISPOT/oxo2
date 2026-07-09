# ADR-0021: On-demand explanations served by a resident Nemo engine

- **Status**: Proposed — cost model validated empirically 2026-07-06 (see Context § measured
  costs). **Motivation largely removed** by
  [ADR-0028](0028-component-sharded-explanation-precompute.md): explanations are precomputed again,
  so nothing in the UI now needs an on-demand trace.
- **Date**: 2026-06-25
- **Refines**: [ADR-0020](0020-defer-explanations-to-on-demand.md) (revises its cost premise; does not disturb its index-bare decision)

> **2026-07-08 scope note.** This ADR's latency measurements stand: a resident engine over the *whole*
> corpus still traces one conclusion in ~1.2 s typical / ~6 s worst. What no longer stands is its
> conclusion that bulk precompute is infeasible ("~341 h at 16 engines"). That figure bounds an
> architecture where every engine holds the whole 55.9M-fact materialisation; per-trace cost tracks
> **store size**, not proof size, and a per-component store makes the same trace ~0.3–0.9 ms. See
> [ADR-0028](0028-component-sharded-explanation-precompute.md). This ADR remains relevant only if a
> *synchronous, arbitrary-conclusion* explanation API is ever needed for conclusions the dataload did
> not precompute.

## Context

[ADR-0020](0020-defer-explanations-to-on-demand.md) deferred explanation reconstruction to "a future
async, cached, on-demand service", on an explicit cost premise:

> "because tracing one conclusion still costs a full reasoning pass (~10–20 min, ~24 GB), explanation
> cannot be a synchronous request … That service will reuse the retained Java chain interpreter over
> `nmo` + `sssom.rls` + `assertedCorpus.nq`."

That premise measures the cost of a **fresh `nmo` process**. Each `nmo --trace-input-file` invocation
re-imports `assertedCorpus.nq` and re-applies `sssom.rls` (the full cross-set chase) *before* it can
trace — which is exactly why the dataload's `EXPLAIN_CROSS_SET_CHUNK` fan-out ran the whole reasoning
once per chunk (ADR-0020 § Context).

Inspecting the Nemo engine (`knowsys/nemo`, the `nemo` crate) shows the trace itself is **not** a
reasoning pass. After the chase, the engine holds the materialised per-step result tables plus a
`rule_history` (step → rule that fired). A trace is a *backward* search over that retained state:
find the step a conclusion first appeared, look up the rule, unify the head, query the body atoms
against the already-materialised tables restricted to earlier steps, and recurse. The expensive part
is the chase; a single trace is far cheaper — though, as the measurements below show, seconds rather
than milliseconds on a store of this size. The CLI already exploits this — within one `nmo` run it
does `execute()` once and then traces on the *same* engine without re-reasoning.

The reason a *second* `nmo` invocation re-pays the chase is that Nemo keeps **no on-disk snapshot** of
the materialised state (database + dictionary + `rule_history`); a new process must rebuild it. So the
~10–20 min is a per-process **cold-start**, not an intrinsic per-trace cost. Keeping a single engine
resident in memory pays the chase once and then answers traces at seconds-scale per-trace cost —
which removes the "explanation cannot be synchronous" blocker.

**Measured costs.** The cost model was validated on the full dev corpus (OLS + Mapping Commons
`assertedCorpus.nq`, 7.2M quads; the chase derives 55.9M facts, of which 14.9M are non-self inferred
mapping conclusions) with `nmo` v0.10.0 on single-CPU SLURM runs (jobs 59579148 and 62712918,
2026-07-06):

- **Chase (cold start): ~14–15 min wall, ~11.6 GiB peak RSS** — consistent across six chase runs,
  matching the dataload's `INFER_CROSS_SET` stage.
- **Warm traces are seconds, not milliseconds**: first trace after the chase 2.9 s (includes ~1.8 s
  one-off setup); marginal random trace **1.16 s**; amortised over 4000 random traces 1.32 s, flat
  across the run. A milliseconds intuition (1.6 ms/trace on an isolated 434-node-component subset)
  does NOT carry over: the backward search looks each fact up across every per-step subtable and
  re-runs a restricted rule-body join against the *global* tables per derivation step
  (`execution_engine/tracing/simple.rs`), so per-trace cost scales with store size, not component
  size. No upstream fix exists (v0.10.0 is the latest release).
- **Worst case ~6 s**: conclusions inside the largest identity component (a 434-node clique)
  averaged 6.25 s/trace over 400 traces, with in-batch memoisation helping — budget ~5–10 s for a
  cold worst-case request.
- **Trace-serving memory overhead ≈ zero**: every trace batch peaked at the chase-only RSS. The
  retained `rule_history` is a vector of rule indices; the backward search re-queries the resident
  tables.
- **The same numbers kill the bulk-precompute alternative**: tracing all 14.9M conclusions at
  dataload on a pool of resident engines costs ~341 h at 16 engines (~85 h at 64) — worse than the
  removed ~48 h `EXPLAIN_CROSS_SET_CHUNK` fan-out at any realistic pool size. If bulk precompute is
  ever needed again (e.g. to restore `distance`), the only route these numbers leave open is
  component-sharded chase+trace: chase small per-component stores, where the milliseconds regime
  holds.

## Decision

Serve on-demand explanations from a long-lived **`oxo2-explain`** service that loads `sssom.rls` +
`assertedCorpus.nq`, runs the cross-set chase **once at startup**, and keeps the Nemo
`ExecutionEngine` **resident in memory**, answering single-conclusion traces against it. A trace is
a near-synchronous backward query — measured ~1.2 s for a typical warm request, ~2.9 s for the first
request after a chase, ~6 s worst case (§ Context); the full reasoning pass (~14–15 min) is paid
once per process at startup (and on cold restart), not per request.

The retained Java chain interpreter is reused unchanged: the resident engine emits the same
`nmo --trace-output` JSON (`ExecutionTraceListOfInferencesJSON`) that `NemoHelper` /
`NemoInferences` / `Inferences2Trace` in `oxo2-json2inferences` already parse into the OxO chain
model. The engine runs in a sidecar (Rust over the `nemo` crate — the Python `nmo_python` route is
closed, see Consequences) that `oxo2-backend` calls over HTTP; the Java side keeps formatting the
conclusion as a canonical Nemo ground fact and interpreting the returned trace.

Status is **Proposed**: the cost claim was validated empirically on the full dev corpus on
2026-07-06 (§ Context; methodology in
[`docs/on-demand-explanation-service.md`](../on-demand-explanation-service.md) § Validation) and
passed its acceptance band — every trace, worst case included, is seconds, not minutes. Remaining
before acceptance: the product call that ~6 s worst-case synchronous latency is acceptable for the
explain UI, and un-parking iteration 2 (production currently ships bare per ADR-0020). On
acceptance, revise ADR-0020's on-demand-service paragraph and the `/CONTEXT.md` bullet, and set this
ADR to Accepted.

## Consequences

- **Explanation becomes near-synchronous**, paid once at startup — ~1.2 s typical, ~3 s first
  request after a chase, ~6 s worst case. The frontend can request an inferred mapping's chain
  inline (`MappingDetails` → `InferredMappingGraph`) behind a spinner instead of polling an async
  job. The chase cost moves from per-request to per-process-start.
- **One trace at a time per engine.** Nemo's trace takes `&mut self` and the engine is not shareable
  across threads (`#[pyclass(unsendable)]` in `nmo_python`); a process owns one engine and serialises
  trace calls. At the measured ~1.2–1.3 s/trace a replica sustains under one request per second, so
  service-side caching (below) carries popular mappings. Throughput scales by **replicas**, each
  holding its own full materialised copy.
- **Memory is the dominant runtime cost.** Each replica holds the whole materialisation + dictionary:
  measured **11.6 GiB peak**, with zero additional trace-serving overhead — ~16 GB pods give
  comfortable headroom (ADR-0020's ~24 GB guess was high). This is a heavyweight, vertically-sized
  pod, not a cheap stateless one; replica count is bounded by memory, not CPU.
- **Cold start re-runs the chase (no snapshot).** Every pod (re)start re-pays the ~14–15 min before
  it is ready. Mitigation, not elimination: startup/readiness probes sized to the chase, a
  PodDisruptionBudget, generous resources, avoid preemptible nodes, and **blue/green on data release**
  (warm the new materialisation, then switch traffic). A future Nemo snapshot/restore feature would
  remove this; it does not exist today.
- **New runtime inputs.** The service needs `sssom.rls` + `assertedCorpus.nq` available at runtime;
  these are currently dataload-internal artifacts. The data release must **publish** them (alongside
  `solr-data.tar.gz`) to a location the service mounts (PVC / object store), versioned with the index
  so explanations match the mappings being displayed.
- **Fact-format is load-bearing.** `trace` looks a conclusion up by exact value; the request must be
  the canonical Nemo ground fact — IRIs, datatypes, and the `urn:uuid` provenance graph
  ([ADR-0010](0010-carry-mapping-provenance-via-nquads.md)) must match the materialised form. The
  backend should derive it from the indexed mapping's structured fields, not reconstruct loosely.
- **The sidecar is Rust.** Rust `ExecutionTrace::json(&handles)` emits the `--trace-output` shape
  `NemoInferences` already parses; the Python `NemoTrace.dict()` is a different nested shape, and —
  decisively — `nmo_python`'s `.trace()` panics ("wrong arena") on IRI facts as of v0.10, hit during
  validation (which therefore ran via the `nmo` CLI). The Python route is closed until fixed
  upstream.
- **Caching stays service-side** (ADR-0020's read-only-Solr constraint is unchanged): explanations are
  deterministic per data version, so cache `(data_version, conclusion) → trace JSON` in the service /
  a shared cache — never written back into the read-only index.
- **Reuses ADR-0020's retained components.** `NemoHelper`, `Inferences2Trace`, `OnDiskChainStore`,
  `ExplainInferredMappings`, and the `NemoInferences` model remain the reuse basis; only the *source*
  of the trace JSON changes (a warm in-memory engine instead of a fresh `nmo` process). ADR-0020's
  index-bare decision, deferred `distance`/`explanation` cleanup, and shortened pipeline are unaffected.
- **Refines, does not supersede, ADR-0020.** Only its "explanation cannot be synchronous / reuse the
  Java interpreter over the `nmo` CLI" premise is revised. ADR-0020 stays Accepted until this ADR is
  accepted, at which point its on-demand paragraph and the `/CONTEXT.md` bullet are updated in the
  same PR.
