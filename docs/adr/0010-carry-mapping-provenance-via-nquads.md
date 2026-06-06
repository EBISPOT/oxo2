# ADR-0010: Carry mapping provenance through inference as `urn:uuid` named graphs (N-Quads)

- **Status**: Accepted
- **Date**: 2026-06-06

## Context

Chaining facts were bare RDF triples `<s> <p> <o>` imported into Nemo via `turtle{}`; `chain-rules.rls`
explicitly kept facts to `(subject, predicate, object)` to keep the rules simple. Provenance — which asserted
mapping (and therefore which source set) produced a given premise — was reconstructed *after* inference by
re-querying Solr for the `(subject, predicate, object)` triple **scoped to the single source set** and taking
the first match (`NemoHelper.createInferredMapping` → `DataloadSolr.querySubjectPredicateObjectIRI`).

With cross-set inference ([ADR-0009](0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md)) there is no
single source set to scope by, and one triple may be asserted in many sets — so triple-matching can no longer
attribute a premise to its exact mapping or set.

## Decision

Carry each mapping's identity through inference. The dataload emits **N-Quads** facts
`<s> <p> <o> <urn:uuid:MAPPING_ID> .`, where `MAPPING_ID` is the mapping's existing content-and-set-derived
UUID (`generateMappingUuid`), wrapped as a `urn:uuid:` IRI. Nemo imports N-Quads in **graph-first** order, so
facts arrive as `assertedMapping(?id, ?s, ?p, ?o)`. Inferred conclusions carry the **nil UUID**
`urn:uuid:00000000-0000-0000-0000-000000000000` as the id sentinel.

Rule atoms are 4-arity throughout. The asserted/inferred separation **projects the id away before the EDB
negation** — a 3-arity `assertedTriple(?s,?p,?o) :- assertedMapping(?id,?s,?p,?o)` view — so the negation
keeps its meaning ("is this triple asserted in *any* set?") and the program stays stratified/terminating. The
trace therefore exposes each premise's real UUID, and the explanation builder resolves provenance by an exact
`mapping_id` lookup in Solr — replacing the triple-match — which requires `mapping_id` to be `indexed`.

Applies to **both** phases.

## Consequences

- Provenance is exact and multi-set-aware: every premise resolves to its precise asserted mapping and source
  set, enabling cross-set explanation chains and per-node source-set display
  ([ADR-0009](0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md)).
- Reverses the `chain-rules.rls` "keep facts to `(s,p,o)`" decision: rule atoms gain an id term, the fact
  files move from Turtle to N-Quads, the inferred export and the trace-input/trace-output parsers all carry
  the id, and `mapping_id` flips to `indexed="true"` on `oxo2-mappings` (needs a reindex).
- The nil-UUID sentinel distinguishes "asserted leaf (look up by id)" from "inferred intermediate (recurse)"
  while walking the trace.
- The nil-UUID lives **only** in the rule facts. Final inferred-mapping *documents* still receive a real
  `generateMappingUuid` id scoped to their inference set (as today).
