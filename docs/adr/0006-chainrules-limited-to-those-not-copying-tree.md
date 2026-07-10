# ADR-0006: Chain rules limited to those that do not copy tree

- **Status**: Accepted
- **Date**: 2026-05-05

## Context

Rules such as:
1. `RI1: (:A)-[skos:narrowMatch]->(:B) -> (:B)-[skos:broadMatch]->(:A)`
2. `RI2: (:A)-[skos:broadMatch]->(:B) -> (:B)-[skos:narrowMatch]->(:A)`
3. `RG1: (:A)-[owl:equivalentTo]->(:B) -> (:A)-[skos:exactMatch]->(:B)`
4. `RG2: (:A)-[owl:subClassOf]->(:B) -> (:A)-[skos:broadMatch]->(:B)`
do not strictly add addition information. That is, without these, searching on `:A` and `:B` will return at least *some
mapping* between `:A` and `:B` (or then `:B` and `:A`). Moreover, these rules result in complete trees being copied resulting in an 
exponential growth of space and time resources needed for reasoning and makes reasoning impractical.

## Decision

As some relevant mapping can already be found without application of these rules, we omit these rules. 

## Consequences
1. For well-designed ontologies, this is unlikely to have any effect as they will either use SKOS or OWL consistently. Or when used together,
there is no intention to derive OWL inferences from SKOS, or conversely SKOS from OWL.
2. Where these are mixed, it can miss inferences, particularly transitive inferences. E.g. 
```
(:A)-[owl:equivalentTo]->(:B)
(:B)-[skos:exactMatch]->(:C)
```
will not result in the inference that `(:A)-[skos:exactMatch]->(:C)`, as will be the case if rule RG1 is applied.
3. Ideally the SSSOM specification should be extended to allow mapping set creators to specify the chain rules that are applicable to their mappings.

## Note (2026-07-10): ADR-0028 does not lift the chase risk for RG2

Two developments since 2026-05-05 could suggest this ADR is due for reconsideration; neither actually
reopens RG2.

First, the RI1/RI2 half of the excluded set is **already back**. The reasoning redesign restored
skos:broadMatch ↔ skos:narrowMatch inversion as named rules `RI1`/`RI2` in `sssom.rls`. Those are
linear (one inverse edge per asserted edge), not tree-copying, so they never carried this ADR's cost.
What remains excluded from the original four is **RG1** (`owl:equivalentClass → skos:exactMatch`) and
**RG2** (`owl:subClassOf → skos:broadMatch`).

Second, [ADR-0028](0028-component-sharded-explanation-precompute.md) made explanation cheap again by
tracing each conclusion against its own strong-predicate *component* rather than the whole
materialisation. It is tempting to read that as "the resource objection in this ADR is now obsolete."
It is not, because **this ADR's cost is in the chase and in space, not in tracing.** ADR-0028
accelerates only the trace; it states plainly that "the chase *is* the clique-closure work" and does
not speed it up. Worse, ADR-0028's cheapness *depends* on components staying small (the loaded corpus'
largest is 434 nodes) — a premise RG2 would destroy.

The loaded dev corpus makes RG2 concrete: it is **57% `rdfs:subClassOf` — 4,095,449 of 7,224,812
quads** — all of it inert today because no rule fires on subsumption edges. Adding RG2 would turn every
one of those into `skos:broadMatch`, and `T9` (broadMatch transitivity) would then materialise the full
transitive closure of the subclass hierarchy — the ncbitaxon-scale ~50.6M-row explosion
[ADR-0016](0016-single-pass-sssom-reasoning.md) measured and dropped subsumption to avoid. Because
`skos:broadMatch` is a strong predicate, that closure also fuses the entire subclass hierarchy into one
giant connected component, so the component-sharded chase+trace of
[ADR-0028](0028-component-sharded-explanation-precompute.md) would degenerate to a single unbounded
shard.

RG1 is a different case — `owl:equivalentClass` and `skos:exactMatch` are both already strong,
transitive, and symmetric, so RG1 only merges two identity components rather than copying a tree, and
ADR-0028 would trace it cheaply. It is not reinstated here because it earns no findability this ADR's
original reasoning does not already cover, not because of cost.

**RG2 therefore stays excluded on chase-and-space grounds that are independent of, and untouched by,
the ADR-0028 explanation rework.** Reconsidering it would require a component-size guard on the chase
(cf. ADR-0017's cross-set closure component-size guard), not a cheaper trace.
