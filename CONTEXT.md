# OxO2 — Project Context

This document anchors the domain language and architectural shape of OxO2. It is the first place to look when introducing a new term, deciding which module 
owns a concern, or working out whether a planned change touches a cross-cutting constraint.

For *operational* details (how to build, how to run) see `README.md`. For *decisions and their rationale* see [`docs/adr/`](docs/adr/). 
For *per-module* shape, vocabulary, and surfaces see each module's `CONTEXT.md`.

## Purpose

OxO2 is a SSSOM-compliant ontology mapping service. It ingests SSSOM mapping sets from external sources, derives inferred 
mappings by applying the SSSOM chaining rules via the Nemo rules engine, indexes mappings and their explanations into Apache Solr, 
and serves them via a REST API consumed by a React frontend.

## Glossary

The glossary covers two kinds of terms: SSSOM standard vocabulary that OxO2 implements, and OxO2-introduced terms that span 
multiple modules. Terms from borrowed technologies (Nemo, Solr, Nextflow) are not detailed here — see § External surfaces for pointers.

### SSSOM standard vocabulary

- **Mapping** — a single assertion that a subject entity corresponds to an object entity under some predicate, with metadata about 
how the assertion was justified. Modelled in `oxo2-shared` as `Mapping`.
- **MappingSet** — a curated collection of `Mapping`s sharing provenance, licence, and other set-level metadata. The unit of 
ingestion and the unit over which chaining rules are applied. Modelled as `MappingSet`.
- **EntityReference** — a typed reference to an entity (typically a CURIE) used as the subject or object of a `Mapping`. Modelled as `EntityReference`.
- **predicate_id**, **subject_id**, **object_id** — the three components that identify what a mapping asserts: which entity (subject) 
is related, by which relation (predicate), to which other entity (object).
- **mapping_justification** — why the mapping is held to be true (e.g. asserted by curator, derived by lexical match, inferred by a chain rule). 
A SSSOM-defined enumeration.
- **subject_source**, **object_source** — the ontologies the subject and object come from. Used in faceting and filtering.

### OxO2 cross-cutting vocabulary

- **Asserted mapping** — a mapping that came directly from the input SSSOM file. Contrast with *inferred mapping*. Represented by `ChainRulesEnum.ASSERTED`.
- **Inferred mapping** — a mapping derived by applying SSSOM chaining rules over an existing mapping set. Modelled as 
`InferredMapping` in `oxo2-shared`. Inferred mappings carry the chain rule that produced them and a link to the explanation chain.
- **Chain rule** — a SSSOM-defined rule that derives a new mapping from existing ones (e.g. transitivity, inverse, generalisation). 
Implemented in OxO2 as `ChainRulesEnum` (`RCE`, `T`, `RI`, `RG`, `RCE-N` families) and as Nemo rules in `oxo2-json2inferences/chain-rules.rls`. 
See the SSSOM chaining-rules spec linked in § External surfaces.
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

- **Inference scope is per mapping set** — chaining rules apply over a single mapping set, never across sets. SSSOM-spec conformance. 
See [ADR-0001](docs/adr/0001-inference-scope-per-mapping-set.md). Affects `oxo2-dataload` (per-set Nemo invocations, within-set parallelism).
- **Solr is the sole data store** — no relational database; both mappings and mapping sets live in Solr collections `oxo2-mappings` 
and `oxo2-mappingsets`. See [ADR-0002](docs/adr/0002-solr-as-sole-data-store.md). Affects `oxo2-dataload` (denormalised documents at load time) and `oxo2-backend` (query patterns constrained by Solr).
- **Nextflow is the sole dataload execution path** — production dataload runs via `loadData.nextflow` only; per-stage `.sh` 
scripts are debug-only. See [ADR-0003](docs/adr/0003-nextflow-as-sole-dataload-path.md). Affects `oxo2-dataload`.
- **OxO2 is backwards compatible with OxO v1** — API surface answers v1's questions even where SSSOM terms are richer. 
See [ADR-0004](docs/adr/0004-backwards-compatible-with-oxo-v1.md). Affects `oxo2-backend` (API design) and `oxo2-frontend` (documentation surface).
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
   ├─ json2ttl       ──► per-set TTL facts
   ├─ nmo infer      ──► inferred mappings
   ├─ split + nmo trace (chunked)
   └─ explanations2json  ──► per-set explanation chain files
        │
        ▼
[oxo2-solr-dataload-client]  ──►  Solr: oxo2-mappings + oxo2-mappingsets
        │
        ▼
[oxo2-backend]  /api/v2/mappings, /api/v2/mapping-sets
        │
        ▼
[oxo2-frontend]  /search/:curies, /mapping/:id
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
