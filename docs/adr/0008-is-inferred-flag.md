# ADR-0008: A denormalised `is_inferred` flag distinguishes inferred from asserted

- **Status**: Accepted
- **Date**: 2026-06-05

## Context

OxO2 distinguishes *asserted* mappings (that came directly from an input SSSOM file) from *inferred* mappings
(derived by OxO applying SSSOM chaining rules) — see `/CONTEXT.md` § Glossary. Until now there was no
first-class, queryable signal for this distinction, so search and the mapping-set selector could not filter
on it.

The signals that did exist were indirect and unreliable as an API filter:

- Inferred *mapping sets* were identifiable only by the `https://www.ebi.ac.uk/spot/oxo/inferences/` id
  prefix and `mapping_tool = "OxO Inferences"` (see [ADR-0005](0005-inferences-stored-in-own-per-mapping-set.md)).
  The `mapping_set_source` field that links an inferred set to its origin was `indexed="false"` on
  `oxo2-mappingsets`, so it could not be queried at all.
- Inferred *mappings* were identifiable only by a compound predicate — `mapping_justification` of
  `SEMAPV:MappingChaining`, a populated `explanation`, and `explanation_length > 0` — complicated by mixed
  `semapv:` / `SEMAPV:` casing in the data.
- Crucially, `mapping_source` and a `semapv:MappingChaining` justification are **standard SSSOM slots that an
  input file may legitimately declare**. A curator can publish an *asserted* set whose rows carry both. So
  the SSSOM signals mean "this mapping is derived-by-chaining," which is **not** the same as "OxO inferred
  it." Driving the filter off slot presence would misclassify such asserted rows as inferred and break the
  glossary invariant *"came from the input file ⇒ asserted."*

We need a single, exact, queryable signal so that inferred-vs-asserted can be filtered through the API at
both the mapping-set level (the selector) and the mapping level (search results).

## Decision

Add a denormalised boolean `is_inferred` to **both** `oxo2-mappings` and `oxo2-mappingsets`, set **once at
dataload** from OxO provenance: `true` for documents emitted by the inference pipeline, `false` for
everything ingested from an input SSSOM file — regardless of that file's `mapping_justification` or
`mapping_source`. `is_inferred` is the **single canonical signal** for inferred-vs-asserted filtering.

The SSSOM provenance fields remain authoritative for SSSOM export and are **not** the filter flag:
`mapping_set_source` continues to link an inferred set to its source set (ADR-0005), and `mapping_source` is
now also populated on inferred mappings so downstream SSSOM consumers see correct provenance.

## Consequences

- The rule for "what counts as inferred" lives in exactly one place — the dataload writers — instead of
  being re-encoded as a compound, case-sensitive predicate in every query. Query-time casing and
  `mapping_source`-presence ambiguity disappear.
- The flag is provenance-based, so an asserted input mapping that happens to declare a chaining
  justification stays `is_inferred = false`; the glossary invariant holds even for adversarial inputs.
- The API exposes a tri-state filter (*Asserted / Inferred / All*, default *All*, so the change is purely
  additive) as a nullable `inferred` field on the mapping-search request and an `?inferred=` parameter on
  the mapping-sets endpoint, translated to an exact `fq=is_inferred:<bool>`. It is **not** routed through the
  substring `columnFilters` bus, whose `*value*` clauses are wrong for a boolean field.
- `is_inferred` is denormalised and must be kept in sync by the dataload writers — the cost of the
  single-source-of-truth and clean query. Consistent with [ADR-0002](0002-solr-as-sole-data-store.md):
  documents are denormalised at load, and schema evolution is via Solr config plus a full reindex.
- Adding the field to both schemas requires a reindex before the filter returns correct results.
- `mapping_set_source` on `oxo2-mappingsets` still needs flipping to `indexed="true"` if set-level
  provenance navigation ("which asserted set did this derive from?") is ever exposed as a query; the
  `is_inferred` filter itself does not depend on it.
