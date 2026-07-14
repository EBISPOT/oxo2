# oxo2-backend — Module Context

See [`/CONTEXT.md`](../CONTEXT.md) for the project-wide glossary and cross-cutting constraints. This document covers what this module 
specifically owns.

## Purpose

`oxo2-backend` is the Spring Boot REST API that serves mapping and mapping-set queries to the frontend (and any other API 
consumer). It is a thin layer over Solr: requests are translated to `SolrQuery` objects, results are wrapped in DTOs and returned. 
It owns no persistence — both query and storage live in Solr ([ADR-0002](../docs/adr/0002-solr-as-sole-data-store.md)).

## Vocabulary introduced here

None. The backend uses the SSSOM and OxO2 cross-cutting vocabulary defined in `/CONTEXT.md` § Glossary, materialised through 
the Java types in `oxo2-shared`.

## Depends on

External:
- **Spring Boot 3.4.1** — REST framework, dependency injection, auto-configured Solr client wiring.
- **SolrJ** — Solr client library. Backend queries the same collections (`oxo2-mappings`, `oxo2-mappingsets`) that `oxo2-dataload` populates.

OxO2 modules:
- `oxo2-shared` — request/response shapes use SSSOM types from here, particularly `Mapping`, `MappingSet`, `InferredMapping`, 
`MappingEnum` (for Solr field names).

## Exposes

A REST API rooted at `/api/v2/`:

- **`GET /api/v2/mappings/{subjectId}`** — list mappings whose subject is `subjectId` (URL-decoded). Paged.
- **`POST /api/v2/mappings/search`** — mapping search. Body: `MappingSearchRequest` (filters,
sort, paging, and a multi-select `inferenceType` filter — [ADR-0011](../docs/adr/0011-inference-type-replaces-is-inferred.md)).
Results carry a soft multiplicative edismax ranking (asserted &gt; SSSOM; shorter chains
higher). Returns `MappingSearchResponse`. Accepts `subjectPrefixes` / `objectPrefixes` for
cross-ontology mapping (see § Cross-ontology mapping).
- **`GET /api/v2/mappings?from=DOID&to=EFO,MONDO`** — the bookmarkable source→target view: the mappings
collection filtered by source/target prefix via query params (no action verb), mirroring v1's
`GET /api/mappings?fromId=…&toId=…`. The view the frontend links to.
- **`POST /api/v2/mappings/batch-map`** — map an explicit list of input terms (CURIEs / IRIs / labels)
into target ontologies; returns the paged mappings **plus** `unmappedInputs` (supplied terms with no
mapping into the targets). Input is capped (default 1 000 terms).
- **`GET /api/v2/ontologies`** — distinct CURIE prefixes with counts (drives the Search-tab from/to
selectors); `?forSubject=<prefix>` facets `object_prefix` over that subject subset to return reachable
targets with counts.
- **`GET /api/v2/suggest/entities?q=&side=&prefix=&size=`** — the entity typeahead
([ADR-0034](../docs/adr/0034-entity-collection-for-typeahead.md)). Reads `oxo2-entities` (one document
per DISTINCT entity), not `oxo2-mappings`, which is denormalised and would suggest an entity once per
mapping. Prefix-of-any-token on the label (so `mel` reaches "malignant melanoma"), whole-string prefix
on the CURIE (so `MONDO:00` is a prefix of `MONDO:0000001`); a leading-edge match outranks a mid-label
token match, and popularity (`mapping_count`) breaks ties. `side` defaults to SUBJECT, because the
default search matches the subject side only (ADR-0030) — suggesting an object-only entity would
complete to zero rows. A query under two characters returns `[]` without touching Solr.
- **`GET /api/v2/suggest/values?field=&size=`** — every distinct value of one controlled-vocabulary
field, most common first. Meant to be fetched once and filtered client-side. Only low-cardinality fields
are permitted (400 otherwise): a global facet on an entity field would enumerate millions of terms, and
one on a `text_general` field would return analyzed tokens rather than values.
- **`POST /api/v2/suggest/values`** — the same, but **scoped to a live search**, so a suggested filter
value can never yield zero rows and arrives with the count of mappings behind it. Body wraps the very
`MappingSearchRequest` the result table is showing (see § Querying patterns).

`?format=` (default `json`; plus `sssom-tsv` / `csv` / `tsv`) on `search`, the prefix-filtered
`GET /api/v2/mappings`, and `batch-map` negotiates the representation: a non-`json` format streams the
full result (paging ignored) via a Solr cursor — SSSOM-compliant TSV with a metadata header +
`curie_map`. There is no standalone export endpoint, mirroring v1's `/api/search?format=`.
- **`GET /api/v2/mapping-sets`** — list all mapping sets (up to 10 000), returning `MappingSetSummary` (id, title, 
description, creator labels, provider, `inference_type`, source-set union). Sorted by title; optional multi-select
`?inferenceType` filter.
- **`GET /api/v2/mapping-sets/by-id?mappingSetId=<IRI>`** — fetch a single mapping set by id, backing the frontend
inferred-set resolution surface ([ADR-0012](../docs/adr/0012-resolvable-inference-set-iris.md)). A query parameter,
not a path variable, because a mapping-set id is a full IRI and Tomcat rejects the encoded slashes a path variable
would require.

Backwards compatibility with OxO v1 is mostly *behavioural* ([ADR-0004](../docs/adr/0004-backwards-compatible-with-oxo-v1.md)) — the `/api/v2/...` endpoints above use OxO2/SSSOM-shaped JSON; 
the design constraint is that v1 questions remain answerable. The **wire-compatible** exceptions live at
the literal v1 paths (not under `/api/v2`), in `controller/api/v1/`:

- **`POST /api/search`** (and `GET`): the v1 batch term-mapping endpoint, taking the v1
  `MappingSearchRequest` (`ids`, `inputSource`, `mappingTarget`, `mappingSource`, `distance`) and
  returning the v1 HAL `PagedResources<SearchResult>` envelope. See § Cross-ontology mapping
  ([ADR-0024](../docs/adr/0024-cross-ontology-mapping.md)).
- **`GET /api/mappings`**: the v1 read-only mapping listing, returning the v1 HAL
  `PagedResources<Mapping>` envelope. Optional `fromId` / `toId` filter by term, undirected as in v1;
  asserted mappings only; weak predicates (`rdfs:subClassOf` / `oboInOwl:hasDbXref`) shown by default
  with a `hideWeakPredicates` opt-in. Term `uri` is the full IRI and the mapping-level `datasource`
  identifies the SSSOM mapping set (`mapping_set_id` + title). Remaining v1 `Mapping` fields are
  documented breaks — null `mappingId`, `scope` derived from the SSSOM predicate, per-item links
  dropped. Read-only; v1's create/delete verbs and `/{id}` / `/summary` are not ported. See
  [ADR-0025](../docs/adr/0025-v1-mappings-listing-compatibility.md).

### SSSOM-API compatibility (`/api/sssom`)

A third surface implements the [mapping-commons SSSOM API spec](https://github.com/mapping-commons/sssom-api),
so ecosystem tooling can point at OxO2 with a base-URL change. See
[ADR-0032](../docs/adr/0032-sssom-spec-api.md); in `controller/api/sssom/`. Every list endpoint returns
the reference envelope `{data, pagination, facets}` with **1-based** `page` / `limit` (default 20, max
100) and absolute previous/next links; same-SPO rows are collapsed and no predicate is hidden by
default. `?format=sssom-tsv` / `tsv` / `csv` streams an SSSOM file (an OxO2 extension).

- **`GET /api/sssom/mappings?filter=field|operator|value`** — repeatable, AND-joined filters.
  Operators `eq` / `ge` / `gt` / `le` / `lt` / `contains`; `field` is any SSSOM or OxO2 extension slot.
- **`GET /api/sssom/mappings/{id}`** — one mapping by `mapping_id` (bare mapping, not the envelope; 404).
  Returns the **full document** — every stored field, not the `MINIMAL_LIST_OF_FIELDS` the list endpoints
  project — because this single-document lookup backs the frontend's mapping-details page, which renders
  provenance, mapping-set metadata, `explanation` and `asserted_mappings`.
- **`GET /api/sssom/mappings/{field}/{*value}`** — the equality shorthand of the filter grammar.
- **`POST /api/sssom/entities`** — body `{curies, mapping_justification?, predicate_id?}`; mappings
  where a curie is the subject **or** object.
- **`GET /api/sssom/mapping_sets?filter=…`** — the mapping sets (stored slots; no facets block).
- **`GET /api/sssom/mappings?mapping_set_id=<IRI>`** — the mappings of one set. The reference nests
  these as `/mapping_sets/{id}/mappings`, but the id is a full IRI (unusable as a path variable) and the
  result is mappings, not sets — so the scope is a `mapping_set_id` filter on `/mappings`, equivalent to
  `filter=mapping_set_id|eq|<IRI>`.
- **`GET /api/sssom/stats`** — `{nb_mapping, nb_mapping_set, nb_mapping_provider, nb_entity}`;
  `nb_entity` is a HLL estimate over the `entity_id` copy-field, low until the next full dataload.

`SssomQueryBuilder` builds the Solr queries (reusing `SolrQueryBuilder`'s provenance ranking and
same-SPO collapse), `SssomResultMapper` assembles the envelope, and `SssomMappingService` centralises
paging / faceting / linking / export for the mapping-returning endpoints. Facets come from Solr's
facet/stats components on the same query as the page — never by streaming rows, unlike the reference.

### API documentation (OpenAPI / Swagger)

The API is self-described via springdoc-openapi. The generated OpenAPI 3 spec is served at
**`/v3/api-docs`** and the bundled Swagger UI at **`/swagger-ui.html`** (port 8081 locally).
The top-level `info` block comes from `config/OpenApiConfig`; per-endpoint summaries, parameter
descriptions and response codes come from `@Operation` / `@Parameter` / `@ApiResponse` annotations
on the controllers, and request/response field descriptions from `@Schema` on the DTOs in
`controller/api/dto/`. The `Mapping` model in `oxo2-shared` is left un-annotated so the
swagger-annotations dependency stays confined to this module; springdoc infers its schema by
reflection. `OpenApiDocsTest` boots the full context and asserts the spec is generated and lists
every endpoint.

Two packaging gotchas are handled in `pom.xml`: (1) the fat jar's shade config appends the
`META-INF/spring/...AutoConfiguration.imports` resource so springdoc's and Spring Boot's
auto-configuration both survive the merge; (2) `io.swagger.core.v3:swagger-annotations-jakarta` is
pinned to match the swagger-core that springdoc pulls, overriding the older transitive version
SolrJ brings (which lacks `@Schema.$dynamicRef()` and would otherwise fail spec generation).

## Module notes

### Layout

- `controller/api/v2/` — REST controllers (`MappingController`, `MappingSetController`).
- `controller/api/dto/request/` — request DTOs: `MappingSearchRequest`, `FieldQuery`, `SortedField`, `SortOrderEnum`.
- `controller/api/dto/response/` — response DTOs: `MappingSearchResponse`, `MappingSetSummary`.
- `service/OxOSolrClient.java` — single SolrJ-backed service exposing `query(...)` over the `oxo2-mappings` collection,
`queryMappingSets(...)` over `oxo2-mappingsets`, and `queryEntities(...)` over `oxo2-entities`
([ADR-0034](../docs/adr/0034-entity-collection-for-typeahead.md)).
- `service/helper/SolrQueryBuilder.java` — translates `MappingSearchRequest` into a `SolrQuery` (filters, sort, paging).
Also builds the two value-suggest queries, because the contextual one has to reuse the real search query (below).
- `service/helper/EntitySuggestQueryBuilder.java` — the entity typeahead query, against `oxo2-entities`. A *peer* of
`SolrQueryBuilder`, not an extension: a different collection with a different schema and no `MappingEnum`.
- `service/helper/SuggestFields.java` — which fields may be faceted for suggestions, and which Solr field a facet on
them must actually read.
- `service/SuggestFacetWarmup.java` — warms the facet caches on startup (the `string` fields have no docValues, so the
first facet on one uninverts it).
- `service/helper/SolrConstants.java` — Solr field-name constants for this module's queries.
- `exception/GlobalExceptionHandler.java` — top-level exception translation.
- `config/OpenApiConfig.java` — OpenAPI 3 `info` metadata for the Swagger docs (see § API documentation).

### Querying patterns

Queries are built directly with SolrJ `SolrQuery`. There is no JPA, no repository abstraction, no caching layer between 
controllers and Solr — this is deliberate ([ADR-0002](../docs/adr/0002-solr-as-sole-data-store.md)). Field names come from constants in `oxo2-shared` (`MappingEnum`, 
`MappingSetConstants`) so the dataload and backend stay aligned.

#### Corpus and inference-type filters

The `inferenceType` filter (search body, and the mapping-sets `?inferenceType` param) becomes an OR of exact
`inference_type:<CODE>` term matches; an absent/empty list means all types.

The `mappingSetCategory` filter picks which **asserted corpora** to search — `ONTOLOGY` (an ontology's own
cross-references) and/or `CURATED` (a curated SSSOM file); an absent/empty list searches both
([ADR-0027](../docs/adr/0027-config-driven-mapping-set-category.md)). It is named for the Solr field rather
than "source" because `mapping_set_source` is already an SSSOM slot meaning something else. Inferred mappings
are ORed back in unconditionally: an inference chains premises from several sets, carries no category, and
would otherwise be dropped by *any* corpus choice — making this control secretly duplicate `inferenceType`.
The two axes stay orthogonal. Before the reindex that populates the field, naming a corpus returns only
inferences, which is why the default emits no clause at all.

#### Provenance-led ranking

Ranking is a **multiplicative** edismax `boost` (`SolrQueryBuilder.RANKING_BOOST`), not an additive `bq`: an
additive boost is skewed by term idf (ASSERTED is common, SSSOM rare) and would invert the intended order
([ADR-0011](../docs/adr/0011-inference-type-replaces-is-inferred.md)). Four tiers multiply together:

1. **Provenance** — ontology-asserted (10000) &gt; curator-asserted (1000) &gt; inferred (100, divided by 5 per
   extra hop). Keyed on `inference_type`, so a doc with no category reads as "asserted, corpus unknown" and
   scores as curated — which is exactly the pre-reindex state, and exactly ADR-0011's old ordering.
2. **Predicate strength** — strict identity (2.0: `owl:equivalentClass`/`equivalentProperty`/`sameAs`) &gt;
   `skos:exactMatch` (1.7) &gt; `skos:closeMatch` (1.4) &gt; broad/narrow (1.2) &gt; anything else (1.0). The
   strict-vs-weak identity split is ADR-0016's, not a second strength model.
3. **Curation** — `semapv:ManualMappingCuration` (1.3) &gt; anything else (1.0).
4. **Confidence** — `1 + 0.3 × confidence`; absent confidence contributes exactly 1.

The tiers are **lexicographic, not a blend**: the closest two provenance values differ by 5×, more than the
widest combined swing of tiers 2–4 (2.0 × 1.3 × 1.3 = 3.38), so no predicate/curation/confidence advantage can
lift a curated mapping above an ontology one — "trust provenance over predicate". `SolrQueryBuilder`
`.rankingTiersAreLexicographic()` encodes that inequality and is asserted by a unit test, so a future tweak to
any constant is checked against it.

Recency is deliberately not a boost factor: `mapping_date` is sparsely populated, and a date function in the
boost would silently reorder results on every query. It is available as an explicit sort instead.

#### Same-SPO grouping

When the request sets `groupBySpo` (the normal/inferences result tables do; the Advanced tab does not),
`SolrQueryBuilder` adds Solr result grouping on the `spo_key` field — `group=true`, `group.ngroups=true`,
`group.limit`, `group.sort=score desc` (the representative is the highest inference-tier member, via the
boost above), and `spo_key` is appended as the final sort key so paging is a stable total order. The page
total becomes the **group count** (`getNGroups`), so a page is N triples not N documents. `OxOSolrClient`
turns each group's top document into the representative row and attaches its members + true size as a
`group_members` JSON string (`{"total":N,"members":[...]}`), serialised with the app `ObjectMapper`, leaving
the `MappingSearchResponse` / `Page<Mapping>` shape unchanged. Grouping sits on top of the filters, so a
group's members reflect only what passed the inference-type filter. See
[ADR-0013](../docs/adr/0013-group-same-spo-mappings-in-result-views.md).

#### Cross-ontology mapping

Mapping a source ontology to target ontologies ([ADR-0024](../docs/adr/0024-cross-ontology-mapping.md))
is a **directional prefix filter** over the existing mappings index, not a graph traversal — the SSSOM
cross-set closure is already materialised at dataload ([ADR-0016](../docs/adr/0016-single-pass-sssom-reasoning.md)).
`subjectPrefixes` / `objectPrefixes` on the search request become OR'd exact-term filter queries on the
denormalised `subject_prefix` / `object_prefix` fields (subject = source, object = target); the
bookmarkable `GET /api/v2/mappings?from=&to=` sets the same two filters from query params. `GET
/api/v2/ontologies` faceting on the same fields drives the count-laden from/to selectors.
`batch-map` runs the same filters with the input terms classified by shape (CURIE → `subject_id`,
IRI → `subject_iri`, label → `subject_label` as a partial match — batch-map does **not** expose the
normal search's label-match mode, see [ADR-0026](../docs/adr/0026-configurable-label-match-mode.md)),
then computes `unmappedInputs` as the input set minus the matched subjects.

The v1 `POST /api/search` adapter builds the same Solr query, regroups the flat hits **by input term**
into `SearchResult` / `MappingResponse`, and maps v1's `distance` onto the inference-type tiers —
`distance=1` → `inference_type:ASSERTED` only; `distance≠1` (incl. `-1`) → no tier filter
(`ASSERTED ∪ SSSOM_INFERENCE`), so "unlimited" still returns the direct mappings. OxO2 has no
query-time hop count, and the stored `distance` is an ontology span rather than a v1 hop count
([ADR-0031](../docs/adr/0031-inferred-mapping-distance-as-ontology-span.md)), so
`MappingResponse.distance` is a coarse direct/indirect sentinel (`1` asserted, `2` inferred),
not a true depth — a deliberate v1 break recorded in ADR-0024.

#### Subject-side default search

A mapping is a directed *subject → predicate → object* statement, and a mapping search is asked from
the subject's perspective, so the classified/normal search (`constructClassifiedQuery`) matches the
**subject side only** ([ADR-0030](../docs/adr/0030-subject-side-default-search.md)). Each term becomes
a `subjectSideClause` — the one subject-side classifier shared with batch mapping (ADR-0024) and the
v1 `/api/search` adapter: an IRI → `subject_iri`, a CURIE → `subject_id` (normalised to its stored
prefix casing via `EntityReference`), anything else → the subject label field the `labelMatch` mode
selects. Terms OR together. The Advanced tab, `queryFields` and column filters still reach the object
and predicate fields — subject-side matching is only the *default* path.

Mappings *into* a term are not lost when the predicate is strong: the inference closure
([ADR-0016](../docs/adr/0016-single-pass-sssom-reasoning.md)) materialises the symmetric/inverse row
(the four equivalence predicates, `skos:exactMatch`, broad/narrow and crossSpecies inverses), whose
subject is the term. Weak predicates (`skos:closeMatch`, `skos:relatedMatch`, `oboInOwl:hasDbXref`,
`rdfs:seeAlso`) are not closed, so a term appearing only as the *object* of a weak predicate is
directional and reached through the Advanced tab or the v1 listing instead.

#### Free-text label matching

A plain **label** term (neither IRI nor CURIE) goes to a subject label field chosen by the request's
`labelMatch` (`LabelMatchType`, default `EXACT_CASE_INSENSITIVE`): `PARTIAL` → the analyzed
`subject_label` (`text_general`, subsequence phrase), `EXACT_CASE_INSENSITIVE` → `subject_label_ci`
(`string_ci` = keyword + lowercase + trim, whole label case-folded), `EXACT_CASE_SENSITIVE` →
`subject_label_str` (`string`, whole label byte-for-byte). The value is quoted and `ClientUtils`-escaped
in every mode. The mode affects only the label branch — never IRI/CURIE routing, the Advanced tab, or
batch-map/v1. The `*_label_ci` fields require a reindex to populate. See
[ADR-0026](../docs/adr/0026-configurable-label-match-mode.md).

#### Column-filter matching

`POST /api/v2/mappings/search` `columnFilters` use "contains" semantics. Label fields
(subject/object/predicate label) are matched against their `*_ngram` twin, whose indexed
terms are n-grams of individual words. The filter value is split on whitespace and each word
becomes its own `*word*` substring wildcard, AND-ed together — order-independent "contains all
of these words". A single wildcard cannot span words: `_ngram:*two words*` matches nothing,
because the analyzer tokenises on whitespace before n-gramming, so no indexed term contains a
space. Non-label fields are `string`-typed (the whole value is one term), so a single
escaped-space wildcard already matches a literal multi-word substring; they are not split.
Every word is escaped with `ClientUtils.escapeQueryChars`.

Performance note: `*word*` is a leading wildcard that scans the n-gram term dictionary
(~0.7 s cold per word on the current corpus, so a two-word label filter is ~1.4 s cold).
Solr's `filterCache` makes a repeated filter ~1 ms, but each distinct value a user types is a
cold query. This is the cost of preserving partial-word (substring) matching; a phrase query
on the plain `text_general` field would be ~50× faster but match whole words only.

### Suggest queries (ADR-0034, ADR-0035)

Three invariants worth stating, because all three are easy to break and none fails loudly.

**A suggestion must be a promise that the search returns something.** The entity typeahead is filtered
by the SAME predicate checkboxes the search is
([ADR-0035](../docs/adr/0035-weak-predicates-as-a-user-visible-control.md)) — not as a nicety, but
because `oxo2-mappings` hides `rdfs:subClassOf` and `oboInOwl:hasDbXref` by default, and an entity whose
every mapping is one of those completes to an empty table. `EntitySuggestQueryBuilder` therefore filters,
ranks and labels on the per-side, per-predicate count buckets the caller's `includeWeakPredicates`
currently makes visible — never on the entity's stored `mapping_count`, which counts predicates the
search will not show, and never on `is_subject`, which means "subject of *some* mapping" rather than
"subject of some mapping the user can see". Getting this wrong is not hypothetical: it shipped, and made
92% of suggestions return no rows.

**The contextual value suggest must REUSE `buildSolrQuery`, never rebuild the filters.** Its
suggestions have to be scoped by exactly what the visible result set is scoped by — the other column
filters, the weak-predicate exclusion, the corpus / inference-type / ontology-prefix / mapping-set
filters. So `buildValueSuggestQuery` builds the *real* search query and then turns it into a `rows=0`
facet request. Two things are deliberately undone: the same-SPO collapse (a facet counts documents; the
collapse only picks a representative row for display, and leaving it on would make the counts disagree
with the filter the user is about to apply) and the in-progress filter on the field being suggested
(otherwise the facet is scoped by the half-typed value it is trying to complete). A regression test
compares the two filter-query lists directly.

**`facet.prefix` is a raw byte prefix — not analyzed, not query-parsed, not case-folded.** So it must
never be `escapeQueryChars`-escaped (escaping is a query-syntax concern; here it would put literal
backslashes into the term). And because the faceted field preserves the original casing — it must, or a
suggestion would come back as `mondo:0005148` — a case-sensitive prefix would miss "Melanoma" for a user
typing `mel`. The suggest therefore issues the prefix under several casings and merges the buckets. This
covers real label and CURIE casing; it does **not** cover a mid-token camelCase boundary (`oboinowl`
will not find `oboInOwl:hasDbXref`), which is the accepted, recorded gap in ADR-0034.

### Configuration

- `OXO2_SOLR_HOST` — base URL of the Solr instance the backend queries.
- The backend serves on port 8081 (see `startBackend.sh` and Docker compose).

### Testing

Tests live under `src/test/java/`. Run with `mvn -pl oxo2-backend test`. No live Solr or
running backend required — tests are pure JVM.

Conventions:

- **`SolrQueryBuilderTest`** drives the builder through its public entry point
  `buildSolrQuery(...)` and asserts on the resulting `SolrQuery`'s observable properties
  (`getQuery()`, `getFilterQueries()`, `getSorts()`, `getFields()`,
  `get("defType")`, `getParams("qf")`). All `construct*` helpers are private — exercise them
  through dispatch in `buildSolrQuery`; do not loosen visibility for tests.
- **`MappingControllerTest`** uses `@WebMvcTest(MappingController.class)` with
  `@MockitoBean OxOSolrClient`. A `MockMvc` GET or POST triggers the controller; an
  `ArgumentCaptor<SolrParams>` captures the `SolrQuery` handed to the mocked Solr client.
- **Escape oracle.** When asserting on escaped Solr strings, compute the expected value
  with `ClientUtils.escapeQueryChars(...)` rather than hard-coding a backslash form. Tests
  stay correct if Solr's escape set changes.
- **`MappingEnum` as-value annotation.** `MappingEnum.getProperty()` carries `@JsonIgnore`
  so Jackson treats only the `@JsonValue`-annotated `getField()` as the as-value method.
  Required from Jackson 2.21 onwards (pulled transitively by `solr-solrj`), which flags any
  public no-arg `String` getter on an enum as an as-value candidate. Camel-case
  deserialization (`"subjectId"` → `SUBJECT_ID`) still works via the `@JsonCreator`
  factory `fromString(...)`. Do not remove the `@JsonIgnore` without confirming the
  POST `/api/v2/mappings/search` MockMvc tests still deserialize their request bodies.
