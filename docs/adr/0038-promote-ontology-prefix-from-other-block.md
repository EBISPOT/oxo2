# ADR-0038: Promote ontology prefix and name out of the OLS `other` block

- **Status**: Accepted
- **Date**: 2026-07-22

## Context

The OLS SSSOM extracts were extended to carry, per mapping set, the CURIE prefix and human-readable
name of the ontology the set was extracted from. These arrive in the TSV metadata header inside the
SSSOM `other` slot — a free-form extension bag for non-spec provenance — as YAML:

```yaml
# other:
#   local_id: addicto.ols
#   prefix: ADDICTO
#   ontology: Addiction Ontology (ADDICTO)
```

`TSV2JSON` already binds `other` to `MappingSet.other` (a `KeyValuePairsAsString`) and serializes it
into the `oxo2-mappingsets` doc as a single JSON-encoded string field, `other`. So the prefix and name
were *already stored in Solr* — but only as opaque text inside one blob, not as fields anything
downstream could read, sort, or facet. Meanwhile each set is already tagged with its curation category
([ADR-0027](0027-config-driven-mapping-set-category.md)): OLS sets are `ONTOLOGY`, EVORA / Mapping
Commons / curated repos are `CURATED`, and the synthetic `oxo2/inferences` set carries no category.

The frontend needs to present ontology-derived sets differently from curated ones — a table of curated
sets, and a table of ontologies with their own Ontology and Prefix columns. That requires the prefix
and name as discrete, typed fields end to end, and requires the category to be visible on the
mapping-set API (it was stored but not exposed on `MappingSetSummary`).

## Decision

Promote `prefix` and `ontology` to first-class fields, derived from the `other` bag at serialization
time. `MappingSet` gains two serialize-only accessors (`prefix()`, `ontology()`) that read the
respective keys out of `other`; they are not record components and have no builder setter, so a doc
round-trips through `other` and the promotion has a single source of truth. They serialize as
top-level `prefix` / `ontology` fields (absent when there is no `other` block), which the
`oxo2-mappingsets` schema now defines (`prefix` as `string`, `ontology` as `text_general`). The raw
`other` field is kept — it still carries `local_id`. `GET /api/v2/mapping-sets` (`MappingSetSummary`)
now also returns `mapping_set_category`, `prefix`, and `ontology`. The frontend splits the mapping-set
picker into a Curated table (curated sets plus the uncategorised inferences set) and a lean Ontologies
table (Ontology + Prefix), keyed on `mapping_set_category`.

## Consequences

- The promotion is OLS-shaped: it lifts exactly the `prefix` and `ontology` keys. Other `other` keys
  (e.g. `local_id`) stay inside the blob. A different producer that reuses the `other` slot for
  unrelated keys is unaffected; one that happens to use `prefix`/`ontology` for a different meaning
  would be surfaced under these columns — acceptable given `other` is provenance-only.
- `prefix` / `ontology` populate only on a reindex: the schema fields and serialization are new, so
  existing `oxo2-mappingsets` docs carry them only after the next dataload. The category-driven table
  split works against the current index immediately (category was already stored); the Ontology/Prefix
  cells are blank until the reload.
- Deriving from `other` rather than from a new builder input keeps the change off the `MappingSet`
  canonical constructor and every positional caller. The cost is that the two fields are serialize-only
  and cannot be set independently of `other` — correct here, since `other` is their sole source.
- The inference-Type column was dropped from both picker tables: there is only ever one inferred set,
  so the column was near-constant. Inference type remains available via the set's `inference_type`
  field and the inference-type filter.
