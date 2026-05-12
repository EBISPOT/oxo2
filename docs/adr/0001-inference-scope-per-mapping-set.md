# ADR-0001: Inference scope is per mapping set

- **Status**: Accepted
- **Date**: 2026-05-12

## Context

OxO2 generates inferred mappings by running the Nemo rules engine over SSSOM chaining rules (see `oxo2-shared`'s `ChainRulesEnum` and `oxo2-json2inferences/chain-rules.rls`). A naive performance optimisation would be to merge the TTL inputs of multiple mapping sets before invoking Nemo, amortising rule materialisation across sets.

The SSSOM specification defines chaining rules as operating over *a given mapping set*, with rule metadata recorded against that set:

> "The idea is to provide the functionality to apply these chaining rules over a given mapping set, and record the appropriate metadata for that rule."
>
> — https://mapping-commons.github.io/sssom/dev/chaining-rules/

Merging mapping sets before inference would apply rules across sets that the spec scopes to one set, and the resulting derived mappings could not be correctly attributed to a single source set.

## Decision

OxO2 runs inference and explanation **per mapping set**. The dataload invokes Nemo once per SSSOM mapping set (with within-set chunking permitted for tracing parallelism). TTL inputs from different mapping sets are never merged before Nemo runs.

## Consequences

- Cross-mapping-set chains are not produced. If users want to chain mappings across sets, that is a separate feature requiring an explicit decision about provenance semantics.
- The parallelism lever for the inference stage is *within* a set (chunking the trace step, parallelising independent sets across workers), not *across* sets.
- Suggestions to merge SSSOM inputs to speed up inference must be rejected as spec-violating, not merely as poor engineering.
- `oxo2-json2inferences` is structured around per-set invocations: `inferAndExplainMappings.nf` iterates mapping sets, and tracing is chunked within each set (default chunk size 100 000, see `/CONTEXT.md` § Cross-cutting).
