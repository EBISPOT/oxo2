# ADR-0009: Two-phase reasoning — OWL rules per mapping set, SSSOM rules across all mapping sets

- **Status**: Accepted
- **Date**: 2026-06-06
- **Supersedes**: [ADR-0001](0001-inference-scope-per-mapping-set.md)

## Context

OxO2 derives inferred mappings with Nemo over a single rule set (`chain-rules.rls`) applied **per mapping
set** ([ADR-0001](0001-inference-scope-per-mapping-set.md)). Per-set scope was chosen for SSSOM-spec
conformance and as a safeguard against an "avalanche of biologically incorrect inferences" and combinatorial
blowup (see also [ADR-0006](0006-chainrules-limited-to-those-not-copying-tree.md), which removed
tree-copying rules).

Per-set scope, however, defeats OxO's core purpose — *findability*. If `A exactMatch B` lives in mapping set
1 and `B exactMatch C` in mapping set 2, no `A exactMatch C` is derived, so the link across ontologies is
never found.

At the same time, not all rules are safe to apply across sets. The rules that derive `rdfs:subClassOf` /
`rdfs:subPropertyOf` express *ontology-internal hierarchy*; applied across sets they would assert that a class
in one ontology is a subclass of a class in another — usually wrong. Note that `RCE-N1`/`RCE-N2` are in fact
the `?p = rdfs:subClassOf` instances of the role-chain rules `RCE1`/`RCE2`, so a naive split would derive
cross-set subsumption anyway and the same inference could be produced by two phases.

## Decision

Reasoning runs in **two independent phases** over the asserted mappings:

- **Phase 1 — OWL reasoning (`owl.rls`), applied per mapping set.** The rules that derive
  `rdfs:subClassOf` / `rdfs:subPropertyOf`: `RCE-N1`–`RCE-N4` plus `subClassOf` (T4) and `subPropertyOf` (T5)
  transitivity. [ADR-0001](0001-inference-scope-per-mapping-set.md)'s per-set rule is **retained here**.
  Produces *OWL inferences*.
- **Phase 2 — SSSOM reasoning (`sssom.rls`), applied across all mapping sets.** Transitivity and role chains
  (`RCE1`/`RCE2`) over the **strong** mapping/equivalence predicates — `exactMatch`, `equivalentClass`,
  `equivalentProperty`, `sameAs`, `broadMatch`, `narrowMatch`, `crossSpecies{Exact,Broad,Narrow}Match` — plus
  the crossSpecies inverse rules (`RI3`–`RI5`). Produces *SSSOM inferences*.

The phases are **independent**: phase 2 runs over the asserted mappings only — phase-1 OWL inferences are
**not** fed into cross-set reasoning. Every explanation chain therefore bottoms out in asserted mappings of
its own phase, and OWL inferences never propagate across sets.

Phase 2 **excludes the weak/noisy predicates** `{relatedMatch, hasDbXref, closeMatch, seeAlso, rdf:type}`
from all chaining. This also resolves a standing contradiction in the former `chain-rules.rls`, whose
transitivity comment excluded those predicates but whose rules nonetheless defined `hasDbXref` and
`relatedMatch` transitivity. `RCE1`/`RCE2` are enumerated one rule per allowed predicate (rather than an
unbound `?p`) so that subsumption and noisy predicates can never propagate across sets.

## Consequences

- Cross-ontology mappings over strong predicates are now found (the findability gain), at the cost of
  departing from the SSSOM spec's per-set scoping **for phase 2 only**.
- The blowup/avalanche risk that motivated [ADR-0001](0001-inference-scope-per-mapping-set.md) is contained
  by three guards: subsumption stays per-set (no cross-ontology subclass assertions), the weak/noisy
  predicates never chain across sets, and [ADR-0006](0006-chainrules-limited-to-those-not-copying-tree.md)'s
  tree-copying rules remain omitted. Even so, phase 2 is a single large Nemo run over the whole corpus — the
  heaviest stage operationally; its input size and memory must be watched (within-run chunked tracing still
  applies).
- Phase-1 and phase-2 conclusions have **disjoint predicate sets** (subsumption vs. mapping/equivalence), so
  the same `(subject, predicate, object)` is never produced by both phases and the `inference_type` label
  ([ADR-0011](0011-inference-type-replaces-is-inferred.md)) is unambiguous.
- `chain-rules.rls` is split into `owl.rls` and `sssom.rls`; the inference stage runs two Nemo passes.
- Cross-set provenance (which source set each premise came from) can no longer be recovered from per-set
  scoping; it is carried through inference as mapping ids — see
  [ADR-0010](0010-carry-mapping-provenance-via-nquads.md).
