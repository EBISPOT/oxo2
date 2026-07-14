# ADR-0034: A per-entity Solr collection for typeahead

- **Status**: Accepted
- **Date**: 2026-07-14

## Context

OxO2 has no autocomplete anywhere. The main search box, the result-table column filters and the
48-field Advanced tab are all bare text inputs: you must already know a CURIE or an exact label, and
in the filter boxes you type blind and hope the value you are typing exists in the result set.

The obstacle is the shape of `oxo2-mappings`. It is **denormalised** — one document per mapping —
so an entity appears in as many documents as it has mappings, its label and IRI copied onto each. The
index has no per-entity view of the corpus at all: "the set of distinct entities" is not a thing you
can cheaply ask it for, and the closest existing artefact, the `entity_id` copyField added for the
`/stats` HLL count ([ADR-0032](0032-sssom-spec-api.md)), is `indexed="false" stored="false"` and so
unusable for lookup.

Nor is there any prefix-matching infrastructure. The only n-gram fields are the `*_ngram` twins, and
they are **infix** n-grams (`NGramFilterFactory`, min 1 max 35) built to back the "contains" column
filters. There is no `EdgeNGramFilterFactory`, no `SuggestComponent` and no `/suggest` handler
anywhere in the repo — the stock Solr spellcheck config in `solrconfig.xml` is the untouched example
and is wired to nothing.

The corpus is static between dataloads ([ADR-0002](0002-solr-as-sole-data-store.md),
[ADR-0003](0003-nextflow-as-sole-dataload-path.md)), so a precomputed, denormalised read model is
cheap to build and free to query.

## Decision

### 1. A third collection, `oxo2-entities` — one document per distinct entity

Fields: the CURIE (`id`, uniqueKey), `label`, `iri`, `prefix`, `subject_count` / `object_count` /
`mapping_count`, and `is_subject` / `is_object`. Derived by a new `mappings2entities` dataload stage
that streams the already-indexed `oxo2-mappings`, sharded by CURIE prefix (`subject_prefix` /
`object_prefix`, [ADR-0024](0024-cross-ontology-mapping.md)), so each shard's heap is bounded by one
ontology's entity count rather than the whole corpus.

The stage runs **after `index-inferred`**, not before: an inferred mapping's subject may appear only
as an *object* in the asserted data, and deriving earlier would drop it from the subject-side suggest.

### 2. Prefix matching by two new **edge**-n-gram field types

- `text_edge_ngram` — StandardTokenizer → LowerCase → EdgeNGram. **Prefix-of-any-token**, so typing
  `mel` surfaces "malignant melanoma", not just labels that literally start with "mel".
- `string_edge_ngram` — KeywordTokenizer → LowerCase → EdgeNGram. **Whole-string prefix**, so the `:`
  in `MONDO:0000001` is not a token boundary.

N-grams at index time, plain analysis at query time. Whole-string prefix is boosted above token
prefix, so "melanoma" outranks "familial atypical melanoma" for `mel`, and `mapping_count` is applied
as a **multiplicative** popularity boost (`boost=`, never an additive `bq` — the same shape as the
four multiplicative tiers of the provenance-led ranking in
[ADR-0027](0027-config-driven-mapping-set-category.md)).

`iri` deliberately carries **no** edge-n-gram. Every OBO IRI begins
`http://purl.obolibrary.org/obo/`, so the n-grams for the first ~31 characters would each have a
posting list containing every OBO entity — pathological, and useless as a discriminator. `iri` is a
plain `string` (exact match, which is what pasting a full IRI wants) and rides along on the suggestion
for display.

### 3. **Not** Solr's `SuggestComponent`

It is the obvious tool and it is the wrong one here:

- **It cannot take an `fq`.** Suggester lookups are not queries (`contextField` covers one field, and
  only on `AnalyzingInfix`). So it could not honour ADR-0030's subject-side restriction, nor the
  ontology / corpus / inference / mapping-set filters that every other read path in OxO2 respects.
- **Over a denormalised index it suggests the wrong unit.** A `DocumentDictionary` iterates
  *documents*, so a high-degree entity would be emitted once per mapping, and its "weight" would be an
  artefact of the schema rather than a property of the entity.
- **It needs a dictionary build step** — a fourth thing the dataload has to keep in sync, with
  `buildOnCommit` / `buildOnStartup` costs on a corpus this size.

A plain edismax query over a properly-modelled collection gives prefix matching, dedup, payload,
ranking **and** full filter support for free, and it is testable with the `SolrQueryBuilderTest`
machinery that already exists.

### 4. Three surfaces, three mechanisms — deliberately not uniform

Suggestion quality is a function of field **cardinality**, so a single mechanism would be wrong
somewhere:

| surface | mechanism | why |
|---|---|---|
| Main search box | global entity suggest over `oxo2-entities`, **subject-side only** (`is_subject:true`) | consistent with [ADR-0030](0030-subject-side-default-search.md) |
| Result-table column filters | a **contextual** `facet.prefix` scoped to the *live query* | a suggestion can then never yield zero rows, and it carries the count of mappings behind it |
| Advanced search (48 fields) | **cardinality-tiered**: entity fields → `oxo2-entities`; controlled vocabularies → one global distinct-values facet, cached, filtered client-side; free prose → nothing | a suggester over millions of labels and a suggester over the ~5 values of `predicate_modifier` are not the same problem; a typeahead over `comment` is noise |

The contextual tier is built by **reusing `SolrQueryBuilder.buildSolrQuery`** and turning its result
into a `rows=0` facet request, so the column filters, the weak-predicate exclusion and the
corpus/inference/prefix/mapping-set restrictions are the *same objects* the `/search` path produces —
never a second implementation of them.

The vocabulary tier reuses the pattern the ontology-prefix selector already established
([ADR-0024](0024-cross-ontology-mapping.md)): fetch the distinct values once, cache them forever,
filter client-side.

### 5. Picking a suggestion is not the same act as typing

Picking applies an **exact** filter; typing free text and submitting keeps the existing **contains**
behaviour. A picked value came *out of* the index and is unambiguous — applying "contains" after a
user explicitly picked "melanoma" would silently also return "familial melanoma" and "melanoma of
skin", values they did not pick. A new `FilterMatchType` (`CONTAINS` default, `EXACT`) carries the
distinction on `ColumnFilter` and `FieldQuery`. `EXACT` targets the whole-value `_str` twin for
`text_general` fields and the field itself for `string` fields — deliberately the case-*sensitive*
twin, not ADR-0026's case-folding `_ci`: the value came verbatim out of the index, so there is no
case to be lenient about. Defaulting to `CONTAINS` leaves every existing caller unchanged.

### 6. Facets read whole-value fields that preserve the original casing

A facet returns the field's **indexed terms**. Two consequences drive the field choice:

- Faceting a `text_general` field returns its *analyzed tokens*, not its values — an autocomplete
  built on it would offer "the" and "disease" as completions of a mapping set title.
- Faceting a case-folding field (the `_ci` twins of [ADR-0026](0026-configurable-label-match-mode.md))
  returns *lower-cased* values. Those are unusable as suggestions: `mondo:0005148` is not how a CURIE
  is written, and echoing a folded label back as the value the user picked is wrong.

So the facet tiers read whole-value, original-casing fields only. Most already exist: the plain
`string` fields (`predicate_id`, `object_id`, `predicate_iri`, `object_iri`, `mapping_justification`,
`mapping_provider`) and the `*_label_str` twins ADR-0026 added. `oxo2-mappings` gains just **seven**
new `_str` twins, for the `text_general` vocabulary fields that had none: `subject_category`,
`object_category`, `mapping_tool`, `mapping_set_title`, `author_label`, `creator_label`,
`reviewer_label`. They are explicit `string` fields, not the dynamic `*_str` (which is
`indexed="false"`, docValues-only — facetable but never filterable), so a picked value can also be
filtered back.

No `_ci` twins are added, and none were needed. The consequence is that `facet.prefix`, which is a
raw byte prefix over the term dictionary, is **case-sensitive** — see Consequences.

## Consequences

- **A third collection to create, ship, archive and keep in sync.** `copySolrConfig.sh` (which
  hard-codes its cores), both Dockerfile `COPY` allowlists (hand-maintained — a missing helper passes
  local and CI but fails HPC with exit 127), the `docker-compose` healthcheck, and
  `SolrLifecycle.COLLECTIONS` in the integration harness. It rides along in `solr-data.tar.gz` for
  free, since the `archive` stage tars all of `$SOLR_HOME`.
- **`oxo2-entities` is a read model, and that is the point.** It can be rebuilt on its own with
  `START_STAGE=mappings2entities` against an already-indexed `oxo2-mappings` — no re-inference, no
  re-explanation, no reindex. Correcting a label-derivation bug costs minutes, not the 6 CPU-hours of
  [ADR-0028](0028-component-sharded-explanation-precompute.md)'s explain stage.
- **This change does need one reindex, but a bounded one.** The mappings schema gains seven `_str`
  fields and their copyFields, so the mappings must be re-posted: `START_STAGE=index-asserted`, which
  re-runs the `json2solr` posting stages against the JSON already on disk and **preserves** `download`
  / `sssom2json` / `nquads` / `infer` / `shard` / `explain`. The expensive half of the pipeline — the
  6 CPU-hours of ADR-0028's explain — is not re-run. Until that re-post happens, the seven `_str`
  fields are empty, so the Advanced-tab vocabulary suggest for those seven fields returns nothing
  (it degrades to a plain text input rather than breaking).
- **The column-filter tier is whole-string prefix, the main box is prefix-of-any-token.** `facet.prefix`
  is a raw term prefix and offers nothing else. Accepted asymmetry: making the column filters
  token-prefix too would mean edge-n-gramming `oxo2-mappings` itself, and the counts — which are the
  reason the contextual tier exists — come free only from a facet.
- **`facet.prefix` is a raw byte prefix: neither analyzed, nor query-parsed, nor case-folded.** Two
  sharp edges follow, both pinned by unit tests. It must **never** be `escapeQueryChars`-escaped, or
  literal backslashes end up in the term. And because the field it reads preserves original casing (it
  must — see Decision 6), a case-sensitive prefix would miss "Melanoma" for a user typing `mel`. The
  contextual tier therefore issues the prefix under a small set of casing variants (as-typed,
  lower, Title, UPPER) and merges the buckets. This covers real-world label and CURIE casing; it does
  **not** cover a mid-token camelCase boundary (typing `oboinowl` will not find `oboInOwl:hasDbXref`).
  Accepted: the alternatives are to lose the original casing (faceting a `_ci` field returns folded
  values, which are wrong to display or filter on), to add docValues plus a JSON-facet `min()`
  aggregation to recover casing, or to pay a second round-trip. A documented gap on mid-token
  camelCase is the cheapest honest option; the affected values are reachable by typing them as-cased.
- **The first facet after a restart is slow.** The `string` fieldType declares no docValues, so the
  first `facet.field=object_id` uninverts the field. The index is static between dataloads, so it is a
  one-off per searcher, warmed by an `ApplicationReadyEvent` listener in the backend rather than a
  `firstSearcher` listener in `solrconfig.xml` — a solrconfig change would only be picked up by
  `copySolrConfig`, i.e. only on a wipe.
- **Two Advanced fields were found to be dead.** `issue_tracker_item` and `asserted_mappings` are
  `indexed="false"` (the latter deliberately, ADR-0028), so filling them in the Advanced tab has always
  returned nothing, silently. The field-classification pass surfaced this; they are removed from the
  Advanced field list.
