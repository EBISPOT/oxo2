# ADR-0025: OxO v1 `/api/mappings` listing compatibility

- **Status**: Accepted
- **Date**: 2026-07-03

## Context

[ADR-0024](0024-cross-ontology-mapping.md) delivered wire-compatible OxO v1 `POST /api/search` (the
batch term-mapping endpoint). The other half of v1's public read surface is the OxO v1
`MappingController` at `/api/mappings` — its **read** verbs specifically, since OxO2 does not accept
mapping writes:

- `GET /api/mappings` — a paged mapping listing, optionally filtered by `fromId` / `toId`, returning a
  HAL `PagedResources<Mapping>` envelope.
- (`GET /api/mappings/{id}`, `GET /api/mappings/summary`, `GET /api/mappings/summary/counts`, and the
  create/delete verbs are **not** ported — see Consequences.)

v1's `Mapping` was a Neo4j `@RelationshipEntity` serialised by Jackson: nested `fromTerm` / `toTerm`
(`Term{curie, identifier, uri, label, datasource}`), a `datasource` node (11 fields: `prefix`,
`preferredPrefix`, `name`, `orcid`, `licence`, …), plus `mappingId` (numeric), `sourcePrefix`,
`sourceType`, `predicate`, `scope`, and `date`. OxO2's data model diverges sharply: mappings are SSSOM
documents on a Solr index, an "ontology" is just a CURIE prefix with no datasource node (ADR-0024), the
only stable mapping identity is a derived `UUID`, and the SSSOM predicate replaces v1's coarse `scope`
enum. Reproducing the v1 `Mapping` wire shape therefore requires deciding, field by field, what OxO2
can honestly populate and what must be a documented gap.

## Decision

Add **`GET /api/mappings`** at the literal v1 path (not under `/api/v2`), returning the v1 HAL
`PagedResources<Mapping>` envelope (`_embedded.mappings`, `_links`, `page`) as a thin, read-only
adapter over the OxO2 mappings index. Field semantics:

- **Filtering is undirected**, as in v1: `fromId` alone matches the term on the subject *or* object
  side; `fromId`+`toId` matches mappings between the two terms in either direction; a lone `toId` is
  **ignored** (v1 fell through to "all mappings" — reproduced verbatim, since the undirected `fromId`
  form already answers the into-a-term question). `fromId` / `toId` accept a CURIE (normalised to the
  stored prefix casing) or a full IRI.
- **Asserted mappings only.** v1 exposed no inference tier on this endpoint, so results are filtered to
  `inference_type:ASSERTED`.
- **Weak predicates shown by default.** `rdfs:subClassOf` and `oboInOwl:hasDbXref` — hidden by default
  on `/api/v2/mappings/search` — are **included** here, because v1 was built on xrefs. An optional
  `hideWeakPredicates=true` excludes them.
- **`scope`** is derived from the SSSOM predicate: `EXACT` = {`skos:exactMatch`, `owl:equivalentClass`};
  `NARROWER` = {`skos:narrowMatch`, `rdfs:subClassOf`}; `BROADER` = {`skos:broadMatch`}; `RELATED` =
  {`skos:relatedMatch`, `skos:closeMatch`, `oboInOwl:hasDbXref`, and anything unrecognised}. v1's
  `PREDICTED` is never emitted.
- **`mappingId` is always null.** v1's numeric Neo4j id cannot be reproduced; mapping identity is
  deferred to the forthcoming SSSOM API. The field is kept in the shape (present, null).
- **`Term.uri` is the full IRI**, from the `subject_iri` / `object_iri` index fields — matching what v1
  returned. `Term.identifier` (the bare local id) is left null; OxO2 does not carry it.
- **`Term.datasource` carries the ontology prefix** (from the CURIE; `preferredPrefix` defaults to it,
  the rest left null/empty). v1 returned `null` here, but the CURIE prefix is a harmless, more useful
  enrichment and cannot break a client that tolerated null.
- **The mapping-level `datasource` identifies the SSSOM mapping set** the mapping came from —
  `mapping_set_id` → `prefix` / `preferredPrefix` (the fields a v1 client reads for datasource identity)
  and `mapping_set_title` → `name`. v1 left this datasource **null** on `/api/mappings`, so populating it
  cannot break wire-compat, and it is OxO2's honest answer to "where did this mapping come from"
  (SSSOM provenance is the mapping set, not a per-ontology datasource node). `sourcePrefix` stays the
  **subject** ontology prefix, matching v1.
- **Per-item HAL `_links` are omitted.** OxO2 exposes no per-mapping resource URL (there is no
  `/api/mappings/{id}`), so a `self` link would be dead. The collection `_links.self` is preserved.

## Consequences

- Discharges the remaining `/api/mappings` slice of [ADR-0004](0004-backwards-compatible-with-oxo-v1.md)
  for the read path. The field mapping was fixed against a running legacy OxO v1 instance, not inferred:
  legacy populates `Term.uri` with the full IRI, returns `Term.datasource` and the mapping-level
  `datasource` as `null`, and sets `sourcePrefix` to the subject prefix — all reproduced or enriched
  above. As with ADR-0024's `distance`, the remaining gaps are **deliberate v1 semantic breaks** where
  the data models cannot be reconciled: null `mappingId`, derived `scope`, and the dropped per-item
  links. A v1 consumer that parsed `fromTerm`/`toTerm`/`uri`/`scope`/`date` keeps working; one that
  relied on numeric `mappingId` or per-item self links does not.
- **`closeMatch → RELATED`, not `EXACT`.** `skos:closeMatch` is a deliberately weaker, non-transitive
  near-equivalence; folding it into `EXACT` (which a v1 consumer reads as identity) would overstate
  confidence, so it maps to `RELATED`. `EXACT` stays reserved for genuine identity.
- **Not ported:** `GET /api/mappings/{id}` (no reproducible id; identity deferred to the SSSOM API),
  `GET /api/mappings/summary` and `/summary/counts` (the OxO2 frontend has its own landing page, and
  `/api/v2/ontologies` already answers the target-counts question), and the create/delete verbs
  (OxO2 ingests mapping sets via the dataload pipeline, not the API). Revisit if a v1 consumer of these
  surfaces appears.
- No reindex or schema change: the endpoint reads existing fields (`subject_id`, `object_id`,
  `subject_iri`, `object_iri`, `subject_label`, `object_label`, `predicate_id`, `predicate_iri`,
  `mapping_date`, `mapping_set_id`, `mapping_set_title`, `inference_type`) and derives prefixes from the
  CURIEs.
