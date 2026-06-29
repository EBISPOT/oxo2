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
- **Inferred mapping** — a mapping derived by OxO's SSSOM reasoning (see § Cross-cutting constraints), not present in any input file. Modelled as `InferredMapping` in `oxo2-shared`. In the index they are **bare**
(`inference_type = SSSOM_INFERENCE`); the chain rule that produced them and their explanation chain are
not precomputed but reconstructed on demand ([ADR-0020](docs/adr/0020-defer-explanations-to-on-demand.md)).
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
- **Explanation** — the derivation step that justifies a single inferred mapping: which chain rule fired and which input mappings it consumed. Not precomputed; reconstructed on demand ([ADR-0020](docs/adr/0020-defer-explanations-to-on-demand.md)).
- **Explanation chain** — the full derivation tree for an inferred mapping, recording every chain-rule application back to asserted mappings. Not precomputed; reconstructed on demand ([ADR-0020](docs/adr/0020-defer-explanations-to-on-demand.md)).
- **Facts to trace** — the set of inferred mappings whose explanation chains need computing. _Dormant_: with explanations deferred ([ADR-0020](docs/adr/0020-defer-explanations-to-on-demand.md)) the dataload has no trace stage; the term applies again to the future on-demand service, which traces one conclusion at a time rather than chunked files.
- **Mapping group** — the set of mappings that share the same `subject_id`, `predicate_id`, `predicate_modifier`, and `object_id`: one 
asserted *meaning* of a triple, collapsed into a single row in the Search and Inferences result views (see § Cross-cutting constraints). 
Identified by the denormalised `spo_key` field. A relation and its negation (`predicate_modifier = Not`) form **different** groups. 
_Avoid_: collapsed row, duplicate mappings, SPO group.
- **Representative mapping** — the member of a **mapping group** shown as its parent row: the highest inference-tier member (`ASSERTED` over `SSSOM_INFERENCE`; the former "shorter chains first" tie-break is dropped now `distance`/`explanation_length` are not precomputed — [ADR-0020](docs/adr/0020-defer-explanations-to-on-demand.md)). Its subject/predicate/object are the ones displayed; the remaining 
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
- **Inferred mappings are indexed without explanations** — the dataload indexes inferred mappings
**bare** (`inference_type` + s/p/o + ids/labels; no explanation chain, asserted evidence, or
source-set union; `distance`/`explanation_length` carry inert model defaults rather than computed
values), built straight from `INFER_CROSS_SET`'s `inferences.ttl` (the
`~assertedTriple` rule already excludes asserted echoes). Explanation reconstruction costs a full
reasoning pass per conclusion, so it is deferred to a future async, cached, **on-demand** service
that reuses the retained (dormant) Java chain interpreter over `nmo` + `sssom.rls` +
`assertedCorpus.nq`. See [ADR-0020](docs/adr/0020-defer-explanations-to-on-demand.md) (supersedes
ADR-0018). Affects `oxo2-dataload` (no trace/explain/merge/`explanations2json` on the pipeline; a
bare inferred-mapping indexer instead). The `distance`/`explanation` backend/frontend/schema surface
is left dead pending that service (deferred cleanup, ADR-0020).
- **On-demand explanations are served by a resident Nemo engine (Proposed)** — the deferred
explanation service (ADR-0020) runs the cross-set chase **once at startup** and keeps the Nemo
`ExecutionEngine` resident, turning each single-conclusion explanation into a cheap backward trace
rather than a full ~10–20 min reasoning pass per request (that cost is a fresh-`nmo`-process cold
start, not an intrinsic per-trace cost). One trace at a time per engine → scale by replicas (each
~24 GB); no state snapshot → cold start re-chases (blue/green on data release); the resident engine
emits `nmo --trace-output` JSON so the retained `NemoHelper`/`NemoInferences` interpreter is reused
unchanged. Refines ADR-0020's cost premise. See
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
deliberate v1 semantic break, since hop counts cannot be reproduced over a flattened closure
([ADR-0020](docs/adr/0020-defer-explanations-to-on-demand.md) left `distance` inert). See
[ADR-0024](docs/adr/0024-cross-ontology-mapping.md) (under [ADR-0004](docs/adr/0004-backwards-compatible-with-oxo-v1.md)).
Affects `oxo2-dataload` (`subject_prefix` / `object_prefix` population + reindex), `oxo2-backend`
(`/api/v2/ontologies`, the prefix-filtered `GET /api/v2/mappings?from=&to=` + `POST …/search`,
`batch-map`, `?format=` export, the v1 `/api/search` adapter), and `oxo2-frontend` (from/to prefix
selectors on the Search tab, batch + export UI).
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
`loadData.jenkins.sh`). NB: [ADR-0020](docs/adr/0020-defer-explanations-to-on-demand.md) removes the
`trace`/`explain`/`merge`/`explanations2json` stages and the `inferSssomCrossSet.nf`
`from_trace`/`from_explain`/`from_merge` entry points, shortening the resumable stage list.
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
(sssom2json). NB: recovering the `priority` views feeds the ~569 MB gene view into the SSSOM cross-set pass — the closure-explosion guard ([ADR-0016](docs/adr/0016-single-pass-sssom-reasoning.md)) is now load-bearing.
- **Chunked tracing** — when computing explanation chains, the "facts to trace" file is split into chunks (default `trace_chunk_size = 20000`, in `inferSssomCrossSet.nf`) and traced in parallel. Tactical parallelism choice, not an ADR.

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
   └─ nmo infer sssom.rls (all sets)  ──► inferences.ttl (bare inferred mappings)
        │   (no trace/explain — explanations are on-demand, ADR-0020)
        ▼
[oxo2-solr-dataload-client]  ──►  Solr: oxo2-mappings + oxo2-mappingsets
        │   (asserted indexed, then bare inferred mappings from inferences.ttl)
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
