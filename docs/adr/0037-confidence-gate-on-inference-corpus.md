# ADR-0037: Confidence gate on the cross-set inference corpus

- **Status**: Accepted
- **Date**: 2026-07-21

## Context

Cross-set SSSOM reasoning ([ADR-0016](0016-single-pass-sssom-reasoning.md)) is a purely logical chase:
`sssom.rls` chains the strong predicates transitively with no notion of how trustworthy each asserted
edge is. A mapping's SSSOM `confidence` is read at ingest (`TSV2JSON`), indexed, and used only as a soft
tie-break in backend ranking (`SolrQueryBuilder`, weight 0.3) — it is dropped at the `nquads` stage,
where each mapping collapses to a 4-term quad `<s> <p> <o> <mapping_id>`, so the chase never sees it.

The consequence is that a low-confidence asserted mapping chains with exactly the same force as a
curated high-confidence one. Because the closure is transitive over connected components, one weak
`skos:exactMatch` edge does not produce one questionable inference — it bridges two cliques and emits
every cross-pair between them. On the loaded Mapping Commons corpus there is real, low confidence data
(e.g. one HP↔DOID set carries 347 of 1291 `owl:equivalentClass` edges below 0.5), so this is a live
quality concern, not a latent one.

This is the **low-confidence** half of the problem. It is distinct from an *incorrect but
high/absent-confidence* mapping (a curation error), for which no threshold is a signal — that is
addressed structurally by the component-size guard ([ADR-0017](0017-cross-set-inference-corpus-component-size-guard.md),
still proposed) and by provenance-driven source curation, not here.

## Decision

Add a confidence gate at the `nquads` stage (`JSON2NQuads`). When `min_inference_confidence > 0`, a
mapping whose `confidence` is **present and strictly below** the threshold is not emitted as a quad, so
it never seeds inference. The gate is deliberately narrow: a mapping with **no** confidence — absent,
blank, or unparseable — always passes. Dropped edges are excluded from **inference only**: they are
still indexed and served as asserted mappings, since the Solr index is built independently of the
N-Quad corpus. Every drop is recorded in a per-set sidecar `<set>.dropped-low-confidence.tsv` — never
silent. The threshold is a single global knob: the top-level `min_inference_confidence` key in the OxO
config (`$OXO2_CONFIG`), read by the dataload (`lib/InferenceConfidenceThreshold.groovy`) and forwarded
to `JSON2NQuads -c`. Absent (or `0`) disables the gate and reproduces the pre-ADR output byte for byte.

## Consequences

- The gate only acts on mappings that self-report a low confidence. It does nothing on corpora without
  confidence values, and nothing against a *wrong* mapping that carries high or absent confidence — that
  failure mode needs the ADR-0017 blast-radius guard and upstream curation, tracked separately.
- The "drop from inference, keep as asserted, report every drop" shape mirrors ADR-0017's proposed
  component guard, so if that is built the two guards compose cleanly at the same seam.
- Scope deliberately left for later: **per-set / per-category thresholds** (the knob is global today) and
  **derived confidence on inferred mappings** (inferred mappings still carry no confidence; propagating
  a combined confidence along each precomputed proof, [ADR-0028](0028-component-sharded-explanation-precompute.md),
  would let inferences be ranked and filtered on evidence strength rather than only seeded/blocked).
- The gate runs before closure, so a dropped edge removes not just itself but the entire sub-closure it
  would have seeded. A too-aggressive threshold can therefore silently shrink findability; the sidecar
  and the `WARN` drop count per set are the audit trail for tuning it.
- `min_inference_confidence` is a **global** top-level key in the OxO config (a sibling of
  `mapping_registries`), not a per-registry field like `category` / `exclude`: it is one policy for the
  whole load. It is registered on `OxoConfiguration` so the strict config deserializer accepts the key,
  but the dataload reads it from the config JSON directly (`lib/InferenceConfidenceThreshold.groovy`),
  the same way `sssom2json.nf` reads per-registry `category`.
