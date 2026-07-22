# OxO2 — Project Context

This document anchors the domain language and architectural shape of OxO2. It is the first place to look when introducing a new term, deciding which module 
owns a concern, or working out whether a planned change touches a cross-cutting constraint.

For *operational* details (how to build, how to run) see `README.md`. For *decisions and their rationale* see [`docs/adr/`](docs/adr/). 
For *per-module* shape, vocabulary, and surfaces see each module's `CONTEXT.md`.

## Purpose

OxO2 is a SSSOM-compliant ontology mapping service. It ingests SSSOM mapping sets from external sources, derives inferred mappings by running SSSOM chaining rules across all mapping sets via the Nemo rules engine, indexes mappings and their explanations into Apache Solr, and serves them via a REST API consumed by a React 
frontend.

## Glossary

The glossary covers two kinds of terms: SSSOM standard vocabulary that OxO2 implements, and OxO2-introduced terms that span 
multiple modules. Terms from borrowed technologies (Nemo, Solr, Nextflow) are not detailed here — see § External surfaces for pointers.

### SSSOM standard vocabulary

- **Mapping** — a single assertion that a subject entity corresponds to an object entity under some predicate, with metadata about 
how the assertion was justified. Modelled in `oxo2-shared` as `Mapping`.
- **MappingSet** — a curated collection of `Mapping`s sharing provenance, licence, and other set-level metadata. The unit of ingestion. Modelled as `MappingSet`.
- **EntityReference** — a typed reference to an entity (typically a CURIE) used as the subject or object of a `Mapping`. Modelled as `EntityReference`.
- **predicate_id**, **subject_id**, **object_id** — the three components that identify what a mapping asserts: which entity (subject) 
is related, by which relation (predicate), to which other entity (object).
- **predicate_modifier** — an optional qualifier on the predicate whose one standard value, `Not`, negates the relation: the 
mapping asserts that the predicate does *not* hold between subject and object. Because it inverts a mapping's meaning, a predicate 
displayed without its modifier misrepresents the mapping.
- **mapping_justification** — why the mapping is held to be true (e.g. asserted by curator, derived by lexical match, inferred by a chain rule). 
A SSSOM-defined enumeration.
- **subject_source**, **object_source** — the ontologies the subject and object come from. Used in filtering.

### OxO2 cross-cutting vocabulary

- **Asserted mapping** — a mapping that came directly from an input SSSOM file (`inference_type = ASSERTED`). Contrast with *inferred mapping*; represented in explanation chains by `ChainRulesEnum.ASSERTED`.
- **Inferred mapping** — a mapping derived by OxO's SSSOM reasoning (see § Cross-cutting constraints), not present in any input file. Modelled as `InferredMapping` in `oxo2-shared`. In the index each carries
(`inference_type = SSSOM_INFERENCE`) the chain rule that produced it and its full explanation chain,
precomputed by the dataload ([ADR-0028](docs/adr/0028-component-sharded-explanation-precompute.md)).
Narrower than the SSSOM notion of a *derived* mapping: a `semapv:LexicalMatching` mapping is derived 
but, having come from an input file, is *asserted* in OxO — *inferred* means produced specifically by OxO reasoning. Every mapping's origin is recorded in the `inference_type` field as one of `ASSERTED` or `SSSOM_INFERENCE`.
- **SSSOM inference** — an inferred mapping produced by SSSOM reasoning (`sssom.rls`): transitivity and role
chains over the strong mapping/equivalence predicates, applied **across all mapping sets** — OxO2's only
inference. `inference_type = SSSOM_INFERENCE`. Shown alongside asserted mappings by default.
- **Inferred mapping set** — the single mapping set holding OxO-derived inferred mappings, at
`https://www.ebi.ac.uk/oxo2/inferences`, whose `mapping_set_source` is the union of all contributing
sources. It carries `inference_type`; its IRI resolves to an OxO2 mapping-set view (see
[ADR-0012](docs/adr/0012-resolvable-inference-set-iris.md)).
- **Chain rule** — a rule that derives a new mapping from existing ones (e.g. transitivity, inverse, role chain). Implemented in OxO2 as `ChainRulesEnum` (`RCE`, `T`, `RI` families) and as Nemo rules in `oxo2-json2inferences/sssom.rls`. See the SSSOM chaining-rules spec linked in § External surfaces.
- **Explanation** — the derivation step that justifies a single inferred mapping: which chain rule fired and which input mappings it consumed. Precomputed by the dataload ([ADR-0028](docs/adr/0028-component-sharded-explanation-precompute.md)).
- **Explanation chain** — the full derivation tree for an inferred mapping, recording every chain-rule application back to asserted mappings. Precomputed and stored in the `explanation` field ([ADR-0028](docs/adr/0028-component-sharded-explanation-precompute.md)).
- **Explanation shard** — an independently-chaseable slice of the cross-set corpus: one connected component of the corpus's strong-predicate edges, plus every asserted quad whose subject and object are both inside it. A conclusion's whole proof lives in one shard, so tracing it against the shard's few thousand facts gives the same derivation as the full 55.9M-fact materialisation ([ADR-0028](docs/adr/0028-component-sharded-explanation-precompute.md)).
- **Facts to trace** — the set of inferred mappings whose explanation chains need computing, written as one semicolon-separated `nmo --trace-input-file` per explanation shard.
- **Mapping group** — the set of mappings that share the same `subject_id`, `predicate_id`, `predicate_modifier`, and `object_id`: one 
asserted *meaning* of a triple, collapsed into a single row in the Search and Inferences result views (see § Cross-cutting constraints). 
Identified by the denormalised `spo_key` field. A relation and its negation (`predicate_modifier = Not`) form **different** groups. 
_Avoid_: collapsed row, duplicate mappings, SPO group.
- **Representative mapping** — the member of a **mapping group** shown as its parent row: the highest inference-tier member (`ASSERTED` over `SSSOM_INFERENCE`; the "shorter chains first" tie-break remains dropped as a grouping rule, though `explanation_length` and `distance` are both precomputed again — [ADR-0028](docs/adr/0028-component-sharded-explanation-precompute.md), [ADR-0031](docs/adr/0031-inferred-mapping-distance-as-ontology-span.md)). Its subject/predicate/object are the ones displayed; the remaining 
members are reached by expanding the row.
- **Ontology prefix** — the CURIE prefix of a `subject_id` / `object_id` (`DOID:0001816` → `DOID`).
OxO2's notion of an "ontology" or v1 "datasource": there is no ontology entity, only the prefix.
Denormalised onto each mapping as `subject_prefix` / `object_prefix` for filtering and faceting
([ADR-0024](docs/adr/0024-cross-ontology-mapping.md)).
- **Cross-ontology mapping** — the query that returns every mapping from one or more **source**
ontologies (by subject prefix) into one or more **target** ontologies (by object prefix); OxO v1's
"map a datasource to a datasource". Directional (subject = source, object = target). Reuses the
precomputed SSSOM closure rather than walking a graph at query time. See
[ADR-0024](docs/adr/0024-cross-ontology-mapping.md).
- **Batch mapping** — cross-ontology mapping driven by an explicit list of input terms (CURIEs / IRIs
/ labels) rather than a whole source ontology; the only mode that can report **unmapped inputs** —
supplied terms with no mapping into the targets. _Why only this mode:_ OxO2 ingests mapping sets, not
ontologies, so it knows only *mapped* terms and cannot enumerate "all of DOID".

## Module map

OxO2 is a multi-module Maven build with a separately-built React frontend. Each top-level module has its own `CONTEXT.md`.

- **[`oxo2-shared`](oxo2-shared/CONTEXT.md)** — SSSOM data model (`Mapping`, `MappingSet`, `EntityReference`, `InferredMapping`, `ChainRulesEnum`, …) 
and Jackson serialization. The vocabulary library every other module depends on.
- **[`oxo2-dataload`](oxo2-dataload/CONTEXT.md)** — multi-stage pipeline that downloads SSSOM mapping sets, converts them to JSON, generates inferences 
+ explanations via Nemo, and loads everything into Solr. Orchestrated by Nextflow.
- **[`oxo2-backend`](oxo2-backend/CONTEXT.md)** — Spring Boot REST API serving mapping and mapping-set queries from Solr. Exposes `/api/v2/...`.
- **[`oxo2-frontend`](oxo2-frontend/CONTEXT.md)** — React + TypeScript single-page app providing search, browsing, and inferred-mapping visualisation.
- **[`oxo2-integration-tests`](oxo2-integration-tests/CONTEXT.md)** — Full-pipeline integration tests driven by per-rule SSSOM fixtures under `testcases/minimal/rules/`. The same `loadData.nextflow` run populates Solr with the test mapping sets so backend / frontend integration tests can read the resulting state without re-running the pipeline.

## Cross-cutting constraints

Decisions that bind multiple modules. Each ADR captures one decision and its consequences; this list points at where each one bites.

- **Reasoning is a single SSSOM cross-set pass** — SSSOM rules (transitivity + role chains over the strong
mapping/equivalence predicates) run **across all mapping sets** for findability, over the asserted mappings
only. OxO2 derives no subsumption — the former per-set OWL phase was dropped (no value on either corpus). See
[ADR-0016](docs/adr/0016-single-pass-sssom-reasoning.md) (supersedes ADR-0009 and ADR-0001). Affects
`oxo2-dataload` (one Nemo pass: `sssom.rls` over the whole corpus).
- **A confidence gate can keep low-confidence edges out of inference** — with the global
`min_inference_confidence` key in the OxO config (default absent = disabled) set above 0, the `nquads`
stage omits any mapping whose SSSOM `confidence` is present and strictly below the threshold, so it never
seeds the closure; a mapping with no confidence always passes, and dropped edges stay indexed as asserted
and are listed per set in a `.dropped-low-confidence.tsv` sidecar. Addresses only the *low-confidence*
failure mode, not a wrong high/absent-confidence mapping (that is the still-proposed ADR-0017
blast-radius guard). See [ADR-0037](docs/adr/0037-confidence-gate-on-inference-corpus.md). Affects
`oxo2-dataload` (`OxoConfiguration`, `lib/InferenceConfidenceThreshold.groovy`, `JSON2NQuads`,
`json2nquadsNextflow.sh`, `inferSssomCrossSet.nf`).
- **Explanations are precomputed by component-sharded chase+trace** — a conclusion's whole proof lives
inside one connected component of the corpus's strong-predicate edges, because every `sssom.rls` rule
chains its head's subject to its object through body atoms. So the dataload partitions
`assertedCorpus.nq` into per-component **explanation shards**, chases each shard's few-thousand-fact
corpus, and traces all of that shard's conclusions against the warm engine. Per-trace cost tracks the
size of the store being traced against, not the size of the proof: ~0.3–0.9 ms per conclusion sharded
versus ~6.2 s against the full 55.9M-fact materialisation. All 14.9M inferred mappings cost 6.35 CPU-h.
Every inferred mapping therefore ships with its `explanation` chain, `asserted_mappings` evidence,
`explanation_length`, `distance`, and the inferred set's `mapping_set_source` union. See
[ADR-0028](docs/adr/0028-component-sharded-explanation-precompute.md) (supersedes ADR-0020, which
supersedes ADR-0018). Affects `oxo2-dataload` (the `shard`/`explain`/`explanations2json` stages).
- **Inferred `distance` is the ontology span** — the number of distinct CURIE prefixes (OxO2's notion
of a term's ontology, ADR-0024) across every subject/object in the explanation DAG, minus one, floored
at 1: an asserted or ≤2-ontology mapping is distance 1, three ontologies is 2, and so on. It drives the
inferred ranking tier's per-hop decay. Reverses ADR-0028's "left inert" default. See
[ADR-0031](docs/adr/0031-inferred-mapping-distance-as-ontology-span.md). Affects `oxo2-shared`
(`EntityReference.getCuriePrefix`) and `oxo2-dataload` (`ExplainInferredMappings`).
- **Explanations are well-founded — the chase never copies an asserted triple** — every derivation
rule in `sssom.rls` guards its own head with `~assertedTriple(s, p, o)`, so no nil-UUID copy is
derived for a triple some set already asserts. Without the guard an involution (`SYM-*` twice, or
`RI4` then `RI5`) re-derives an asserted fact as a *distinct* atom: Nemo's trace stays acyclic over
its 4-ary atoms, but folding the `mapping_id` away for display collapses the two into one triple and
the conclusion appears inside its own proof. The guard is output-preserving — it changes proofs, not
conclusions. See [ADR-0033](docs/adr/0033-well-founded-explanations.md). Affects `oxo2-dataload`
(`sssom.rls`, `ExplainInferredMappings.hasFoldedCycle`) and `oxo2-integration-tests` (the
`explanation well-founded` assertion).
- **On-demand explanations are served by a resident Nemo engine (Proposed; motivation largely
removed by ADR-0028)** — the once-deferred explanation service runs the cross-set chase **once at startup** and keeps the Nemo
`ExecutionEngine` resident, turning each single-conclusion explanation into a cheap backward trace
rather than a full ~10–20 min reasoning pass per request (that cost is a fresh-`nmo`-process cold
start, not an intrinsic per-trace cost). One trace at a time per engine → scale by replicas (each
~24 GB); no state snapshot → cold start re-chases (blue/green on data release); the resident engine
emits `nmo --trace-output` JSON so the retained `NemoHelper`/`NemoInferences` interpreter is reused
unchanged. Its latency numbers stand, but its "precompute is infeasible" premise does not — that
bounded a whole-materialisation trace, not a per-component one ([ADR-0028](docs/adr/0028-component-sharded-explanation-precompute.md)). See
[ADR-0021](docs/adr/0021-on-demand-explanation-resident-nemo-engine.md) and
[docs/on-demand-explanation-service.md](docs/on-demand-explanation-service.md). Affects a new
`oxo2-explain` service, `oxo2-backend` (format conclusion + interpret trace), and the data release
(publishes `sssom.rls` + `assertedCorpus.nq` as runtime artifacts).
- **Solr is the sole data store** — no relational database; both mappings and mapping sets live in Solr collections `oxo2-mappings` 
and `oxo2-mappingsets`. See [ADR-0002](docs/adr/0002-solr-as-sole-data-store.md). Affects `oxo2-dataload` (denormalised documents at load time) and `oxo2-backend` (query patterns constrained by Solr).
- **Origin is a denormalised `inference_type` field** — both mappings and mapping sets carry `inference_type` (`ASSERTED` / `SSSOM_INFERENCE`), set once at dataload from OxO provenance, as the single queryable origin signal; the SSSOM 
provenance fields (`mapping_source`, `mapping_set_source`) stay authoritative for export but are not the filter. The API filter is 
multi-select (absent = all); the UI shows {Asserted, SSSOM inference} and ranks Asserted > SSSOM. See 
[ADR-0011](docs/adr/0011-inference-type-replaces-is-inferred.md) (supersedes ADR-0008). Affects `oxo2-dataload` (writers set it), 
`oxo2-shared` (`InferenceType` enum), `oxo2-backend` (filter + relevance boost), and `oxo2-frontend` (labels, default, ranking).
- **Inference provenance is carried as `urn:uuid` named graphs** — Nemo facts are N-Quads `<s> <p> <o> <urn:uuid:mapping_id> .` 
so the trace attributes each premise to its exact asserted mapping and source set; inferred conclusions use the nil UUID. See 
[ADR-0010](docs/adr/0010-carry-mapping-provenance-via-nquads.md). Affects `oxo2-dataload` (N-Quads emit, rule arity, explanation 
builder) and `oxo2-mappings` (`mapping_id` becomes `indexed`).
- **Entity IRIs are canonicalised via a curated override table** — SSSOM sets disagree on the IRI 
stem for some prefixes (e.g. `MESH:` as `id.nlm.nih.gov` vs `identifiers.org`), which would give Nemo 
two nodes for one entity and produce duplicate conclusions plus circular explanations. A curated 
`iri-prefix-overrides.json` pins one canonical stem per problematic prefix, applied at 
`EntityReference.toUri` ahead of the set's curie_map and the Bioregistry fallback (ADR-0015); 
`PrefixDivergenceDetector` surfaces new candidates. See 
[ADR-0029](docs/adr/0029-canonical-entity-iri-overrides.md). Affects `oxo2-shared` (override loader + 
expansion), `oxo2-sssom2json` (detector), and `oxo2-json2inferences` (folded-cycle guard).
- **Inference-set IRIs are resolvable under the OxO2 base** — inference sets live under 
`https://www.ebi.ac.uk/oxo2/inferences[/…]` and resolve to an OxO2 mapping-set view. See 
[ADR-0012](docs/adr/0012-resolvable-inference-set-iris.md). Affects `oxo2-dataload` (set ids), `oxo2-backend` (`GET 
/api/v2/mapping-sets/by-id?mappingSetId=<IRI>`), and `oxo2-frontend` (`/inferences` route).
- **Same-SPO mappings are grouped in result views** — the Search and Inferences tables collapse mappings sharing 
(`subject_id`, `predicate_id`, `predicate_modifier`, `object_id`) into one *mapping group* row, via the denormalised Solr `spo_key` 
field and the Solr CollapsingQParserPlugin + ExpandComponent (ADR-0023, replacing the original result grouping whose 
`group.ngroups` count cost ~19s on high-frequency terms). Collapse is presentation-layer, layered on top of the inference-type 
filter, and a page counts groups not documents (the collapsed `numFound`); the Advanced tab stays flat. See 
[ADR-0013](docs/adr/0013-group-same-spo-mappings-in-result-views.md) and 
[ADR-0023](docs/adr/0023-collapse-for-same-spo.md). Affects `oxo2-dataload` (`spo_key` population + reindex), `oxo2-backend` 
(collapse query path + `group_members` transport), and `oxo2-frontend` (expandable rows, paging over groups).
- **Cross-ontology mapping is a prefix filter over the precomputed closure** — mapping a source
ontology to target ontologies is a directional filter on denormalised `subject_prefix` / `object_prefix`
(subject = source, object = target), served from the existing mappings index; it does **not** traverse
a graph at query time the way OxO v1 did, because the SSSOM cross-set closure
([ADR-0016](docs/adr/0016-single-pass-sssom-reasoning.md)) is already materialised. v1's
`POST /api/search` is honoured wire-for-wire (HAL `SearchResult` envelope), but v1's query-time
`distance` (hop count) degrades to a tier toggle (`1` = asserted, `≠1` = asserted ∪ inferred) — a
deliberate v1 semantic break, since v1 hop counts cannot be reproduced over a flattened closure. The
stored `distance` is now populated as an ontology span, not a v1 hop count
([ADR-0031](docs/adr/0031-inferred-mapping-distance-as-ontology-span.md)), so v1's per-mapping
`distance` stays a coarse `asserted ? 1 : 2` sentinel. See
[ADR-0024](docs/adr/0024-cross-ontology-mapping.md) (under [ADR-0004](docs/adr/0004-backwards-compatible-with-oxo-v1.md)).
Affects `oxo2-dataload` (`subject_prefix` / `object_prefix` population + reindex), `oxo2-backend`
(`/api/v2/ontologies`, the prefix-filtered `GET /api/v2/mappings?from=&to=` + `POST …/search`,
`batch-map`, `?format=` export, the v1 `/api/search` adapter), and `oxo2-frontend` (from/to prefix
selectors on the Search tab, batch + export UI).
- **The default search matches the subject side only** — a mapping is a directed *subject → predicate
→ object* statement, and a mapping search is asked from the subject's perspective, so the classified
(default) search matches each term against the subject column alone (IRI → `subject_iri`, CURIE →
`subject_id`, label → the subject label field the match mode picks) via the one `subjectSideClause`
shared with batch mapping (ADR-0024) and the v1 adapter. Mappings *into* a term are still found when the
predicate is strong — the closure ([ADR-0016](docs/adr/0016-single-pass-sssom-reasoning.md))
materialises the symmetric/inverse row whose subject is the term — but weak, non-closed predicates
(`skos:closeMatch`, `oboInOwl:hasDbXref`, …) become directional and are reached via the Advanced tab or
the v1 listing. The Advanced tab, `queryFields` and column filters still target any field. Query-only
change, no reindex. See [ADR-0030](docs/adr/0030-subject-side-default-search.md). Affects `oxo2-backend`
(classified-query construction) and `oxo2-frontend` (search copy).
- **Label matching in a normal search is a configurable mode** — a free-text (label) term in the
classified/normal search matches the **subject** label ([ADR-0030](docs/adr/0030-subject-side-default-search.md))
by one of three modes: *partial* (the analyzed `subject_label` subsequence match), *case-insensitive
exact* (the whole label folded, via the `subject_label_ci` `string_ci` field) — the **default** — or
*case-sensitive exact* (`subject_label_str`). IRI / CURIE terms stay exact `subject_iri` / `subject_id`
lookups regardless, and the Advanced tab and batch-map / v1 paths are unaffected. Changing the default
from partial to case-insensitive exact required a schema field + reindex. See
[ADR-0026](docs/adr/0026-configurable-label-match-mode.md). Affects `oxo2-dataload` (`string_ci` field
type + `*_label_ci` fields + reindex), `oxo2-shared` (`LabelMatchType`), `oxo2-backend` (`labelMatch`
request field + classified-query field selection), and `oxo2-frontend` (the match-mode control on the
Search tab, carried in the URL `?match=`).
- **OxO2 exposes the mapping-commons SSSOM API at `/api/sssom`** — a third API surface (beside
`/api/v2` and the v1 compat paths) implementing the [SSSOM spec](https://github.com/mapping-commons/sssom-api):
`/mappings` (with a `filter=field|operator|value` grammar, plus a `mapping_set_id` param scoping to one
set — the reference's `/mapping_sets/{id}/mappings`), `/mappings/{id}`, `/mappings/{field}/{value}`,
`/entities`, `/mapping_sets` and `/stats`. Matches the reference envelope
`{data, pagination, facets}` (1-based `page`/`limit`, absolute links, Solr-native facets) and fixes its
pathologies (400 not 302; facets from the facet/stats components, not a full result-set scan). Serves
asserted **and** inferred mappings with their extension slots, same-SPO collapsed, hiding no predicate.
Reuses the provenance ranking and collapse of the v2 search. `nb_entity` in `/stats` is a HLL estimate
over a new additive `entity_id` copy-field (subject ∪ object) that is empty until the next full dataload.
See [ADR-0032](docs/adr/0032-sssom-spec-api.md). Affects `oxo2-backend` (`controller/api/sssom/`,
`SssomQueryBuilder` / `SssomResultMapper`, `server.forward-headers-strategy`) and `oxo2-dataload`
(the `entity_id` field + copyFields on the mappings schema).
- **Typeahead is served by a third Solr collection, `oxo2-entities`** — one document per distinct
entity (CURIE, label, IRI, prefix, subject/object membership, mapping count), derived from the indexed
mappings by a new `mappings2entities` dataload stage. `oxo2-mappings` is denormalised (one doc per
mapping), so it has no per-entity view and its only n-gram fields are *infix* — both wrong for a
typeahead. Prefix matching uses two new **edge**-n-gram field types (prefix-of-any-token for labels,
whole-string for CURIEs); Solr's `SuggestComponent` is deliberately **not** used, because it takes no
`fq` (so it could honour neither the subject-side default nor the ontology/corpus filters) and over a
denormalised index would suggest once per *mapping*. Three surfaces, three mechanisms by field
cardinality: the main box → global entity suggest (subject-side, ADR-0030); column filters →
a `facet.prefix` scoped to the **live query**, reusing `SolrQueryBuilder.buildSolrQuery` so a
suggestion can never yield zero rows; Advanced search → entity suggest, a cached distinct-values facet,
or nothing, per field. Picking a suggestion applies an **exact** filter (`FilterMatchType`); typing
free text keeps *contains*. `oxo2-entities` is a read model, rebuildable alone via
`START_STAGE=mappings2entities`. Facets read only whole-value, **original-casing** fields — faceting a
`text_general` field returns analyzed tokens ("the", "disease") and faceting a case-folding `_ci` field
returns lower-cased values, both unusable as suggestions — so the mappings schema gains seven `_str`
twins for the `text_general` vocabulary fields. See
[ADR-0034](docs/adr/0034-entity-collection-for-typeahead.md). Affects `oxo2-dataload` (the new
collection, the `mappings2entities` stage, and the seven `_str` twins — a bounded re-post, not a
re-inference), `oxo2-shared` (`EntityConstants`, `FilterMatchType`, `EntitySide`), `oxo2-backend`
(`SuggestController`, `EntitySuggestQueryBuilder`) and `oxo2-frontend` (the suggest components and the
48-field tier map).
- **The weak predicates are a user-visible control, and the typeahead obeys it** — `rdfs:subClassOf`
and `oboInOwl:hasDbXref` assert no equivalence and swamp an OLS-derived corpus, so both stay hidden by
default; but each is now independently revealable by a checkbox (search page and Predicate column
header), carried as `includeWeakPredicates` on the request and `wp` in the URL. Crucially the **entity
typeahead is filtered by the same selection**: `oxo2-entities` counts are bucketed per side and per
predicate (`{subject,object}_count_{strong,subclassof,hasdbxref}`), and a suggest filters, ranks and
labels on the buckets the checkboxes currently make visible. Suggesting on the unfiltered totals
instead is what let the typeahead offer 46,783 entities on a corpus whose default search could reach
3,714 — 92% of suggestions completed to an empty table. A suggestion must be a promise that the search
returns something. `WeakPredicate` in `oxo2-shared` is the single source of truth, because the search
filter and the entity fold must agree on the pair by construction. See
[ADR-0035](docs/adr/0035-weak-predicates-as-a-user-visible-control.md). Affects `oxo2-shared`
(`WeakPredicate`, `EntityConstants`), `oxo2-dataload` (the fold now reads `predicate_iri`; the entity
schema gains six count fields; `copySolrConfig entities-only` now wipes rather than skips),
`oxo2-backend` (`SolrQueryBuilder`, `EntitySuggestQueryBuilder`, `SuggestController`) and
`oxo2-frontend` (the two checkboxes, the `wp` URL param).
- **Search-form options are grouped by intent behind one disclosure; result order lives on the
results table** — the search surface keeps only the term input, from/to ontology selectors and
buttons; everything else sits behind "More options" as three groups (corpus with nested
mapping-set picker, weak predicates, label matching), whose collapsed summary must name every
non-default choice. The Best match / confidence / recency trio is a toolbar control on the results
table writing the same `?sort` the column popovers write. A new search option must join one of the
three groups and add its summary hint. See
[ADR-0036](docs/adr/0036-search-form-options-grouped-by-intent.md). Affects `oxo2-frontend` only.
- **Ontology sets carry a promoted prefix and name; the picker splits by category** — OLS extracts put
the ontology's CURIE prefix and display name in the SSSOM `other` bag, and the dataload promotes them to
discrete `prefix` / `ontology` fields on `oxo2-mappingsets` (serialize-only accessors on `MappingSet`,
derived from `other`, which is kept). With `mapping_set_category` ([ADR-0027](docs/adr/0027-config-driven-mapping-set-category.md))
now also on `MappingSetSummary`, the search set-picker renders two tables — curated sets (plus the
uncategorised inferences set) and a lean Ontologies table (Ontology + Prefix). Populating the two fields
needs a reindex; the category split works against the current index. See
[ADR-0038](docs/adr/0038-promote-ontology-prefix-from-other-block.md). Affects `oxo2-shared`
(`MappingSet`, `MappingConstants`/`MappingSetConstants`), `oxo2-dataload` (`oxo2-mappingsets` schema),
`oxo2-backend` (`MappingSetSummary`, `MappingSetController`), and `oxo2-frontend` (`MappingSetSelector`).
- **Nextflow is the sole dataload execution path** — production dataload runs via `loadData.nextflow` only; per-stage `.sh` 
scripts are debug-only. See [ADR-0003](docs/adr/0003-nextflow-as-sole-dataload-path.md). Affects `oxo2-dataload`.
- **The dataload is resumable from a chosen (sub)stage** — parameterised by `START_STAGE` (default
`download` = full run); substage resume reads *published* artifacts under `$OXO2_DATA` (never Nextflow's
work dir, which is wiped every run), and stage-ownership cleanup preserves earlier stages' outputs. Both
orchestrators share one contract: the ordered stage list, cleanup, checkpoint and Solr wipe/needed
decisions live in the sourced `oxo2-dataload/loadData.lib.sh`, used by `loadData.slurm` (HPC) and
`loadData.nextflow` (local/integration) alike — so a new stage must be declared in the library, not in
either script. On HPC a Jenkins Freestyle job drives it over SSH (the `ssh` plugin's remote-shell build
step, against a Jenkins-global SSH site — no credentials in the repo). Operational layer on top of, not a
replacement for, ADR-0003. See [ADR-0019](docs/adr/0019-resumable-hpc-dataload.md) (HPC) and
[ADR-0022](docs/adr/0022-resumable-local-dataload-shared-library.md) (the shared library that extends it to
local). Affects `oxo2-dataload` (`loadData.lib.sh`, `loadData.slurm`/`.hpc`, `loadData.nextflow`,
`loadData.jenkins.sh`). NB: [ADR-0028](docs/adr/0028-component-sharded-explanation-precompute.md)
adds the `shard`/`explain` substages (one Nextflow process graph, `explainSssomCrossSet.nf`, resume
entry `from_explain_shard`) and replaces `inferences2json` with `explanations2json`.
- **OxO2 is backwards compatible with OxO v1** — API surface answers v1's questions even where SSSOM terms are richer. 
See [ADR-0004](docs/adr/0004-backwards-compatible-with-oxo-v1.md). Affects `oxo2-backend` (API design) and `oxo2-frontend` (documentation surface).
- **GitHub registries are fetched via archive tarball** — GitHub mapping registries download as the default-branch archive 
tarball over plain HTTP (no GitHub Contents API, no token), extracting only the configured directory; avoids the shared-NAT 
60 req/hr API rate limit. See [ADR-0007](docs/adr/0007-github-registries-via-archive-tarball.md). Affects `oxo2-dataload` (downloader).
- **Mapping Commons is ingested via its aggregated catalogue** — the `mapping_commons_registry` source type reads 
`mapping-commons.github.io`'s `data/mapping-specifications.json` (a registry-of-registries already aggregated to one JSON array) 
and downloads each `type=sssom` `content_url` — namespaced per source registry (distinct sets sharing a filename, e.g. the five 
biopragmatics SeMRA-landscape `priority` views, kept and disambiguated by landscape name from the source `registry.yml` — 
gene/cell/protein/anatomy/disease — falling back to Zenodo record id; only exact-duplicate URLs collapse), gunzipping `*.gz` — 
dropping the FAIR-transform registry and any basenames in the entry's `exclude` list (the visible guard against the 
closure-exploding SeMRA `processed`/`raw` assemblies). See [ADR-0014](docs/adr/0014-mapping-commons-registry-via-specifications-json.md). 
Affects `oxo2-dataload` (downloader, default `oxo-config.json`).
- **Bare SSSOM sets are recovered, not dropped** — a TSV with no metadata header and no external `.yml` (e.g. the 
biopragmatics SeMRA landscape `priority` views) has its `MappingSet` synthesised from the per-row set-level columns, and 
CURIEs expand against a bundled **Bioregistry** prefix-map snapshot (`oxo2-shared`'s `BioregistryPrefixMap`) used as the 
fallback `curie_map` — applied only to sets that declare no prefixes of their own. The sssom2json output filename is the 
input's sssom-root-relative path flattened, so same-basename sets across sub-directories don't collide. See 
[ADR-0015](docs/adr/0015-default-prefix-map-and-metadata-synthesis-for-bare-sssom.md). Affects `oxo2-shared`, `oxo2-dataload` 
(sssom2json). NB: recovering the `priority` views feeds the ~569 MB gene view into the SSSOM cross-set pass **unguarded** — the component-size guard intended to bound its closure ([ADR-0017](docs/adr/0017-cross-set-inference-corpus-component-size-guard.md)) was scoped but never built, so the pass is safe only because the loaded priority views form no giant strong-predicate component.
- **Explanation shard sizing** — the corpus is partitioned into per-component explanation shards, each capped at `max_shard_entities = 1200` entities (in `explainSssomCrossSet.nf`), and their traces are interpreted `explain_bundle_size = 100` shards per JVM (in `explanations2json.nf`). The entity cap bounds per-trace cost, which is linear in a shard's dictionary size; the bundle size amortises JVM and Solr-connection startup. Tactical sizing choices under [ADR-0028](docs/adr/0028-component-sharded-explanation-precompute.md), not separate decisions.

## End-to-end flow

```
SSSOM mapping set URLs
        │  (oxo-config.json lists sources)
        ▼
[oxo2-downloader]  ──►  SSSOM TSV files
        │
        ▼
[oxo2-sssom2json]  ──►  per-set JSON (Mapping, MappingSet)
        │
        ▼
[oxo2-json2inferences]
   ├─ json2nquads    ──► N-Quads facts (mapping_id as urn:uuid graph)
   ├─ nmo infer sssom.rls (all sets)  ──► inferences.ttl (the inferred mappings)
   ├─ shardConclusions  ──► per-component shards (corpus + facts to trace)
   └─ nmo trace per shard  ──► shardChains/*-chains.json  (ADR-0028)
        │
        ▼
[oxo2-solr-dataload-client]  ──►  Solr: oxo2-mappings + oxo2-mappingsets
        │   (asserted indexed, then inferred mappings + explanation chains)
        │
        ▼
[oxo2-backend]  /api/v2/mappings, /api/v2/mapping-sets
        │
        ▼
[oxo2-frontend]  /search/:curies, /mapping/:id, /inferences
```

Detail per stage lives in `oxo2-dataload/CONTEXT.md` § Module notes.

## External surfaces

Pointers to systems and specifications OxO2 borrows from or exposes to. Terminology from these surfaces is *not* duplicated in 
the glossary above — follow the links provided here for more details.

- **SSSOM specification** — the standard OxO2 implements. https://mapping-commons.github.io/sssom/. Chaining rules in particular: 
https://mapping-commons.github.io/sssom/dev/chaining-rules/.
- **Nemo rules engine** — used by `oxo2-json2inferences` for inference (`nmo` CLI). Vocabulary: rule, fact, derivation, trace. 
See https://github.com/knowsys/nemo.
- **Apache Solr** — sole data store. Vocabulary: collection, core, schema. See https://solr.apache.org/. 
Collection configs live in `oxo2-dataload/solr-config/`.
- **Nextflow** — workflow engine for the dataload. Vocabulary: process, channel, queueSize. See https://www.nextflow.io/. 
Workflow definitions are `.nf` files under `oxo2-dataload/`.
- **REST API** — `/api/v2/mappings` (incl. `/{subjectId}`, `/search`, `/batch-map`, and `?from=&to=`
cross-ontology filtering with `?format=` export), `/api/v2/mapping-sets`, and `/api/v2/ontologies`;
plus the OxO v1 compatibility endpoint `/api/search` (HAL `SearchResult` envelope —
[ADR-0024](docs/adr/0024-cross-ontology-mapping.md)). Detail in `oxo2-backend/CONTEXT.md`.

---

Maintenance: when introducing a new domain term, renaming one, or changing a cross-cutting constraint, update this document and/or 
add an ADR in the same PR. See `CLAUDE.md` § Documentation conventions.
