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
- **`POST /api/v2/mappings/search`** — faceted mapping search. Body: `MappingSearchRequest` (filters, facets, sort, paging). 
Returns `FacetedMappingResponse`.
- **`GET /api/v2/mapping-sets`** — list all mapping sets (up to 10 000), returning `MappingSetSummary` (id, title, 
description, creator labels, provider). Sorted by title.

Backwards compatibility with OxO v1 is *behavioural* ([ADR-0004](../docs/adr/0004-backwards-compatible-with-oxo-v1.md)) — the endpoints above use OxO2/SSSOM-shaped JSON; 
the design constraint is that v1 questions remain answerable.

## Module notes

### Layout

- `controller/api/v2/` — REST controllers (`MappingController`, `MappingSetController`).
- `controller/api/dto/request/` — request DTOs: `MappingSearchRequest`, `FieldQuery`, `SortedField`, `SortOrderEnum`, 
`MappingFacetEnum`.
- `controller/api/dto/response/` — response DTOs: `FacetedMappingResponse`, `MappingSetSummary`.
- `service/OxOSolrClient.java` — single SolrJ-backed service exposing `query(...)` over the `oxo2-mappings` collection 
and `queryMappingSets(...)` over `oxo2-mappingsets`.
- `service/helper/SolrQueryBuilder.java` — translates `MappingSearchRequest` into a `SolrQuery` (facets, filters, sort, paging).
- `service/helper/SolrConstants.java` — Solr field-name constants for this module's queries.
- `exception/GlobalExceptionHandler.java` — top-level exception translation.

### Querying patterns

Queries are built directly with SolrJ `SolrQuery`. There is no JPA, no repository abstraction, no caching layer between 
controllers and Solr — this is deliberate ([ADR-0002](../docs/adr/0002-solr-as-sole-data-store.md)). Field names come from constants in `oxo2-shared` (`MappingEnum`, 
`MappingSetConstants`) so the dataload and backend stay aligned.

### Configuration

- `OXO2_SOLR_HOST` — base URL of the Solr instance the backend queries.
- The backend serves on port 8081 (see `startBackend.sh` and Docker compose).

### Testing

Tests live under `src/test/java/`. Run with `mvn -pl oxo2-backend test`. No live Solr or
running backend required — tests are pure JVM.

Conventions:

- **`SolrQueryBuilderTest`** drives the builder through its public entry point
  `buildSolrQuery(...)` and asserts on the resulting `SolrQuery`'s observable properties
  (`getQuery()`, `getFilterQueries()`, `getSorts()`, `getFacetFields()`, `getFields()`,
  `get("defType")`, `getParams("qf")`). All `construct*` helpers are private — exercise them
  through dispatch in `buildSolrQuery`; do not loosen visibility for tests.
- **`MappingControllerTest`** uses `@WebMvcTest(MappingController.class)` with
  `@MockitoBean OxOSolrClient`. A `MockMvc` GET triggers the controller; an
  `ArgumentCaptor<SolrParams>` captures the `SolrQuery` handed to the mocked Solr client.
- **Escape oracle.** When asserting on escaped Solr strings, compute the expected value
  with `ClientUtils.escapeQueryChars(...)` rather than hard-coding a backslash form. Tests
  stay correct if Solr's escape set changes.

Known limitation: POST `/api/v2/mappings/search` is not covered by MockMvc tests. Jackson
2.21 (pulled transitively by `solr-solrj`) flakily fails to introspect `MappingEnum` —
only `getField()` carries `@JsonValue`, but Jackson also detects `getProperty()` as an
as-value candidate and reports "Multiple 'as-value' properties defined". The error appears
or not depending on deserializer-cache order across test runs. Until this is resolved
(adding `@JsonIgnore` to `MappingEnum.getProperty()` or pinning Jackson via
`dependencyManagement`), cover the POST endpoint indirectly through `SolrQueryBuilderTest`.
