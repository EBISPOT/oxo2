# ADR-0027: Config-driven mapping set category (ontology vs curated)

- **Status**: Accepted
- **Date**: 2026-07-08

## Context

A novice asking "what does this term map to?" cares first about *who says so*. A cross-reference
asserted by the ontology itself (EFO's own `oboInOwl:hasDbXref` to MONDO, reaching OxO2 via the OLS
bulk SSSOM export) carries different weight from a mapping in a curated standalone SSSOM file
(EVORA, the Mapping Commons registry, a curated GitHub repository), which in turn differs from one
OxO2 inferred by SSSOM reasoning. Today OxO2 can only distinguish the third: `inference_type`
(ADR-0011) separates `ASSERTED` from `SSSOM_INFERENCE`. Nothing separates *ontology-asserted* from
*curator-asserted*, so the ranking (ADR-0011: `boost` = inference tier × distance factor) cannot
express "prefer what the ontology itself says", and the UI cannot offer "search ontologies only".

The distinction is **not an SSSOM concept**. Nothing in an SSSOM file's metadata says "I am an
ontology's own mappings"; `mapping_set_source`, `mapping_provider`, and `mapping_justification` are
all set by whoever wrote the file, are frequently absent (ADR-0015 synthesises metadata for bare
SSSOM), and none carries this meaning. Nor can it be derived from the OxO config entry *type*: both
the OLS bulk export and EVORA are plain `url` entries.

It is, however, knowledge the OxO2 operator has when they add a registry. That is where it belongs.

## Decision

Add an optional **`category`** key to each `mapping_registries` entry in the OxO config, with the
values `ontology` and `curated`. The operator tags each registry once:

```jsonc
{ "id": "ols_mappings",    "url": "…/sssom.tgz",  "category": "ontology" }
{ "id": "evora",           "url": "…/…sssom.tsv", "category": "curated"  }
{ "id": "mapping_commons", "mapping_commons_registry": "…", "category": "curated" }
```

The dataload stamps the tag onto every mapping and mapping set of that registry, stored as the code
string in the new Solr `mapping_set_category` field on **both** `oxo2-mappings` and
`oxo2-mappingsets`. `MappingSetCategory` (`oxo2-shared`) models it as `ONTOLOGY` / `CURATED`,
following the `InferenceType` pattern (`@JsonValue getCode()` / case-insensitive `@JsonCreator
fromCode()`).

**An untagged registry is `CURATED`** (`MappingSetCategory.DEFAULT`). Curated is the conservative
default: it claims no ontology endorsement a set may not have, and it is what a bare SSSOM file
dropped into a config is.

The field exists to serve two consumers:

- a **source filter** — "search ontologies / curated sets / both", defaulting to both;
- **provenance-led ranking** — ordering results by who asserts the mapping *before* how strong the
  predicate is: ontology-asserted, then curator-asserted, then inferred (nearer inferences first),
  and only then predicate strength (exact › close › broad/narrow › related), curation effort, and
  confidence.

`mapping_set_category` is **orthogonal to `inference_type`**. They answer different questions —
*which corpus is this from* and *was this asserted or derived* — and a mapping carries both. The
ranking reads them together; neither replaces the other.

## Consequences

- **Operator burden, deliberately.** A registry's category cannot be inferred, so an operator adding
  one must decide. The default keeps a forgotten tag safe rather than wrong.
- **Requires a full reindex** to populate `mapping_set_category` for existing data, exactly as
  ADR-0026's `*_label_ci` does. Until a `loadData.nextflow` run recreates the collections from
  `managed-schema.xml`, the field is empty everywhere. The feature therefore ships **dark**: the
  API's source parameter defaults to *both* (no filter clause), and an empty `mapping_set_category`
  makes the ontology-before-curated ranking tier a no-op rather than a mis-ordering. The remaining
  ranking tiers, which read fields that already exist, work immediately.
- **The category is a property of the set, not the mapping.** Every mapping in a registry gets the
  same value, denormalised onto `oxo2-mappings` so ranking and filtering need no join (ADR-0002:
  Solr is the sole store). A file that mixed ontology-asserted and curated mappings could not be
  described — no such input exists, and splitting it into two registries would be the answer if one
  appeared.
- **Inferred mappings inherit nothing.** An inference derived from premises in several sets has no
  single source category. Its rank comes from `inference_type` and `distance`, below both asserted
  tiers, so it needs none.
- **Ranking becomes explainable.** "Ontology-asserted, exact match, manually curated" is a sentence
  a novice can read off the result row; the previous multiplicative boost was not legible.

## Considered options

- **Derive it from `mapping_set_id` / `mapping_set_source`** (e.g. treat anything under the OLS
  namespace as an ontology). Rejected: it hard-codes one registry's URL shape into the model, breaks
  the moment a second ontology bulk source appears, and silently mis-classifies bare SSSOM whose
  metadata ADR-0015 synthesised.
- **Derive it from the config entry type** (`url` = ontology, `github_repository` = curated).
  Rejected: EVORA is a `url` entry and is curated. The entry type describes *transport*, not
  provenance.
- **Reuse `mapping_justification`** (`semapv:UnspecifiedMatching` and friends). Rejected: it records
  *how* a single mapping was established, not *whose corpus* it lives in. Both axes matter, and they
  are separately useful in the ranking — justification feeds the curation tier, category the
  provenance tier.
- **Rank by predicate strength first**, treating an exact match as authoritative wherever it came
  from. Rejected as the primary tier: a `skos:exactMatch` in an unreviewed lexical-match file is
  weaker evidence than an ontology's own `close` cross-reference. Predicate strength stays as the
  tier that breaks ties *within* a provenance class.
