# ADR-0020: Inferred mappings indexed without explanations; explanations deferred to on-demand

- **Status**: Accepted
- **Date**: 2026-06-25
- **Supersedes**: [ADR-0018](0018-out-of-core-cross-set-explanation.md)

## Context

The explanation stage dominates the dataload. `nmo trace` cannot trace a single conclusion cheaply:
every `EXPLAIN_CROSS_SET_CHUNK` invocation re-imports the whole `assertedCorpus.nq` and re-applies
`sssom.rls` (the full reasoning), with `--trace-input-file` only selecting which conclusions to emit
derivation trees for. The trace input is split into chunks, so the full cross-set reasoning runs
once for `INFER_CROSS_SET` and then **again per chunk** for the explain fan-out, followed by the
out-of-core `EXPLANATIONS_TO_JSON` pass ([ADR-0018](0018-out-of-core-cross-set-explanation.md)).
Together these run the dataload up to ~48 h.

[ADR-0018](0018-out-of-core-cross-set-explanation.md) kept every inferred mapping's explanation
precomputed — and explicitly **rejected** on-demand explanation — for one reason: `distance` was a
user-facing filter facet, so it had to be materialised for every inferred mapping at load time.
Reassessing that single premise: if we **drop `distance`** and distinguish mappings only by the
existing `inference_type` field, the blocker disappears.

The key enabler is that the inferred mappings themselves — the *what* — do not need the trace.
`sssom.rls` exports them directly:

```
inferredMapping(?s, ?p, ?o) :- mapping(?id, ?s, ?p, ?o), ~assertedTriple(?s, ?p, ?o) .
@export inferredMapping :- turtle{resource=$exportfile} .
```

`inferences.ttl` is already exactly the set of inferred mappings, with asserted echoes excluded by
the `~assertedTriple` negation. Only the *why* (the explanation chain, and the `distance` /
`explanation_length` / asserted-evidence / source-union that are pure functions of it) needs the
expensive trace.

## Decision

Stop precomputing explanations in the dataload. Index inferred mappings **bare** — subject/object
IRI + CURIE + label, predicate id/label, `inference_type = SSSOM_INFERENCE`, `spo_key`, and
`mapping_set_id` = the single cross-set inferences set — built directly from `INFER_CROSS_SET`'s
`inferences.ttl` (skipping `s == o` self-mappings). The explanation chain and per-mapping asserted
evidence are no longer materialised, and the inferred set's `mapping_set_source` union is left empty.
`distance` and `explanation_length` are no longer *computed*: a bare doc carries the model's inert
defaults (`distance` = 1, `explanation_length` = 0) until the deferred cleanup drops the fields from
the model and Solr schema.

Explanation reconstruction is **deferred to a future async, cached, on-demand service** (a separate
iteration): because tracing one conclusion still costs a full reasoning pass (~10–20 min, ~24 GB),
explanation cannot be a synchronous request, and — since the asserted neighbourhood of a conclusion
is unknown without its chain — it cannot be scoped to a local sub-corpus either. That service will
reuse the retained Java chain interpreter over `nmo` + `sssom.rls` + `assertedCorpus.nq`.

This iteration removes `DETERMINE_CROSS_SET_TRACE` / `SPLIT_CROSS_SET_TRACE` /
`EXPLAIN_CROSS_SET_CHUNK` / `MERGE_CROSS_SET_CHAIN` / `EXPLANATIONS_TO_JSON` from the active
pipeline and rebaselines the integration goldens. The backend / frontend / Solr-schema cleanup of
the now-dead `distance` and `explanation` surface is **consciously deferred** (see Consequences).

## Consequences

- **Dataload wall-time.** The K per-chunk full-reasoning passes plus the out-of-core explanation
  pass — the dominant cost — are gone; only the single `INFER_CROSS_SET` pass remains. Measuring the
  new wall-time on a full run is the immediate goal of this iteration.
- **Inferred mappings lose** their precomputed explanation chain and asserted-mapping evidence;
  `distance`/`explanation_length` are no longer computed (a bare doc carries the inert model defaults
  1/0 until the fields are dropped in the deferred cleanup). The inferred set's `mapping_set_source`
  is empty (per-mapping source was already unset in cross-set mode).
- **Ranking degrades within the inference tier.** `SolrQueryBuilder.RANKING_BOOST`'s distance factor
  `1 + 0.4/(distance+1)` is now constant — every inferred doc carries the inert default
  `distance` = 1 (asserted docs have none, so `def(distance,1)` = 1 too), so the factor is uniform
  and the "shorter chains first" tie-break is neutralised. The inference-type tier ordering
  (ASSERTED > SSSOM_INFERENCE) is unaffected, so same-SPO **representative** selection
  ([ADR-0013](0013-group-same-spo-mappings-in-result-views.md)) still picks an asserted member; only
  the within-inferred chain-length tie-break is lost.
- **Frontend degrades gracefully.** `MappingDetails` renders the chain as
  `{mapping.explanation && <InferredMappingGraph/>}`; a bare inferred mapping simply omits the
  "Explanation of inferred mapping" graph — no error.
- **Read-only Solr at runtime** (the HPC-built `solr-data.tar.gz` is extracted into the dev-cluster
  PVC) means a computed explanation can never be written back into the inferred mapping doc. The
  on-demand service must therefore return chains via a new API path and cache them service-side, not
  in Solr — and `distance` / explanation will never be repopulated in the index, which is why the
  deferred surface cleanup below is inert, not a live landmine.
- **Retained for reuse (dormant in the pipeline):** `ExplainInferredMappings`, `NemoHelper`'s
  chain-building, `OnDiskChainStore`, `Inferences2Trace`, and the `nmo --trace-input-file`
  invocation. The on-demand service is built on these.
- **Deferred cleanup**, against the usual clean-as-you-go standard and recorded here so it is a
  conscious choice, not an oversight: the `distance` / `explanation_length` / `explanation`
  Solr-schema fields, the `RANKING_BOOST` collapse to tier-only, `MappingSearchRequest.distance`,
  and the frontend distance column/sort/filter. They are inert (read-only Solr never repopulates
  them) and the same surface is reshaped when the on-demand explanation API lands.
- **Revises the [ADR-0019](0019-resumable-hpc-dataload.md) stage list.** `trace` / `explain` /
  `merge` are removed, `explanations2json` is replaced by `inferences2json` (the bare build), and
  the `inferSssomCrossSet.nf` resume entry points `from_trace` / `from_explain` / `from_merge` are
  removed (only `from_infer` remains); the resumable stages become
  `download → sssom2json → nquads → infer → index-asserted → inferences2json → index-inferred → archive`.
- **Supersedes [ADR-0018](0018-out-of-core-cross-set-explanation.md):** the out-of-core explanation
  builder is no longer on the dataload path. ADR-0018's core insight — that the explanation is
  *identity-independent*, a pure function of a conclusion's reachable sub-DAG — remains the
  correctness basis for recomputing any single chain on demand.
