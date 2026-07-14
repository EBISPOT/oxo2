# ADR-0033: Well-founded explanations — no derived copy of an asserted triple

- **Status**: Accepted
- **Date**: 2026-07-14

## Context

Explanations shipped with inferred mappings could be **circular**: the conclusion appeared inside its
own proof. Observed on `mapping_id=e938e96b-5c52-3887-9ad9-e8e69b9fcc7a`
(`UPHENO:0049904 —crossSpeciesNarrowMatch→ HP:0001939`), whose chain contained

```
UPHENO:0049904 —crossSpeciesNarrowMatch→ MP:0005376   (via RI5)
  MP:0005376   —crossSpeciesBroadMatch→  UPHENO:0049904 (via RI4)
    UPHENO:0049904 —crossSpeciesNarrowMatch→ MP:0005376 (ASSERTED)
```

and, on its other branch, the same shape via `SYM-exactMatch` twice. A user reading the proof is told
that a fact holds *because it holds*.

The cause is not Nemo, and not [ADR-0029](0029-canonical-entity-iri-overrides.md) IRI aliasing (the
entities above are distinct raw IRIs). It is the interaction between two existing decisions:

- The rules carry the `mapping_id` **inside** the derived atom ([ADR-0010](0010-carry-mapping-provenance-via-nquads.md)):
  asserted facts are `mapping(<urn:uuid:…>, s, p, o)`, rule-derived ones are `mapping(<nil-uuid>, s, p, o)`.
- Several rules are **involutions**: `SYM-*` applied twice, or `RI4` then `RI5` (and `RI1`/`RI2`),
  return to the original triple.

So the chase could derive a *nil-UUID copy* of a triple that some set already asserts. To Nemo these
are two distinct atoms, and its trace DAG over them is perfectly acyclic — it is entitled to prove
the nil-copy by the involution round-trip. But `ExplainInferredMappings` projects the `mapping_id`
away to display an S-P-O triple, the two atoms **fold into one**, and the proof becomes circular.

The rule body `mapping(?id1, ?a, ?p, ?b)` binds `?id1` freely, so a rule such as `RCE2-2b` was free to
consume the redundant nil-copy rather than the asserted fact it was always entitled to use.

This was not a rare corner: the committed golden
`testcases_expected_output/minimal/RCE2_2/solr/mapping/inferences-explained.json` contained the same
double cycle and had been asserted as *expected* output, because the integration test compares the
explanation layer as text and a text diff cannot tell a correct proof from a circular one.

## Decision

**Every derivation rule in `sssom.rls` carries a `~assertedTriple(s, p, o)` guard on its own head**, so
the chase never derives a nil-UUID copy of a triple that any set asserts. `assertedTriple/3` already
existed (it is the projected EDB view used by `inferredMapping`), and negation over it is stratified,
so this needs no new machinery.

Additionally, `ChainRulesIntegrationTest` asserts **structurally** that no explanation restates an
ancestor's S-P-O, per fixture — this is not left to the golden-file diff.

## Consequences

**Cyclic proofs become impossible, rather than merely absent.** The guard makes the atom → triple
projection collapse-free: for any triple, *either* it is asserted (only leaf atoms, which have no
premises) *or* it is derived (exactly one nil atom). Every route to a cycle is then closed:

- two derived nodes sharing a triple would mean an atom is its own ancestor — Nemo's DAG forbids it;
- a derived ancestor with an asserted descendant restating it — the guard prevents that copy existing;
- an asserted ancestor — it is a leaf, so it has no descendants at all.

This is the invariant `ExplainInferredMappings.hasFoldedCycle` always assumed but never enforced. That
detector stays, now covering both remaining regression routes: a lost head guard (this ADR) and an
aliased entity IRI ([ADR-0029](0029-canonical-entity-iri-overrides.md)).

**The inference set is unchanged.** The guard is output-preserving, not a semantic narrowing. The
suppressed nil-copies are exactly the triples `inferredMapping` already excludes via `~assertedTriple`,
and every rule body still matches the asserted copy, so the fixpoint over *triples* is identical.
Verified by diffing the `inferredMapping` export with and without the guards over a 120-quad corpus
spanning all ten predicates: 244 inferred triples, byte-identical.

**Proofs get shorter, and the goldens move.** Each eliminated involution round-trip removes two nodes
from a chain. The reported mapping's `explanation_length` drops from 7 to 3, and `RCE2_2`'s from 7 to
3. `explanation_length` and `distance` are recomputed from the DAG, so both can change for any mapping
whose proof previously routed through a redundant copy; the affected goldens are re-baselined in the
same change.

**The chase gets slightly cheaper.** The redundant copies are never materialised (36 → 34 facts on the
two-fact repro; 12246 → 12131 on the 120-quad corpus).

**Explanations must be recomputed for existing indexes.** They are precomputed by the dataload
([ADR-0028](0028-component-sharded-explanation-precompute.md)), so already-indexed mappings keep their
circular chains until the dataload is re-run. The conclusions themselves were never wrong, so this is
a proof-quality re-index, not a correctness emergency.
