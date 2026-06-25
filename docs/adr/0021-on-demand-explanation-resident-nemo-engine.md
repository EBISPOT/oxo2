# ADR-0021: On-demand explanations served by a resident Nemo engine

- **Status**: Proposed
- **Date**: 2026-06-25
- **Refines**: [ADR-0020](0020-defer-explanations-to-on-demand.md) (revises its cost premise; does not disturb its index-bare decision)

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
is the chase; a single trace is cheap. The CLI already exploits this — within one `nmo` run it does
`execute()` once and then traces on the *same* engine without re-reasoning.

The reason a *second* `nmo` invocation re-pays the chase is that Nemo keeps **no on-disk snapshot** of
the materialised state (database + dictionary + `rule_history`); a new process must rebuild it. So the
~10–20 min is a per-process **cold-start**, not an intrinsic per-trace cost. Keeping a single engine
resident in memory pays the chase once and then answers many traces cheaply — which removes the
"explanation cannot be synchronous" blocker.

## Decision

Serve on-demand explanations from a long-lived **`oxo2-explain`** service that loads `sssom.rls` +
`assertedCorpus.nq`, runs the cross-set chase **once at startup**, and keeps the Nemo
`ExecutionEngine` **resident in memory**, answering single-conclusion traces against it. A trace is a
near-synchronous backward query (sub-second to seconds); the full reasoning pass is paid once per
process at startup (and on cold restart), not per request.

The retained Java chain interpreter is reused unchanged: the resident engine emits the same
`nmo --trace-output` JSON (`ExecutionTraceListOfInferencesJSON`) that `NemoHelper` /
`NemoInferences` / `Inferences2Trace` in `oxo2-json2inferences` already parse into the OxO chain
model. The engine runs in a sidecar (Rust over the `nemo` crate, or Python over `nmo_python`) that
`oxo2-backend` calls over HTTP; the Java side keeps formatting the conclusion as a canonical Nemo
ground fact and interpreting the returned trace.

Status is **Proposed**: the cost claim must be validated empirically on the real corpus before
adoption (see [`docs/on-demand-explanation-service.md`](../on-demand-explanation-service.md) §
Validation). On acceptance, revise ADR-0020's on-demand-service paragraph and the `/CONTEXT.md`
bullet, and set this ADR to Accepted.

## Consequences

- **Explanation becomes near-synchronous**, paid once at startup. The frontend can request an inferred
  mapping's chain inline (`MappingDetails` → `InferredMappingGraph`) instead of polling an async job.
  The chase cost moves from per-request to per-process-start.
- **One trace at a time per engine.** Nemo's trace takes `&mut self` and the engine is not shareable
  across threads (`#[pyclass(unsendable)]` in `nmo_python`); a process owns one engine and serialises
  trace calls. Throughput scales by **replicas**, each holding its own full materialised copy.
- **Memory is the dominant runtime cost.** Each replica holds the whole materialisation + dictionary
  (~24 GB per ADR-0020's figure — measure precisely). This is a heavyweight, vertically-sized pod,
  not a cheap stateless one; replica count is bounded by memory, not CPU.
- **Cold start re-runs the chase (no snapshot).** Every pod (re)start re-pays the ~10–20 min before
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
- **JSON-contract compatibility favours the Rust sidecar.** Rust `ExecutionTrace::json(&handles)`
  emits the `--trace-output` shape `NemoInferences` already parses; the Python `NemoTrace.dict()` is a
  different nested shape that would need a small `nmo_python` addition or a Java-side adapter. Python
  is still the faster route to *validate* the cost model (see the spec).
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
