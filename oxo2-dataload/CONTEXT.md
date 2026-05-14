# oxo2-dataload — Module Context

See [`/CONTEXT.md`](../CONTEXT.md) for the project-wide glossary and cross-cutting constraints. This document covers what this 
module specifically owns.

## Purpose

`oxo2-dataload` is the pipeline that turns a configured list of SSSOM mapping-set URLs into populated Solr collections. 
It downloads SSSOM TSVs, converts them to OxO2 JSON, runs the Nemo rules engine to derive inferred mappings and their 
explanation chains, and loads everything into Solr. Orchestration is by Nextflow ([ADR-0003](../docs/adr/0003-nextflow-as-sole-dataload-path.md)).

## Vocabulary introduced here

The cross-cutting terms `inferred mapping`, `chain rule`, `explanation`, `explanation chain`, and `facts to trace`
(defined in `/CONTEXT.md` § Glossary) originate in this module — they describe artifacts produced by the inference stage.

Module-local artifact names worth knowing:

- **Per-set TTL fact file** — RDF triples generated from a mapping set's JSON, fed to Nemo as input. Produced by `json2ttl.{sh,nf}`.
- **Inferences file** — Nemo's output for a mapping set: the derived inferred mappings before tracing. Produced by `inferMappings.{sh,nf}`.
- **Trace chunk** — a slice of the per-set facts-to-trace file (default `trace_chunk_size = 100 000`), used as the parallelism 
unit for `nmo trace`. See `inferAndExplainMappings.nf`.
- **Chain file** — per-set or per-chunk JSON file containing explanation chains. Chunk-level chain files are merged 
into the per-set chain file by `mergeChainFiles.sh`.

## Depends on

External:
- **Nextflow** — workflow orchestration ([ADR-0003](../docs/adr/0003-nextflow-as-sole-dataload-path.md)).
- **Nemo (`nmo` CLI)** — rules engine; used for both inference and trace stages.
- **Apache Solr** — destination data store ([ADR-0002](../docs/adr/0002-solr-as-sole-data-store.md)).

Internal (sub-modules):
- `oxo2-downloader` — fetches SSSOM TSVs from the URLs listed in `OXO2_CONFIG`.
- `oxo2-sssom2json` — TSV → JSON conversion.
- `oxo2-json2inferences` — JSON → TTL → Nemo infer → trace → explanations JSON. Contains `chain-rules.rls` (Nemo rules 
implementing the SSSOM chaining-rules spec).
- `oxo2-solr-dataload-client` — Solr indexer; caches `EntityDetails` and `<s, p, o>` triples during load.
- `oxo2-dataload-testing` — test utilities and fixtures.

Java sub-modules depend on `oxo2-shared` for the SSSOM data model.

## Exposes

- **Two populated Solr collections** — `oxo2-mappings` and `oxo2-mappingsets`. Schemas under `solr-config/`.
- **Container image** — built from `Dockerfile.dataload`; `CMD` invokes `loadData.nextflow`.
- **HPC entry points** — `loadData.hpc`, `loadData.slurm` for SLURM-based deployments.

This module exposes no Java API to other OxO2 modules — its outputs flow downstream via Solr, not via library calls.

## Module notes

### Pipeline stages

Single execution path: `loadData.nextflow` invokes the stages below in order. Per-stage `.sh` scripts (e.g. `downloadMappings.sh`, 
`inferMappings.sh`) exist for local debugging only — they are *not* part of the production pipeline (see [ADR-0003](../docs/adr/0003-nextflow-as-sole-dataload-path.md)).

**1. Download** — `downloadMappings.nf` (calling logic in `oxo2-downloader`). Reads the SSSOM source list from `OXO2_CONFIG` 
(an `oxo-config*.json` file) and downloads each mapping set's TSV to the dataload working directory.

**2. SSSOM → JSON** — `sssom2json.nf` (logic in `oxo2-sssom2json`). Parses each SSSOM TSV into OxO2's JSON representation of 
`MappingSet` and its `Mapping`s, using the types in `oxo2-shared`.

**3. Inference and explanation** — `determineInferencesAndExplanations.nextflow` → `inferAndExplainMappings.nf` (logic in 
`oxo2-json2inferences`). For each mapping set, in sequence per set ([ADR-0001](../docs/adr/0001-inference-scope-per-mapping-set.md)):
   - `json2ttl` — convert per-set JSON to TTL facts.
   - Nemo infer — run `nmo` with `chain-rules.rls` to produce inferred mappings.
   - Split + parallel trace — `splitInferencesToTrace` chunks the facts-to-trace file (`trace_chunk_size` mappings per chunk, 
default 100 000), runs `nmo trace` in parallel across chunks, then `mergeChainFiles.sh` recombines per-chunk chain JSONs into 
one per-set chain file.
   - `explanations2json` — convert Nemo's trace output to OxO2's explanation-chain JSON shape.

**4. Solr load** — `json2solr.sh` (logic in `oxo2-solr-dataload-client`). Indexes mapping-set, mapping, and explanation-chain 
JSON into the two Solr collections. The Solr client caches `EntityDetails` and `<s, p, o>` triples to avoid redundant lookups during load.

### Configuration

- `OXO2_CONFIG` — absolute path to the JSON file listing SSSOM source URLs. Several variants live at the repo root (`oxo-config.json`, 
`oxo-config-evora.json`, `oxo-config-stress-test*.json`).
- `OXO2_DATA` — working directory for downloads and intermediate artifacts.
- `NEXTFLOW_DIR` — Nextflow workdir.
- `params.trace_chunk_size` (default 100 000) — see `inferAndExplainMappings.nf`.

### Solr config

`solr-config/oxo2-mappings/` and `solr-config/oxo2-mappingsets/` hold the Solr collection configs. `copySolrConfig.sh` deploys 
them to `$SOLR_HOME` for local runs.
