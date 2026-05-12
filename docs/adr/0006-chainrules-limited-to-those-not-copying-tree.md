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
