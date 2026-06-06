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

- **Per-set N-Quads fact file** — `<s> <p> <o> <urn:uuid:mapping_id> .` quads generated from a mapping set's JSON,
  fed to Nemo as input. The `mapping_id` graph term carries source-mapping provenance through Nemo
  ([ADR-0010](../docs/adr/0010-carry-mapping-provenance-via-nquads.md)). Produced by `json2nquadsNextflow.sh`.
- **Cross-set corpus** — the concatenation of every set's N-Quads into one file; the input to phase-2 (SSSOM,
  cross-set) reasoning. Produced by `inferSssomCrossSet.nf`.
- **Trace chunk** — a slice of the facts-to-trace file (default `trace_chunk_size = 100 000`), used as the
  parallelism unit for `nmo trace`. See `inferAndExplainMappings.nf` / `inferSssomCrossSet.nf`.
- **Chain file** — per-chunk/per-set (phase 1) or the single cross-set (phase 2) JSON file of explanation chains;
  per-chunk files are merged by `mergeChainFiles.sh`.

## Depends on

External:
- **Nextflow** — workflow orchestration ([ADR-0003](../docs/adr/0003-nextflow-as-sole-dataload-path.md)).
- **Nemo (`nmo` CLI)** — rules engine; used for both inference and trace stages.
- **Apache Solr** — destination data store ([ADR-0002](../docs/adr/0002-solr-as-sole-data-store.md)).

Internal (sub-modules):
- `oxo2-downloader` — fetches SSSOM TSVs from the URLs listed in `OXO2_CONFIG`.
- `oxo2-sssom2json` — TSV → JSON conversion.
- `oxo2-json2inferences` — JSON → N-Quads → Nemo infer → trace → explanations JSON. Contains the two rulesets
  `owl.rls` (phase 1, per-set OWL reasoning) and `sssom.rls` (phase 2, cross-set SSSOM reasoning), split per
  [ADR-0009](../docs/adr/0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md).
- `oxo2-solr-dataload-client` — Solr indexer; caches `EntityDetails` and `<s, p, o>` triples during load.
- `oxo2-dataload-testing` — test utilities and fixtures.

Java sub-modules depend on `oxo2-shared` for the SSSOM data model.

## Exposes

- **Two populated Solr collections** — `oxo2-mappings` and `oxo2-mappingsets`. Schemas under `solr-config/`.
- **Container image** — built from `Dockerfile.dataload`; `CMD` invokes `loadData.nextflow`.
- **HPC entry points** — `loadData.hpc`, `loadData.slurm` for SLURM-based deployments.
- **Solr data archive** — on a successful HPC run, `loadData.slurm` stops Solr cleanly and writes
  `$OXO2_INFERENCES/solr-data.tar.gz` (the contents of `$SOLR_HOME`, excluding the run-local `logs/`
  and `pid/` dirs). Jenkins copies it onto the NFS export, and the dev-cluster Solr init container
  extracts it into `/var/solr` (`k8chart-dev/oxo2/templates/solr-deployment.yaml`). The init
  container is version-gated: it records the tarball's size+mtime in `/var/solr/.import-version` on
  the persistent PVC and re-extracts only when a new tarball differs from that marker. So restarting
  the Solr pod (`kubectl rollout restart deploy/oxo2-solr`) after a dataload refreshes the data iff
  the tarball actually changed; an unchanged tarball is a fast no-op.

This module exposes no Java API to other OxO2 modules — its outputs flow downstream via Solr, not via library calls.

## Module notes

### Pipeline stages

Single execution path: `loadData.nextflow` invokes the stages below in order. Per-stage `.sh` scripts (e.g. `downloadMappings.sh`, 
`inferMappings.sh`) exist for local debugging only — they are *not* part of the production pipeline (see [ADR-0003](../docs/adr/0003-nextflow-as-sole-dataload-path.md)).

**1. Download** — `downloadMappings.nf` (calling logic in `oxo2-downloader`). Reads the SSSOM source list from `OXO2_CONFIG` 
(an `oxo-config*.json` file) and downloads each mapping set's TSV to the dataload working directory. A registry may be a direct
`url`, an `ftp_server`, or a `github_repository`; GitHub registries are fetched as the default-branch archive tarball and only the
configured `directory` is extracted — no GitHub API, see [ADR-0007](../docs/adr/0007-github-registries-via-archive-tarball.md).

**2. SSSOM → JSON** — `sssom2json.nf` (logic in `oxo2-sssom2json`). Parses each SSSOM TSV into OxO2's JSON representation of 
`MappingSet` and its `Mapping`s, using the types in `oxo2-shared`.

**3. Inference (both phases)** — `determineInferencesAndExplanations.nextflow` runs `json2nquads` (per-set JSON →
N-Quads carrying `mapping_id`, [ADR-0010](../docs/adr/0010-carry-mapping-provenance-via-nquads.md)) then two
reasoning phases ([ADR-0009](../docs/adr/0009-two-phase-reasoning-owl-per-set-sssom-cross-set.md)):
   - **Phase 1 — OWL, per set** (`inferAndExplainMappings.nf`): `nmo` runs `owl.rls` over each set's N-Quads to
     derive subsumption (subClassOf/subPropertyOf) within that set → a per-source inferred set.
   - **Phase 2 — SSSOM, cross-set** (`inferSssomCrossSet.nf`): every set's N-Quads is concatenated into one
     corpus over which `nmo` runs `sssom.rls` (strong-predicate transitivity + role chains) to derive mappings
     that may chain across sets → the single `https://www.ebi.ac.uk/oxo2/inferences` set.
   - Both phases split the facts-to-trace file into chunks, run `nmo trace` in parallel, and `mergeChainFiles.sh`
     recombines per-chunk chain JSONs (per-set for phase 1, one file for phase 2).

**4. Solr load + explanation** — `json2solr.sh` (logic in `oxo2-solr-dataload-client`) indexes the asserted
mapping-set and mapping JSON first, because `explanations2json` then queries Solr by `mapping_id` to recover each
asserted premise's source set. `explanations2json` runs once per phase (`--inferenceType OWL_INFERENCE` /
`SSSOM_INFERENCE`, [ADR-0011](../docs/adr/0011-inference-type-replaces-is-inferred.md)), converting Nemo's trace
output to OxO2 explanation-chain JSON; the inferred mappings/sets are then indexed. The Solr client caches
`EntityDetails` and by-id mapping lookups to avoid redundant queries during load.

### Configuration

- `OXO2_CONFIG` — absolute path to the JSON file listing SSSOM source URLs. Several variants live at the repo root (`oxo-config.json`, 
`oxo-config-evora.json`, `oxo-config-stress-test*.json`).
- `OXO2_DATA` — working directory for downloads and intermediate artifacts.
- `NEXTFLOW_DIR` — Nextflow workdir.
- `params.trace_chunk_size` (default 100 000) — see `inferAndExplainMappings.nf`.

### Solr config

`solr-config/oxo2-mappings/` and `solr-config/oxo2-mappingsets/` hold the Solr collection configs. `copySolrConfig.sh` deploys 
them to `$SOLR_HOME` for local runs.

> **Reindex required (ADR-0011):** the schemas changed — `is_inferred` (boolean) became `inference_type`
> (string, default `ASSERTED`) on both cores, and `mapping_id` is now `indexed="true"` on `oxo2-mappings`
> (the explanation step looks up asserted premises by it). An existing index must be rebuilt; a normal
> `loadData.nextflow` run does a fresh load and so reindexes automatically.

### Input validation

Remote filenames sourced from registries (FTP listings, and TAR entries — including the GitHub archive
tarballs fetched per [ADR-0007](../docs/adr/0007-github-registries-via-archive-tarball.md)) are untrusted:
they flow into `Paths.get`/`File` on disk and later into Bash interpolation in the Nextflow scripts, so an
unsanitised name enables both path traversal and command injection. All three downloaders validate names
against the allowlist in
[`SafeFilename`](oxo2-downloader/src/main/java/uk/ac/ebi/spot/oxo/downloader/util/SafeFilename.java)
(`[A-Za-z0-9._-]+`, no leading `.` or `-`, no `.`/`..`, max 255 bytes) and skip+log offending files. The
GitHub and HTTP downloaders share the tar-extraction guard (per-segment `SafeFilename` check plus a
canonical-path containment check) in
[`TgzExtractor`](oxo2-downloader/src/main/java/uk/ac/ebi/spot/oxo/downloader/util/TgzExtractor.java). Every
`.nf` script re-asserts the same rule on `baseName` at the `channel.fromPath` entry point as defense-in-depth
against files dropped into `OXO2_DATA/` outside the downloader path, via the shared Groovy class
[`FilenameGuard`](lib/FilenameGuard.groovy) in `oxo2-dataload/lib/` (Nextflow auto-loads this for all scripts
run via `loadData.nextflow`; `oxo2-json2inferences/lib` is a symlink to `../lib` for standalone debug runs).
Keep `FilenameGuard`'s regex in sync with `SafeFilename.PATTERN`.
