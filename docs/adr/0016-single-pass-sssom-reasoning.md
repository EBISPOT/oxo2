# ADR-0016: Single-pass SSSOM reasoning across all mapping sets (drop per-set OWL/subsumption inference)

- **Status**: Accepted
- **Date**: 2026-06-10
- **Supersedes**: [ADR-0009](0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md),
  [ADR-0001](0001-inference-scope-per-mapping-set.md)

## Context

[ADR-0009](0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md) split reasoning into two independent
phases: **phase 1** — OWL/subsumption reasoning (`owl.rls`: `T4`/`T5` plus `RCE-N1`–`RCE-N4`) applied **per
mapping set**, deriving `rdfs:subClassOf`/`rdfs:subPropertyOf`; and **phase 2** — SSSOM reasoning
(`sssom.rls`) applied **across all mapping sets**, deriving mapping/equivalence chains. Phase 2 is the
findability gain; phase 1 was retained from [ADR-0001](0001-inference-scope-per-mapping-set.md)'s per-set
scope.

Measured against both real corpora, **phase 1 earns no value**:

- On the **OLS** corpus it is pure cost: subsumption transitivity materialises an enormous hidden closure
  (the `ncbitaxon` set alone produces on the order of 50.6M derived subsumption rows), all of it carried
  through trace, explanation, and Solr for a relation users do not query OxO for.
- On the curated **Mapping Commons** corpus it is a guaranteed no-op: the corpus is ≈78% `skos:exactMatch`
  with effectively zero asserted subsumption, so the OWL rules fire on nothing.

Maintaining phase 1 nonetheless costs a full extra Nemo inference pass per set, the per-set trace/explain
fan-out, the `owl.rls` ruleset, the `OWL_INFERENCE` tier threaded through the data model, backend, and
frontend, and a per-source inferred-set surface — all for zero realised benefit. The "two phases" framing
also pervades the code and docs.

## Decision

OxO2 runs a **single inference pass**: SSSOM reasoning (`sssom.rls`) across all mapping sets. Phase 1 is
removed in full — OxO2 no longer derives `rdfs:subClassOf`/`rdfs:subPropertyOf`. Concretely: `owl.rls`, the
`T4`/`T5`/`RCE-N1`–`RCE-N4` chain rules, the `OWL_INFERENCE` inference type, the per-set
`inferAndExplainMappings.nf` pipeline, and the "phase" terminology are all removed. The JSON → N-Quads
conversion (`JSON2NQUADS`) folds into the single inference pipeline (`inferSssomCrossSet.nf`).

On [ADR-0001](0001-inference-scope-per-mapping-set.md): its per-set rule existed to stop subsumption being
asserted *across* ontologies (a class in one ontology declared a subclass of one in another) and the
attendant avalanche. We did **not** move subsumption cross-set — ADR-0001's warning against that still
holds. We **dropped** subsumption inference altogether, which makes per-set scope moot. All findability
value lives in the cross-set mapping/equivalence predicates, which are unaffected.

## Consequences

- OxO2 no longer derives subsumption (`rdfs:subClassOf`/`rdfs:subPropertyOf`). This is a deliberate
  capability removal, justified by the no-value finding above; it is recoverable by reinstating a ruleset if
  a future corpus shows demand.
- `inference_type` ([ADR-0011](0011-inference-type-replaces-is-inferred.md)) now has two values: `ASSERTED`
  and `SSSOM_INFERENCE`. `OWL_INFERENCE` is removed end-to-end (data model, backend ranking/filters,
  frontend labels/badges/filters, integration-test goldens).
- The dataload has **one** inference pipeline and no phase vocabulary. It remains the heaviest stage
  operationally; the cross-set closure cost of the strong-equivalence predicates is a separate, live concern
  tracked outside this ADR (this change is the prerequisite that removes the unrelated phase-1 overhead
  first).
- `ChainRulesEnum` drops `T4`/`T5`/`RCE-N1`–`RCE-N4`, and `RG1`/`RG2` (which were defined in neither
  ruleset — dead since before this change).
- The weak/noisy predicate exclusions and the one-rule-per-strong-predicate enumeration that ADR-0009
  specified for phase 2 carry forward unchanged as the single pass.
- Cross-set provenance via `mapping_id` ([ADR-0010](0010-carry-mapping-provenance-via-nquads.md)) and the
  resolvable inference-set IRI surface ([ADR-0012](0012-resolvable-inference-set-iris.md)) are unchanged.
  Only the per-source inferred sets that phase 1 produced are no longer generated; the single cross-set
  `https://www.ebi.ac.uk/oxo2/inferences` set remains.
