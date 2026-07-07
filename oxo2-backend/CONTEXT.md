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
- `service/OxOSolrClient.java` — single SolrJ-backed service exposing `query(...)` over the `oxo2-mappings` collection 
and `queryMappingSets(...)` over `oxo2-mappingsets`.
- `service/helper/SolrQueryBuilder.java` — translates `MappingSearchRequest` into a `SolrQuery` (filters, sort, paging).
- `service/helper/SolrConstants.java` — Solr field-name constants for this module's queries.
- `exception/GlobalExceptionHandler.java` — top-level exception translation.
- `config/OpenApiConfig.java` — OpenAPI 3 `info` metadata for the Swagger docs (see § API documentation).

### Querying patterns

Queries are built directly with SolrJ `SolrQuery`. There is no JPA, no repository abstraction, no caching layer between 
controllers and Solr — this is deliberate ([ADR-0002](../docs/adr/0002-solr-as-sole-data-store.md)). Field names come from constants in `oxo2-shared` (`MappingEnum`, 
`MappingSetConstants`) so the dataload and backend stay aligned.

#### Inference-type filter and ranking

The `inferenceType` filter (search body, and the mapping-sets `?inferenceType` param) becomes an OR of exact
`inference_type:<CODE>` term matches; an absent/empty list means all types. Ranking is a **multiplicative**
edismax `boost` (`SolrQueryBuilder.RANKING_BOOST`), not an additive `bq`: an additive boost is skewed by term
idf (ASSERTED is common, SSSOM rare) and would invert the intended order. The tier multiplier (asserted 3 &gt;
SSSOM 2 &gt; OWL 1) is multiplied by a distance factor bounded to `[1.0, 1.4]` (shorter chains higher), kept under
the 1.5× adjacent-tier ratio so it can never flip the tiers. See
[ADR-0011](../docs/adr/0011-inference-type-replaces-is-inferred.md).

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
query-time hop count ([ADR-0020](../docs/adr/0020-defer-explanations-to-on-demand.md) left `distance`
inert), so `MappingResponse.distance` is a coarse direct/indirect sentinel (`1` asserted, `2` inferred),
not a true depth — a deliberate v1 break recorded in ADR-0024.

#### Free-text label matching

A classified/normal search (`constructClassifiedQuery`) routes each free-text term to an OR over the
subject/object/predicate fields. An IRI term goes to `*_iri` and a CURIE to `*_id` (both exact
`string` lookups); a plain **label** term goes to a label field chosen by the request's `labelMatch`
(`LabelMatchType`, default `EXACT_CASE_INSENSITIVE`): `PARTIAL` → the analyzed `*_label`
(`text_general`, subsequence phrase), `EXACT_CASE_INSENSITIVE` → `*_label_ci` (`string_ci` = keyword
+ lowercase + trim, whole label case-folded), `EXACT_CASE_SENSITIVE` → `*_label_str` (`string`, whole
label byte-for-byte). The value is quoted and `ClientUtils`-escaped in every mode. The mode affects
only the label branch — never IRI/CURIE routing, the Advanced tab, or batch-map/v1. The `*_label_ci`
fields require a reindex to populate. See [ADR-0026](../docs/adr/0026-configurable-label-match-mode.md).

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
