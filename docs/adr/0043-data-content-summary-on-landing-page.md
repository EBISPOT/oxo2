# ADR-0043: Data Content summary on the landing page, with a dataload-stamped release date

- **Status**: Accepted
- **Date**: 2026-07-30

## Context

The landing page told a visitor nothing about what OxO2 actually holds. The home grid is
`lg:grid-cols-4` with the search and the info cards in `col-span-3`, so a right-hand column already
existed and stood empty.

Six of the seven figures wanted — total mappings split into asserted and inferred, total mapping sets
split into curated sets and ontologies — were already derivable from single-valued string fields
(`inference_type`, `mapping_set_category`) in the existing collections.

The seventh, the date of the current data release, did not exist anywhere. `oxo-config.json` carries
only `min_inference_confidence` and `mapping_registries`; no Solr schema, dataload stage, or API
recorded when a corpus was loaded. The `oxo2-mappingsets` schema has `publication_date` and
`mapping_date`, but those are per-set SSSOM properties describing the *source* set, not OxO2's
release, and no loaded fixture populates them. Three sources were considered:

- **Solr's `index.lastModified`** (via CoreAdmin STATUS) — no reindex needed, but it reports the last
  write to a Lucene index, not a declared release. It moves when a single core is touched, so a
  partial reindex would advertise a new release that never happened.
- **An operator-declared key in `oxo-config.json`** — explicit and cheap, but hand-maintained and free
  to drift from what is actually indexed. A config file cannot be wrong about the data it describes
  and still be useful.
- **A dataload stamp** — the release date becomes a property of the loaded data itself.

A further constraint shaped the query design. The existing SSSOM `/stats` endpoint computes
`unique(entity_id)` over the whole mappings collection; a full-collection `unique()` facet has
crash-killed a 4 GB local Solr on the real corpus. A landing-page widget is the worst possible place
to repeat that.

## Decision

The dataload stamps a `data_release_date` on every mapping set it writes, from **one run-level UTC
timestamp** resolved by the orchestrator (`oxo2_resolve_release_date` in the shared
`loadData.lib.sh`), and `GET /api/v2/data-content` serves the Data Content block from three cheap Solr
queries — the newest `data_release_date`, an `inference_type` facet, and a `mapping_set_category`
facet over asserted sets only.

## Consequences

**The release date is a property of the data, not of the config or the filesystem.** It travels with
the corpus: a `oxo2-mappingsets` collection carries its own release date, and no separate bookkeeping
can disagree with it.

**It reads `null` until the next full dataload.** Every currently indexed set predates the field. The
endpoint returns `releaseDate: null` and the frontend omits the row rather than showing "unknown" —
an absent release date is honest, an invented one is not. This is the same
reindex-to-go-live shape ADR-0041 accepted for the obsolete flags.

**One timestamp per run, minted by the stage that writes it.** `sssom2json` runs one JVM per TSV, so a
per-task `date` call would stamp mapping sets seconds apart and "the release date" would depend on
which set you asked. `oxo2_resolve_release_date` therefore mints a fresh value only when the
`sssom2json` stage will actually run, and a resume entering *later* reuses the value persisted in
`$OXO2_DATA/.oxo2-data-release-date` — the mapping-set JSON on disk already carries it, and minting a
new one would claim a release the indexed data does not have. `OXO2_RELEASE_DATE` may be exported
beforehand to pin a reproducible rebuild.

**The date is carried as an ISO-8601 string, not a `Date`.** The dataload serialises with a plain
`ObjectMapper`, which writes `java.util.Date` as epoch millis — a value Solr's date field cannot
parse. Passing the already-ISO string through verbatim removes the conversion entirely; the JAR
validates it with `Instant.parse` so a malformed value fails the stage loudly instead of surfacing
later as a Solr `Invalid Date String` on the index POST.

**The newest value wins, read by sort rather than by `max()`.** The query filters to
`data_release_date:[* TO *]` and sorts descending, so with no missing values in the result the first
document is the newest release regardless of how the date field orders documents that lack the field.
Taking the newest — instead of assuming the single shared value — is what makes a partial reload
report correctly.

**Nothing in this endpoint scales with corpus size.** Every count is a `numFound` or a bucket of a
facet over a single-valued string field. There is deliberately no `unique()` aggregation and no
same-SPO collapse: `mappings.total` counts mapping *documents*, not distinct subject-predicate-object
triples, because the distinct-triple count needs a `CollapsingQParser` over the whole collection
(the cost that already forced `groupBySpo=false` on the set-detail views, ADR-0023). A
`DataContentControllerTest` case asserts the absence of `json.facet` so this cannot regress silently.

**Mapping-set counts exclude the synthetic inferences set.** It is a real document in
`oxo2-mappingsets` carrying `inference_type = SSSOM_INFERENCE`, but it is not a loaded corpus, so the
counts filter to `inference_type:ASSERTED` and `curated + ontologies` accounts for `total`. A set
indexed before `mapping_set_category` existed falls into neither bucket, so the two can sum to less
than the total until the next full dataload.

**`ontologies` is the ONTOLOGY-category set count, not the `/api/v2/ontologies` count.** The latter
facets every CURIE prefix appearing anywhere in the mappings index — including prefixes OxO2 holds no
mapping set for — so it is a larger number that would not sum with curated sets. The two endpoints
answer different questions and must not be reconciled.

**The block fails silent.** A summary is decoration; if the endpoint errors the component renders
nothing rather than putting an error box beside the search form, which still works.
