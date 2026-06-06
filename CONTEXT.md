# OxO2 — Project Context

This document anchors the domain language and architectural shape of OxO2. It is the first place to look when introducing a new term, deciding which module 
owns a concern, or working out whether a planned change touches a cross-cutting constraint.

For *operational* details (how to build, how to run) see `README.md`. For *decisions and their rationale* see [`docs/adr/`](docs/adr/). 
For *per-module* shape, vocabulary, and surfaces see each module's `CONTEXT.md`.

## Purpose

OxO2 is a SSSOM-compliant ontology mapping service. It ingests SSSOM mapping sets from external sources, derives inferred 
mappings in two reasoning phases via the Nemo rules engine — OWL rules per mapping set and SSSOM chaining rules across all 
mapping sets — indexes mappings and their explanations into Apache Solr, and serves them via a REST API consumed by a React 
frontend.

## Glossary

The glossary covers two kinds of terms: SSSOM standard vocabulary that OxO2 implements, and OxO2-introduced terms that span 
multiple modules. Terms from borrowed technologies (Nemo, Solr, Nextflow) are not detailed here — see § External surfaces for pointers.

### SSSOM standard vocabulary

- **Mapping** — a single assertion that a subject entity corresponds to an object entity under some predicate, with metadata about 
how the assertion was justified. Modelled in `oxo2-shared` as `Mapping`.
- **MappingSet** — a curated collection of `Mapping`s sharing provenance, licence, and other set-level metadata. The unit of 
ingestion and the scope of phase-1 (OWL) reasoning. Modelled as `MappingSet`.
- **EntityReference** — a typed reference to an entity (typically a CURIE) used as the subject or object of a `Mapping`. Modelled as `EntityReference`.
- **predicate_id**, **subject_id**, **object_id** — the three components that identify what a mapping asserts: which entity (subject) 
is related, by which relation (predicate), to which other entity (object).
- **predicate_modifier** — an optional qualifier on the predicate whose one standard value, `Not`, negates the relation: the 
mapping asserts that the predicate does *not* hold between subject and object. Because it inverts a mapping's meaning, a predicate 
displayed without its modifier misrepresents the mapping.
- **mapping_justification** — why the mapping is held to be true (e.g. asserted by curator, derived by lexical match, inferred by a chain rule). 
A SSSOM-defined enumeration.
- **subject_source**, **object_source** — the ontologies the subject and object come from. Used in faceting and filtering.

### OxO2 cross-cutting vocabulary

- **Asserted mapping** — a mapping that came directly from an input SSSOM file (`inference_type = ASSERTED`). Contrast with *inferred mapping*; represented in explanation chains by `ChainRulesEnum.ASSERTED`.
- **Inferred mapping** — a mapping derived by OxO's two-phase reasoning (see § Cross-cutting constraints), not present in any 
input file. Modelled as `InferredMapping` in `oxo2-shared`. Inferred mappings carry the chain rule that produced them and a link 
to the explanation chain. Narrower than the SSSOM notion of a *derived* mapping: a `semapv:LexicalMatching` mapping is derived 
but, having come from an input file, is *asserted* in OxO — *inferred* means produced specifically by OxO reasoning. Every 
mapping's origin is recorded in the `inference_type` field as one of `ASSERTED`, `OWL_INFERENCE`, or `SSSOM_INFERENCE`.
- **OWL inference** — an inferred mapping produced by **phase 1** (OWL reasoning, `owl.rls`): the rules that derive 
`rdfs:subClassOf` / `rdfs:subPropertyOf` (`RCE-N1`–`RCE-N4`, plus subClassOf/subPropertyOf transitivity), applied **per mapping 
set**. `inference_type = OWL_INFERENCE`. A last-resort signal — hidden in the default UI filter.
- **SSSOM inference** — an inferred mapping produced by **phase 2** (SSSOM reasoning, `sssom.rls`): transitivity and role chains 
over the strong mapping/equivalence predicates, applied **across all mapping sets**. `inference_type = SSSOM_INFERENCE`. Shown 
alongside asserted mappings by default.
- **Inferred mapping set** — a mapping set holding OxO-derived inferred mappings. There are two kinds: one **OWL-inference set 
per source set** (phase 1), identified by `https://www.ebi.ac.uk/oxo2/inferences/<URLEncoded(source id)>` and linked to its origin 
via `mapping_set_source`; and a **single SSSOM-inference set** (phase 2) at `https://www.ebi.ac.uk/oxo2/inferences`, whose 
`mapping_set_source` is the union of all contributing sources. Both carry `inference_type`; their IRIs resolve to an OxO2 
mapping-set view (see [ADR-0012](docs/adr/0012-resolvable-inference-set-iris.md)).
- **Chain rule** — a rule that derives a new mapping from existing ones (e.g. transitivity, inverse, role chain). Implemented in 
OxO2 as `ChainRulesEnum` (`RCE`, `T`, `RI`, `RG`, `RCE-N` families) and as Nemo rules split across `oxo2-json2inferences/owl.rls` 
(phase 1) and `oxo2-json2inferences/sssom.rls` (phase 2). See the SSSOM chaining-rules spec linked in § External surfaces.
- **Explanation** — the derivation step that justifies a single inferred mapping: which chain rule fired and which input mappings it consumed.
- **Explanation chain** — the full derivation tree for an inferred mapping, recording every chain-rule application back to asserted mappings.
- **Facts to trace** — the set of inferred mappings whose explanation chains still need to be computed. Produced by the inference stage, 
consumed by the trace stage. Lives as per-set files split into chunks for parallel tracing (see § Cross-cutting constraints).

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

- **Reasoning is two-phase** — phase 1 (OWL rules: `rdfs:subClassOf`/`subPropertyOf` derivation) runs **per mapping set**; phase 2 
(SSSOM rules: transitivity + role chains over strong mapping/equivalence predicates) runs **across all mapping sets** for 
findability. The phases are independent (phase 2 sees only asserted mappings). See 
[ADR-0009](docs/adr/0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md) (supersedes ADR-0001). Affects `oxo2-dataload` (two 
Nemo passes: `owl.rls` per set, `sssom.rls` over the whole corpus).
- **Solr is the sole data store** — no relational database; both mappings and mapping sets live in Solr collections `oxo2-mappings` 
and `oxo2-mappingsets`. See [ADR-0002](docs/adr/0002-solr-as-sole-data-store.md). Affects `oxo2-dataload` (denormalised documents at load time) and `oxo2-backend` (query patterns constrained by Solr).
- **Origin is a denormalised `inference_type` field** — both mappings and mapping sets carry `inference_type` (`ASSERTED` / 
`OWL_INFERENCE` / `SSSOM_INFERENCE`), set once at dataload from OxO provenance, as the single queryable origin signal; the SSSOM 
provenance fields (`mapping_source`, `mapping_set_source`) stay authoritative for export but are not the filter. The API filter is 
multi-select (absent = all); the UI defaults to {Asserted, SSSOM inference} and ranks Asserted > SSSOM > OWL. See 
[ADR-0011](docs/adr/0011-inference-type-replaces-is-inferred.md) (supersedes ADR-0008). Affects `oxo2-dataload` (writers set it), 
`oxo2-shared` (`InferenceType` enum), `oxo2-backend` (filter + relevance boost), and `oxo2-frontend` (labels, default, ranking).
- **Inference provenance is carried as `urn:uuid` named graphs** — Nemo facts are N-Quads `<s> <p> <o> <urn:uuid:mapping_id> .` 
so the trace attributes each premise to its exact asserted mapping and source set; inferred conclusions use the nil UUID. See 
[ADR-0010](docs/adr/0010-carry-mapping-provenance-via-nquads.md). Affects `oxo2-dataload` (N-Quads emit, rule arity, explanation 
builder) and `oxo2-mappings` (`mapping_id` becomes `indexed`).
- **Inference-set IRIs are resolvable under the OxO2 base** — inference sets live under 
`https://www.ebi.ac.uk/oxo2/inferences[/…]` and resolve to an OxO2 mapping-set view. See 
[ADR-0012](docs/adr/0012-resolvable-inference-set-iris.md). Affects `oxo2-dataload` (set ids), `oxo2-backend` (`GET 
/api/v2/mapping-sets/{id}`), and `oxo2-frontend` (`/inferences` route).
- **Nextflow is the sole dataload execution path** — production dataload runs via `loadData.nextflow` only; per-stage `.sh` 
scripts are debug-only. See [ADR-0003](docs/adr/0003-nextflow-as-sole-dataload-path.md). Affects `oxo2-dataload`.
- **OxO2 is backwards compatible with OxO v1** — API surface answers v1's questions even where SSSOM terms are richer. 
See [ADR-0004](docs/adr/0004-backwards-compatible-with-oxo-v1.md). Affects `oxo2-backend` (API design) and `oxo2-frontend` (documentation surface).
- **GitHub registries are fetched via archive tarball** — GitHub mapping registries download as the default-branch archive 
tarball over plain HTTP (no GitHub Contents API, no token), extracting only the configured directory; avoids the shared-NAT 
60 req/hr API rate limit. See [ADR-0007](docs/adr/0007-github-registries-via-archive-tarball.md). Affects `oxo2-dataload` (downloader).
- **Per-set chunked tracing** — when computing explanation chains, the per-set "facts to trace" file is split into chunks 
(default `trace_chunk_size = 100 000`, in `inferAndExplainMappings.nf`) and traced in parallel. Tactical parallelism choice, not an ADR.

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
   ├─ phase 1: nmo infer owl.rls   (per set)   ──► OWL inferences
   ├─ phase 2: nmo infer sssom.rls (all sets)  ──► SSSOM inferences
   ├─ split + nmo trace (chunked)
   └─ explanations2json  ──► explanation chain files (provenance via mapping_id)
        │
        ▼
[oxo2-solr-dataload-client]  ──►  Solr: oxo2-mappings + oxo2-mappingsets
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
- **Apache Solr** — sole data store. Vocabulary: collection, core, schema, faceting. See https://solr.apache.org/. 
Collection configs live in `oxo2-dataload/solr-config/`.
- **Nextflow** — workflow engine for the dataload. Vocabulary: process, channel, queueSize. See https://www.nextflow.io/. 
Workflow definitions are `.nf` files under `oxo2-dataload/`.
- **REST API** — `/api/v2/mappings` (incl. `/{subjectId}` and `/search`) and `/api/v2/mapping-sets`. Detail in `oxo2-backend/CONTEXT.md`.

---

Maintenance: when introducing a new domain term, renaming one, or changing a cross-cutting constraint, update this document and/or 
add an ADR in the same PR. See `CLAUDE.md` § Documentation conventions.
