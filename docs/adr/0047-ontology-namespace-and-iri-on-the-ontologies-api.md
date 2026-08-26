# ADR-0047: A prefix's namespace comes from the index, its ontology IRI from the mapping set

- **Status**: Accepted
- **Date**: 2026-08-26

## Context

`GET /api/v2/ontologies` lists every CURIE prefix in the mappings index with its subject and object
counts, and nothing else. A consumer holding `ABEROWL:GO` cannot turn it into an IRI, and cannot tell
which of the ~1339 entries are ontologies at all: the listing is faceted over `subject_prefix` /
`object_prefix`, so it also carries registry prefixes (`BARTOC`, `BIOPORTAL`) and corpus artefacts
(`ATC_CODE`, `AISM_2`, `AEO_RETIRED`, `ANS.EDUCATIONLEVEL`). [Issue
113](https://github.com/EBISPOT/oxo2/issues/113) asked for a `namespace` field, and for the ontology's
own IRI if it were available.

These are two different values and neither is derivable from the other. For MONDO they are
`http://purl.obolibrary.org/obo/MONDO_` and `http://purl.obolibrary.org/obo/mondo.owl`; for EFO,
`http://www.ebi.ac.uk/efo/EFO_` and `http://www.ebi.ac.uk/efo/efo.owl`. So they need separate sources.

**The ontology IRI** became available upstream: OLS SSSOM extracts now carry `ontology_iri` in the
SSSOM `other` extension block, alongside the `prefix` and `ontology` keys [ADR-0038](0038-promote-ontology-prefix-from-other-block.md)
already promotes. Verified present in 288 of 288 files in the 2026-08-25 release, as the plain
ontology PURL rather than the versioned IRI.

**The namespace** had three candidate sources, measured against the live dev corpus (1339 prefixes)
and the `oxo-config-test` fixture:

| Source | Prefix coverage | Occurrences | Agrees with the IRIs OxO2 serves? |
| --- | --- | --- | --- |
| Bundled Bioregistry snapshot ([ADR-0015](0015-default-prefix-map-and-metadata-synthesis-for-bare-sssom.md)) | 448/1339 = 33.5% | — | No |
| Union of the sets' declared `curie_map`s | 1232/1339 = 92.0% | 79.2% | No |
| The `iri` already stored on each entity doc | 63/63 on the fixture | ~100% | Yes, by construction |

The Bioregistry snapshot contradicts the canonical stem for 8 of the 16 prefixes in the
[ADR-0029](0029-canonical-entity-iri-overrides.md) override table — for MeSH it names
`https://meshb.nlm.nih.gov/record/ui?ui=` while OxO2 mints `http://id.nlm.nih.gov/mesh/`.

The declared `curie_map`s reach far better coverage but inherit producer corruption — `BFO` appears in
some OLS extracts as `http://purl.obolibrary.org/obo/http://purl.obolibrary.org/obo/BFO_`, the
double-expanded-PURL class that `iri-prefix-overrides.json` is curated to exclude — and they lose to
ADR-0029 at load time anyway. The `oxo-config-test` fixture carries a reproducible instance: the
`mondo.ols.obsolete` set declares `HGNC: https://www.genenames.org/data/gene-symbol-report/#!/hgnc_id/`
while the index serves `HGNC:5056` as `http://identifiers.org/hgnc/5056`.

That is the crux. The first two sources are *claims* about what a prefix ought to expand to. The entity
`iri` is the *outcome* of the resolution the dataload actually performed — ADR-0029 override, then the
set's own `curie_map`, then the Bioregistry fallback — and it is what every other endpoint returns.

## Decision

Add two optional fields to each `/api/v2/ontologies` entry, from two different sources.

**`namespace`** is derived from the entity index. `mappings2entities` computes it per entity as the
entity's `iri` minus its CURIE's local part, and stores it as a new `namespace` field on
`oxo2-entities`. The listing resolves one namespace per prefix with a single `prefix,namespace` pivot
facet; where a prefix carries several stems the one most entities use wins, with the lexicographically
smaller stem breaking an exact tie. Stripping the local part rather than matching a known stem is what
makes it answerable for prefixes no registry knows — `ABEROWL` resolves to
`http://aber-owl.net/ontology/` despite appearing in neither the Bioregistry snapshot nor any set's
`curie_map`.

**`uri`** is the ontology's own IRI, promoted from `other` exactly as ADR-0038 promotes `prefix` and
`ontology`: a serialize-only `MappingSet.ontologyIri()` accessor with no record component and no
builder setter, landing as an `ontology_iri` field on `oxo2-mappingsets` and exposed on
`MappingSetSummary`. The listing joins it by prefix, **case-insensitively** — the listing's prefixes are
upper-cased by `EntityReference.getCuriePrefix`, while a set's prefix is verbatim from the producer and
keeps its casing. An exact-string join matches 211 prefixes covering 15.0% of subject+object
occurrences; a case-folded join matches 234 covering 33.9%, and the 23 it recovers are the largest in
the corpus — `NCBITaxon` (9.1M occurrences), `HGNC` (570k), `mesh` (566k), `ror` (37k), plus the
FlyBase and WormBase families. The join is also one-to-many: 74 prefixes carry both `<ontology>.ols`
and `<ontology>.ols.obsolete`, which collapse to one entry.

Both fields are **omitted when unknown**, never serialised as `null` or `""`. That makes `uri`'s
presence the signal that a real ontology backs the prefix, which is the discriminator the listing
previously lacked.

The enrichment is not allowed to fail the request. The counts are the payload; if the entity or
mapping-set query errors, the listing is served without the fields.

## Consequences

- **Both fields populate only on a reindex.** The schema fields and the fold are new, so existing
  `oxo2-entities` and `oxo2-mappingsets` documents carry neither until the next dataload. Until then
  the endpoint answers exactly as it does today — the pivot finds no `namespace` field, the ontology
  sets carry no `ontology_iri`, and both fields are simply absent.
- **`uri` is absent for most of the listing** — 1105 of 1339 prefixes have no ontology-derived set
  behind them. That is the honest answer rather than a gap to fill: `ABEROWL` and `ATC_CODE` are not
  ontologies, and inventing an IRI for them from another registry would misrepresent the corpus.
- **`namespace` reports corpus artefacts faithfully.** `CHEBI_2` resolves to
  `http://www.ebi.ac.uk/chebi/searchId.do?chebiId=CHEBI:`, because that genuinely is the stem those
  mappings used. A registry lookup would have returned nothing instead. Reporting the real stem is more
  useful to a consumer resolving identifiers, at the cost of surfacing rather than hiding the artefact.
- **One namespace per prefix means a minority of entities will not reconstruct.** Reporting the
  most-used stem is a choice, not a derivation: an entity carrying a losing stem cannot be rebuilt from
  its prefix's reported namespace. Measured on the `oxo-config-test` corpus, 6184 of 6186 entities
  reconstruct exactly; the two exceptions are the `PR` entities using `https://purl.obolibrary.org/obo/PR_`
  where the prefix reports the `http://` form that 8 others use. A consumer expanding a CURIE gets the
  stem the corpus predominantly uses, which is the best single answer available — the alternative is
  returning several namespaces per prefix and making every consumer choose.
- **The `namespace` field is highly repetitive** — one distinct value per prefix, stored on every entity
  of that prefix. That is what makes the pivot facet possible in one query instead of a lookup per
  prefix, and docValues compress the repetition.
- **The pivot's cost at corpus scale is not yet measured.** On the `oxo-config-test` corpus (6186
  entities, 63 prefixes) the pivot answers in ~6 ms and the endpoint in ~30 ms. The production entity
  collection is orders of magnitude larger with ~1339 prefixes, and a two-level pivot over it has not
  been timed. It is a single query rather than a lookup per prefix, and it faceted over two docValues
  string fields, so it should behave — but this is the number to check first if the listing gets slow,
  and the fallback is to precompute the per-prefix table during the dataload instead of pivoting at
  request time. Note the endpoint deliberately does not use `unique()` or any full-store
  high-cardinality aggregation, which has OOM-killed a 4 GB Solr before.
- **This is not the export's `curie_map`.** `MappingTsvExporter` resolves prefixes from the Bioregistry
  snapshot and so has both failure modes described above — it omits prefixes its rows use and
  contradicts ADR-0029 stems. That is [issue 118](https://github.com/EBISPOT/oxo2/issues/118) and is
  deliberately not fixed here; when it is, it should read the same per-prefix namespaces this ADR
  introduces rather than resolving prefixes a second way.
- `OntologyTarget` (the `?forSubject=` response) is unchanged — the request concerns the main listing,
  and a target selector needs counts, not IRIs.
