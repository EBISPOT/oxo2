# ADR-0024: Cross-ontology mapping via prefix queries, with OxO v1 batch-search compatibility

- **Status**: Accepted
- **Date**: 2026-06-29

## Context

OxO v1's signature use case is *map a datasource to a datasource*: give a source datasource (e.g.
`DOID`) and one or more targets (e.g. `EFO`, `MONDO`) — or a pasted list of identifiers — and get back
every mapping from the source into the targets, downloadable as CSV/TSV. Established users have
pipelines built on v1's `POST /api/search` endpoint and its HAL `SearchResult` response envelope.
OxO2 has so far exposed only term/label search (`POST /api/v2/mappings/search`) and has **not** yet
delivered the v1-compatibility surface promised by
[ADR-0004](0004-backwards-compatible-with-oxo-v1.md).

Two facts about OxO2's data model shape how this maps on:

- **An "ontology" is a CURIE prefix.** There is no `ontology`/`datasource` field on a mapping
  document; the ontology lives in the `subject_id` / `object_id` CURIE prefix (`DOID:0001816` → `DOID`).
  v1 modelled this as a first-class `Datasource` node with a `HAS_SOURCE` edge per term; OxO2 has no
  such node.
- **The transitive closure is already materialised.** v1 computed mapping paths by traversing Neo4j
  `MAPPING` edges at query time, bounded by a `distance` (hop count). OxO2 precomputes the cross-set
  SSSOM closure at dataload ([ADR-0016](0016-single-pass-sssom-reasoning.md)); `DOID → EFO` already
  exists as an `SSSOM_INFERENCE` mapping when only `DOID → UMLS → EFO` was asserted. So "map DOID to
  EFO/MONDO" is a **filter over existing mappings**, not a graph walk. Crucially, OxO2 has no
  query-time hop count: `distance` / `explanation_length` carry inert defaults
  ([ADR-0020](0020-defer-explanations-to-on-demand.md)).

OxO2 also only knows *mapped* terms — it ingests mapping sets, not ontologies — so "all terms from
DOID" can only mean "all DOID terms that appear as a subject in some mapping". There is no
unmapped-term report for the whole-ontology case; it exists only when the caller supplies an explicit
input list (v1's batch behaviour).

## Decision

Add **cross-ontology mapping** as a query dimension over the existing mappings index: return all
mappings whose `subject` prefix is in a set of source ontologies and whose `object` prefix is in a set
of target ontologies. Expose it through the existing v2 search surface for the frontend, and through a
**wire-compatible `/api/search` adapter** for v1 consumers. Mapping is **directional** (subject =
source, object = target); the UI offers a swap rather than an undirected query, so asymmetric
predicates (`broad`/`narrowMatch`) are never silently misrepresented.

### Data model — denormalised prefix fields

- Add `subject_prefix` and `object_prefix` (`string`, `indexed`, `docValues`) to `oxo2-mappings`,
  populated **once at dataload** as the CURIE prefix (text before the first `:`) of `subject_id` /
  `object_id`. Requires a **full reindex**.
- These make the prefix filter an exact-term query (`object_prefix:EFO`) and — the reason they are a
  stored field rather than a `subject_id:DOID\:*` wildcard — make prefixes **facetable**, so the UI can
  show count-driven target options ("EFO · 842, MONDO · 1 203") and grey out zero-count targets.

### v2 API (frontend-facing, SSSOM-shaped)

- **`GET /api/v2/ontologies`** — distinct prefixes with counts, driving the selectors;
  `?forSubject=DOID` facets `object_prefix` over the DOID subset to return reachable targets + counts.
- **`GET /api/v2/mappings?from=DOID&to=EFO,MONDO`** — the bookmarkable source→target view: the mappings
  collection filtered by source/target prefix via query params, **no action verb**, mirroring OxO v1's
  `GET /api/mappings?fromId=…&toId=…`. `from` / `to` are prefix lists.
- **Extend `POST /api/v2/mappings/search`** with `subjectPrefixes` / `objectPrefixes` (each becomes an
  OR'd `*_prefix` filter query) for the complex/body case. The workhorse — paging, same-SPO collapse
  ([ADR-0023](0023-collapse-for-same-spo.md)), the inference-type filter
  ([ADR-0011](0011-inference-type-replaces-is-inferred.md)), column filters and sorting all apply
  unchanged.
- **`POST /api/v2/mappings/batch-map`** — the v1 term-list case: body carries the input terms
  (CURIEs / IRIs / labels) and `objectPrefixes`; returns `{ mappings: Page<Mapping>, unmappedInputs,
  summary }`. `unmappedInputs` is computed over the whole input set, independent of paging. Input is
  capped (default 1 000 terms).
- **`?format=` on any of the above** (default `json`; plus `sssom-tsv` / `csv` / `tsv`) negotiates the
  representation — a non-`json` format streams the **full** result (paging ignored) via a Solr cursor,
  SSSOM-compliant TSV carrying a metadata header + `curie_map`. There is no standalone export endpoint;
  this mirrors v1's `/api/search?format=`.

### v1 compatibility adapter (wire-compatible)

- **`POST /api/search`** (and `GET`), **not** under `/api/v2` — the literal v1 path, so existing
  pipelines are unchanged. Accepts the v1 `MappingSearchRequest` (`ids`, `inputSource`,
  `mappingTarget`, `mappingSource`, `distance`) and returns the v1 HAL `PagedResources<SearchResult>`
  envelope (`SearchResult{queryId, querySource, curie, label, mappingResponseList[]}` /
  `MappingResponse{curie, label, sourcePrefixes[], targetPrefix, distance}`), implemented as a thin
  adapter over the same Solr query, regrouping the flat result set **by input term**. Unmapped inputs
  come back as a `SearchResult` with an empty `mappingResponseList`, as in v1.
- **`distance` is best-effort** — OxO2 has no query-time hop count, so v1's traversal depth collapses
  to a tier filter: `distance=1` → `ASSERTED` only (direct); `distance≠1` (including `-1` = unlimited)
  → `ASSERTED ∪ SSSOM_INFERENCE`, i.e. no tier filter, so a v1 caller asking for "everything" still
  gets the direct mappings *and* the closure. The response's `MappingResponse.distance` is a coarse
  direct/indirect sentinel (`1` for asserted, `2` for inferred), **not** a true hop count.
- `sourcePrefixes` is populated from `mapping_set_source` / `mapping_provider`; CSV/TSV export keeps the
  v1 columns (`curie_id, label, mapped_curie, mapped_label, mapping_source_prefix,
  mapping_target_prefix, distance`).
- **`label` is resolved from `oxo2-entities`, not from the mapping row.** A label in `oxo2-mappings` is
  a property of the ROW: whole mapping sets (the SeMRA-assembled ones, `atlas`, `ukbiobank`,
  `mondo.sssom.tsv`) ship without labels, so reading the label off whichever row ranked first returned
  `null` for terms OxO2 can label from another row. Precedence is **entity label → row label → the
  CURIE itself**; v1 never emits a null label, falling back to the CURIE, so a client reading `label`
  unconditionally keeps working. The entity lookup deliberately does **not** apply ADR-0045's
  `obsolete:false` default — v1 has no notion of obsolescence and labels obsolete terms
  (`obsolete_carcinoma`), so inheriting the typeahead's default would leave exactly those unlabelled.
  An input with no mappings at all keeps v1's `null` `curie`/`label`; the CURIE fallback applies only
  to terms that were actually found.

### Frontend — extend the normal Search tab

- The normal Search tab gains two optional exact-prefix selectors, **"from \[source ontologies]"** and
  **"to \[target ontologies]"** (options + counts from `GET /api/v2/ontologies`), plus a ⇄ swap, beside
  the existing terms box. They compose: source prefix + empty box → entire ontology; terms + target
  prefix → bounded batch lookup; terms only → today's search.
- **Label inputs are source-side.** A non-CURIE/IRI input (e.g. `diabetes`) matches `subject_label`,
  bounded by `object_prefix ∈ {targets}` — "mappings *from* a diabetes-labelled source term *into* the
  target ontologies". The target filter is what keeps an otherwise-broad label match tractable.
- Results reuse `NormalResultsTable` (same-SPO collapse, paging, filters) plus a summary header and an
  "Export" (SSSOM-TSV) button.

### Sharing

The ontology→ontology view is URL-driven and bookmarkable (`/map?from=…&to=…`). The pasted-term-list
(batch) view is **POST-only and not bookmarkable** — matching v1, which also shared results only via
export, not URL — with the input mirrored to `sessionStorage` so a refresh re-runs it.

## Consequences

- **Full reindex required** to populate `subject_prefix` / `object_prefix` (joins the ADR-0011 /
  ADR-0013 reindex notes in `oxo2-dataload/CONTEXT.md`).
- The v1 `/api/search` adapter discharges ADR-0004's promised `/api/...` surface for this use case. It
  is a **deliberate v1 semantic break** on `distance`: query-time hop counts cannot be reproduced over
  a precomputed, flattened closure, so depth degrades to a direct/all toggle and `MappingResponse.distance`
  is a sentinel. Documented here per ADR-0004's "breaking changes require a deliberate decision".
- Whole-ontology mode has **no unmapped report** (OxO2 knows only mapped terms); only the explicit
  term-list (`batch-map`) mode can report unmapped inputs.
- Directional-only: a mapping stored solely in the reverse direction under a *symmetric* predicate is
  reachable only because SSSOM reasoning materialises its inverse; the swap button covers the user
  intent without an undirected query that would mis-handle asymmetric predicates.
- The prefix selectors and `/ontologies` facet treat the CURIE prefix as the ontology identity. Bare
  IRIs that never resolved to a CURIE have no prefix and fall outside prefix filtering (rare; the
  dataload leaves their `*_prefix` empty).

## Considered options

- **`subject_id:DOID\:*` prefix wildcards, no new field** — ships without a reindex and the
  colon-anchoring is correct, but cannot cheaply facet, so the target picker loses its counts.
  Rejected as the end state; usable as an interim slice before the reindex lands.
- **Behavioural-only v1 compat (no HAL envelope)** — answer v1's question through the v2 SSSOM endpoint
  and let callers adapt. Rejected: users' pipelines parse the exact `SearchResult` envelope, so
  wire-compat is required.
- **Undirected query (subject∈source∧object∈target) OR (reverse)** — "more complete" for symmetric
  predicates but misrepresents asymmetric ones unless the predicate is inverted on reversal. Rejected
  in favour of directional + swap; revisit if symmetric-only completeness gaps appear.
- **A dedicated `/map` tab** — rejected in favour of extending the normal Search tab, since source
  terms, target prefixes and the result table are the same surface users already use.
- **An action verb for the source→target query** (`/mappings/map`, `/mappings/cross-ontology`) —
  rejected in favour of filtering the bare mappings collection by `?from=&to=` query params: more
  RESTful, one fewer verb, and a direct echo of v1's `GET /api/mappings?fromId=…&toId=…`. A standalone
  `/export` verb was likewise collapsed into `?format=`, mirroring v1's `/api/search?format=`. (The
  *frontend* keeps a `/map` route — a UI concern, distinct from the API path.)
