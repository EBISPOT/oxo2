# ADR-0032: SSSOM-API compatibility surface at `/api/sssom`

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

The mapping-commons community publishes a [SSSOM API specification and reference implementation](https://github.com/mapping-commons/sssom-api)
— a small set of endpoints (`/entities`, `/mappings`, `/mapping_sets`, `/stats`) for retrieving SSSOM
mappings, with a fixed JSON envelope (`{data, pagination, facets}`) and a
`filter=field|operator|value` grammar. Tooling in the SSSOM ecosystem expects a service that speaks
this contract. OxO2 already stores and serves SSSOM mappings, but through its own `/api/v2` search
surface (SSSOM-shaped payloads, a different envelope, 0-based paging) and a v1 compatibility layer
([ADR-0025](0025-v1-mappings-listing-compatibility.md), [ADR-0024](0024-cross-ontology-mapping.md)).

We wanted OxO2 to be a conforming SSSOM endpoint without disturbing either existing surface. The
reference implementation is Python/FastAPI over an RDF4J triplestore and carries a few pathologies we
did not want to reproduce (it returns HTTP 302 for an invalid filter field, and computes its facets by
streaming the entire matching result set through the application to count in Python).

## Decision

Add a third, parallel API surface at **`/api/sssom`** implementing the SSSOM spec's endpoints against
the existing Solr collections, matching the documented request/response shapes but fixing the
undocumented pathologies. The endpoints:

- `GET /api/sssom/mappings?filter=field|operator|value` — repeatable, AND-joined filters. Operators
  `eq`, `ge`, `gt`, `le`, `lt`, `contains` (`eq` is our documented superset of the reference's
  range/contains set). `field` is any SSSOM slot **or** OxO2 extension slot (`inference_type`,
  `distance`, …).
- `GET /api/sssom/mappings/{id}` — one mapping by its `mapping_id` (the stable name-based UUID); a
  bare SSSOM mapping, not the envelope; 404 if absent.
- `GET /api/sssom/mappings/{field}/{*value}` — the equality shorthand of the filter grammar; `value`
  may contain slashes and is URL-decoded.
- `POST /api/sssom/entities` — body `{curies, mapping_justification?, predicate_id?}`; every mapping
  where a curie is the `subject_id` **or** `object_id`.
- `GET /api/sssom/mapping_sets?filter=…` — the mapping sets (over the sets collection).
- `GET /api/sssom/mappings?mapping_set_id=<iri>` — the mappings of one set (the reference's
  `/mapping_sets/{id}/mappings`, see below).
- `GET /api/sssom/stats` — `{nb_mapping, nb_mapping_set, nb_mapping_provider, nb_entity}`.

Every list endpoint returns the reference envelope `{data, pagination, facets}` with **1-based**
`page` and a `limit` (default 20, capped at 100). `pagination.previous`/`next` are absolute URLs built
from the current request (`server.forward-headers-strategy=framework` makes them correct behind the
ingress). `facets` (value→count on `mapping_justification` and `predicate_id`, min/max on
`confidence`) come from Solr's facet/stats components on the same query that fetches the page — one
round trip, computed over the whole filtered set, never by streaming rows through the application.

Deliberate choices and deviations from the reference, all documented on the endpoints:

- **Mount point `/api/sssom`, not host root.** A spec client configures a base URL anyway; a prefix
  keeps the surface unambiguous beside `/api/v2` and the v1 paths.
- **Inferred mappings are included, with their extension slots.** SSSOM permits extension slots, and
  hiding half the corpus would misrepresent OxO2. A purist client filters `inference_type|eq|ASSERTED`.
- **Same-SPO rows are collapsed** ([ADR-0023](0023-collapse-for-same-spo.md) mechanism), so a triple
  asserted in many sets is one row carrying its `group_members`. Consistent with `total_items` being
  the collapsed group count and the facet counts being over representatives.
- **No default weak-predicate exclusion.** Unlike the `/api/v2` search UI, this surface hides no
  predicate — the spec's contract is "retrieve all mappings".
- **`400`, not `302`, for an invalid filter/field/operator or bad paging.**
- **Set-scoped mappings are a `mapping_set_id` filter on `/mappings`, not a sub-resource.** The
  reference nests them as `GET /mapping_sets/{id}/mappings` — an idiomatic sub-collection. But that
  shape is only clean when the child key is path-safe, and a mapping-set id is a full IRI whose
  encoded slashes Tomcat rejects in a path variable. Rather than the mangled
  `/mapping_sets/by_id/mappings?mapping_set_id=…` hybrid (which is neither a clean sub-resource nor a
  clean filter, and reads as if a `/mapping_sets/…` path returned mappings), the scope is exposed as a
  `mapping_set_id` query parameter on `/mappings` — equally idiomatic REST (filtering a collection by a
  foreign key), honest about returning mappings, and equivalent to `filter=mapping_set_id|eq|<iri>`.
  Underscores (not hyphens) throughout the SSSOM paths, matching the reference.
- **`?format=sssom-tsv|tsv|csv`** is supported on every mapping endpoint as an OxO2 extension, reusing
  the streamed SSSOM exporter ([ADR-0024](0024-cross-ontology-mapping.md)); the reference has no export.
- **Mapping sets are returned as their stored Solr fields** (whose names already are the SSSOM slot
  names, minus Solr-internal bookkeeping), because the shared `MappingSet` model carries no Solr field
  bindings; this is faithful to every stored slot and robust to schema additions.
- **`nb_entity` is a HyperLogLog estimate** of distinct entities appearing as a subject or object,
  computed with `unique(entity_id)` over a new `entity_id` copy-field (`subject_id` ∪ `object_id`,
  docValues) added to the mappings schema. Like `mapping_set_category`
  ([ADR-0027](0027-config-driven-mapping-set-category.md)), the field is empty on documents indexed
  before it existed, so `nb_entity` reads low until the next full dataload repopulates it.

## Consequences

- OxO2 answers the mapping-commons SSSOM contract, so ecosystem tooling can point at it with only a
  base-URL change, while `/api/v2` and the v1 paths are untouched.
- The surface is thin: `SssomQueryBuilder` builds the Solr queries (reusing `SolrQueryBuilder`'s
  provenance ranking and same-SPO collapse, now exposed as two public methods), `SssomResultMapper`
  assembles the envelope, and `SssomMappingService` centralises paging/faceting/linking/export for the
  three mapping-returning endpoints. Adding an endpoint or a filter operator is a local change.
- `nb_entity` requires a reindex to become accurate. This is a soft dependency — every other endpoint
  works immediately against the current index; only that one stat reads low until the next dataload.
  The `entity_id` field is additive (`indexed=false`, docValues-only), so it does not affect existing
  queries or the size of the inverted index.
- Filtering on a mapping-set listing is validated against a mapping-set slot allowlist, because a
  filter on a field absent from the sets collection would make Solr fault ("undefined field").
- The reference's per-request facet cost (a full result-set scan) does not apply: OxO2's facets are
  Solr-native and bounded, so a filter that matches millions of rows still facets in one cheap pass.
- **Not yet exercised against a live Solr.** The query shapes, envelope, facet/stats wiring, paging and
  error semantics are covered by unit and full-context Spring tests, but the Solr-side semantics
  (`json.facet unique(entity_id)`, `stats.field` on the confidence point field, collapse+facet
  interaction, range syntax) should be smoke-tested against a loaded index before release.
