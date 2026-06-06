# ADR-0011: `inference_type` replaces the `is_inferred` flag

- **Status**: Accepted
- **Date**: 2026-06-06
- **Supersedes**: [ADR-0008](0008-is-inferred-flag.md)

## Context

[ADR-0008](0008-is-inferred-flag.md) added a denormalised boolean `is_inferred` to both cores as the single
asserted-vs-inferred filter signal. The two-phase redesign
([ADR-0009](0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md)) produces **two** kinds of inference —
*OWL inferences* (phase 1) and *SSSOM inferences* (phase 2) — which users treat very differently: asserted
mappings and SSSOM inferences are the primary interest; OWL inferences are a last-resort fallback used only
when nothing else links the entities. A boolean cannot express this three-way distinction.

## Decision

Replace `is_inferred` on **both** `oxo2-mappings` and `oxo2-mappingsets` with a single string field
**`inference_type`** holding one of the codes `ASSERTED`, `OWL_INFERENCE`, `SSSOM_INFERENCE`, set **once at
dataload** from OxO provenance (the phase that produced the document; `ASSERTED` for everything ingested from
an input SSSOM file). It is backed by a typed `InferenceType` enum in `oxo2-shared`; the **frontend** maps
codes to display labels ("Asserted" / "OWL inference" / "SSSOM inference").

- **Filter** is multi-select: a list of `inference_type` codes (absent = all) → `fq=inference_type:(… OR …)`.
- **Default** selection is `{ASSERTED, SSSOM_INFERENCE}` (OWL hidden until opted in), applied in the
  **frontend**; the backend stays *absent = all*.
- **Default relevance** weights `ASSERTED` > `SSSOM_INFERENCE` > `OWL_INFERENCE` (soft edismax boost), with
  shorter explanation chains boosted (`recip(distance,…)`); an explicit column sort overrides. This requires
  the default search path to use edismax.

## Consequences

- `is_inferred` is removed everywhere — both schemas, the `oxo2-shared` model, the backend filter/DTOs, and
  the frontend types/UI; the just-merged `is_inferred` work ([ADR-0008](0008-is-inferred-flag.md)) is
  replaced. Requires a reindex.
- The product opinion (OWL hidden by default; asserted ranked highest) lives in the UI and a **tunable**
  boost, not in the stored data or the API default — keeping the API orthogonal and v1-compatible
  ([ADR-0004](0004-backwards-compatible-with-oxo-v1.md)).
- Storing **codes** (not display strings) keeps `fq` and query params clean and lets the UI relabel without a
  reindex.
- Supersedes [ADR-0008](0008-is-inferred-flag.md).
