# oxo2-dataload — Module Context

See [`/CONTEXT.md`](../CONTEXT.md) for the project-wide glossary and cross-cutting constraints. This document covers what this 
module specifically owns.

## Purpose

`oxo2-dataload` is the pipeline that turns a configured list of SSSOM mapping-set URLs into populated Solr collections. 
It downloads SSSOM TSVs, converts them to OxO2 JSON, runs the Nemo rules engine to derive inferred mappings and their 
explanation chains, and loads everything into Solr. Orchestration is by Nextflow ([ADR-0003](../docs/adr/0003-nextflow-as-sole-dataload-path.md)).

## Vocabulary introduced here

The cross-cutting terms `inferred mapping`, `chain rule`, `explanation`, `explanation chain`, and `facts to trace`
(defined in `/CONTEXT.md` § Glossary) originate in this module. `inferred mapping` and `chain rule` still describe
live inference-stage artifacts; `explanation`, `explanation chain`, and `facts to trace` are **dormant** — the
dataload no longer computes explanations ([ADR-0020](../docs/adr/0020-defer-explanations-to-on-demand.md)), which
defers them to a future on-demand service.

Module-local artifact names worth knowing:

- **Per-set N-Quads fact file** — `<s> <p> <o> <urn:uuid:mapping_id> .` quads generated from a mapping set's JSON,
  fed to Nemo as input. The `mapping_id` graph term carries source-mapping provenance through Nemo
  ([ADR-0010](../docs/adr/0010-carry-mapping-provenance-via-nquads.md)). Produced by `json2nquadsNextflow.sh`.
- **Cross-set corpus** — the concatenation of every set's N-Quads into one file (`assertedCorpus.nq`); the input
  to SSSOM cross-set reasoning. Produced by `inferSssomCrossSet.nf`. Also the corpus the future on-demand
  explanation service reasons over ([ADR-0020](../docs/adr/0020-defer-explanations-to-on-demand.md)).
- **Inferred mappings TTL** — `inferences.ttl`, the Turtle export of `INFER_CROSS_SET`: exactly the inferred
  mappings (asserted echoes already excluded by the `~assertedTriple` rule). The bare inferred-mapping indexer
  reads it directly; no trace is needed for the *what*.
- **Trace chunk** / **Chain file** — _dormant_ ([ADR-0020](../docs/adr/0020-defer-explanations-to-on-demand.md)):
  the chunked `nmo trace` and per-chunk explanation-chain JSON only existed to precompute explanations, which the
  dataload no longer does.

## Depends on

External:
- **Nextflow** — workflow orchestration ([ADR-0003](../docs/adr/0003-nextflow-as-sole-dataload-path.md)).
- **Nemo (`nmo` CLI)** — rules engine; used for both inference and trace stages.
- **Apache Solr** — destination data store ([ADR-0002](../docs/adr/0002-solr-as-sole-data-store.md)).

Internal (sub-modules):
- `oxo2-downloader` — fetches SSSOM TSVs from the URLs listed in `OXO2_CONFIG`.
- `oxo2-sssom2json` — TSV → JSON conversion.
- `oxo2-json2inferences` — JSON → N-Quads → Nemo infer → trace → explanations JSON. Contains the SSSOM ruleset `sssom.rls`, applied across all mapping sets per [ADR-0016](../docs/adr/0016-single-pass-sssom-reasoning.md).
- `oxo2-solr-dataload-client` — Solr indexer; caches `EntityDetails` and `<s, p, o>` triples during load.
- `oxo2-dataload-testing` — test utilities and fixtures.

Java sub-modules depend on `oxo2-shared` for the SSSOM data model.

## Exposes

- **Two populated Solr collections** — `oxo2-mappings` and `oxo2-mappingsets`. Schemas under `solr-config/`.
- **Container image** — built from `Dockerfile.dataload`; `CMD` invokes `loadData.nextflow`.
- **HPC entry points** — `loadData.hpc` (login node: validate + `sbatch`) and `loadData.slurm` (the
  single batch job) for SLURM-based deployments. Both honour a `START_STAGE` resume parameter, and
  `loadData.jenkins.sh` (the login-node submit→poll→tail wrapper a Jenkins Freestyle job runs over
  SSH) wraps them for CI-driven runs — see § Resumable dataload. The `START_STAGE` contract they share
  with the local `loadData.nextflow` lives in the sourced `loadData.lib.sh`.
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
`loadData.nextflow` is itself resumable from a chosen `START_STAGE` (default `download` = full run), sharing the same contract as the
HPC path — see § Resumable dataload.

**1. Download** — `downloadMappings.nf` (calling logic in `oxo2-downloader`). Reads the SSSOM source list from `OXO2_CONFIG` 
(an `oxo-config*.json` file) and downloads each mapping set's TSV to the dataload working directory. A registry may be a direct
`url`, an `ftp_server`, a `github_repository`, or a `mapping_commons_registry`:
- `github_repository` registries are fetched as the default-branch archive tarball and only the configured `directory` is
  extracted — no GitHub API, see [ADR-0007](../docs/adr/0007-github-registries-via-archive-tarball.md).
- `mapping_commons_registry` registries point at a Mapping Commons aggregated catalogue
  (`mapping-commons.github.io/data/mapping-specifications.json`): the downloader reads the JSON, keeps the `type=sssom` entries
  (dropping the FAIR-transform registry and any `exclude`d basenames), and downloads each `content_url` into a per-source-registry
  subdirectory, gunzipping `*.gz` to `.tsv`. Distinct sets that share a filename within a registry (e.g. the five biopragmatics
  SeMRA-landscape `priority` views) are all kept and namespaced by landscape name — looked up best-effort from the source
  `registry.yml`, falling back to the Zenodo record id; only exact-duplicate URLs collapse. See
  [ADR-0014](../docs/adr/0014-mapping-commons-registry-via-specifications-json.md).

**2. SSSOM → JSON** — `sssom2json.nf` (logic in `oxo2-sssom2json`). Parses each SSSOM TSV into OxO2's JSON representation of 
`MappingSet` and its `Mapping`s, using the types in `oxo2-shared`. Two robustness behaviours
([ADR-0015](../docs/adr/0015-default-prefix-map-and-metadata-synthesis-for-bare-sssom.md)):
- **Bare sets.** A TSV with no embedded YAML header and no external `.yml` (e.g. the biopragmatics SeMRA landscape
  `priority` views) is not dropped: `TSV2JSON` synthesises the `MappingSet` from the first row's set-level columns
  (`mapping_set_id`/`mapping_set_title`/`license`) and applies the bundled Bioregistry prefix map (`oxo2-shared`'s
  `BioregistryPrefixMap`) as the fallback `curie_map` so the row CURIEs still expand to IRIs.
- **Output naming.** Each output JSON is named by the input's path *relative to the sssom root*, flattened
  (`mapping_commons/mapping-registry/gene/priority.sssom.tsv` → `mapping_commons.mapping-registry.gene.priority.sssom.json`),
  so distinct sets that share a basename across sub-directories (the five landscape `priority.sssom.tsv` files) don't
  collapse at the flat publish dir. Downstream stages treat the stem as an opaque unique key.

**3. Inference** — `determineInferences.nextflow` runs the single SSSOM cross-set pass
([ADR-0016](../docs/adr/0016-single-pass-sssom-reasoning.md)) via `inferSssomCrossSet.nf`: `json2nquads`
converts each set's JSON to N-Quads carrying `mapping_id`
([ADR-0010](../docs/adr/0010-carry-mapping-provenance-via-nquads.md)); every set's N-Quads is concatenated
into one corpus (`assertedCorpus.nq`); `nmo` runs `sssom.rls` (strong-predicate transitivity + role chains)
over the whole corpus to derive mappings that may chain across sets, exported as `inferences.ttl` (the single
`https://www.ebi.ac.uk/oxo2/inferences` set). **No trace/explain/merge** — explanations are deferred to
on-demand ([ADR-0020](../docs/adr/0020-defer-explanations-to-on-demand.md)). A set whose mappings yield no
quads — all using non-inference predicates (e.g. the `ebi-text-mappings` sets are `skos:closeMatch`) or
lacking a subject/object IRI — produces no `.nq` and is logged (it is still indexed as asserted; it just does
not enter the inference corpus).

**4. Solr load** — `json2solr.sh` (logic in `oxo2-solr-dataload-client`) indexes the asserted mapping-set and
mapping JSON first, so the bare inferred-mapping indexer can resolve each inferred subject/object's CURIE and
label from the already-indexed asserted documents (`DataloadSolr.prefetchEntityDetailsByIris`). That indexer
then builds **bare** inferred mappings straight from `inferences.ttl` — subject/object/predicate + ids/labels,
`inference_type = SSSOM_INFERENCE` ([ADR-0011](../docs/adr/0011-inference-type-replaces-is-inferred.md)),
`spo_key`; no explanation chain, asserted evidence, or set-source union, with `distance`/`explanation_length`
left at their inert model defaults ([ADR-0020](../docs/adr/0020-defer-explanations-to-on-demand.md)) — and
indexes them. The Solr client caches `EntityDetails` to avoid redundant queries during load.

### Resumable dataload (local, HPC, Jenkins)

Decision and rationale: [ADR-0019](../docs/adr/0019-resumable-hpc-dataload.md) (HPC) and
[ADR-0022](../docs/adr/0022-resumable-local-dataload-shared-library.md) (the shared library extending it
to local).

Both orchestrators can resume from the last completed (sub)stage instead of re-running from scratch — so
a load that fails late (e.g. a flaky Solr start, or a late stage failing after the expensive inference)
can be restarted at that point. The ordered stage list, `should_run` gating, stage-aware cleanup, the
resume checkpoint, and the Solr wipe/needed decisions live in **one sourced library**,
`loadData.lib.sh`, used by both `loadData.slurm` (HPC) and `loadData.nextflow` (local/integration); a new
stage must be declared there, not in either script. This replaced the old manual
`>>> TEMP run-from-merge <<<` edit of `loadData.slurm` (HPC) and the unconditional `rm -R $OXO2_DATA/*`
full wipe (local).

**Stages.** `loadData.lib.sh` defines one ordered stage list; each orchestrator re-enters it at
`START_STAGE` (default `download` = full run):

```
download → sssom2json → nquads → infer
         → index-asserted → inferences2json → index-inferred → archive
```

`nquads`/`infer` are the substages of the single cross-set inference (ADR-0016); the rest are the
download / Solr-load / archive stages. The `trace`/`explain`/`merge` stages were removed with
explanations and `explanations2json` was replaced by `inferences2json`
([ADR-0020](../docs/adr/0020-defer-explanations-to-on-demand.md)): `inferences2json` builds the
bare inferred-mapping JSON from `inferences.ttl` (querying the asserted Solr index for entity
details), and `index-inferred` posts it to Solr. On HPC, `loadData.hpc` forwards `START_STAGE` to the
batch job via `--export`; locally it is just an environment variable read by `loadData.nextflow`. Either
way `loadData.lib.sh` validates it against the list and computes which stages to run. `loadData.nextflow`
has no body for `archive`, so locally that final stage is a no-op (no `solr-data.tar.gz`).

**How resume stays correct.** Two mechanisms:

- *Substage resume reads PUBLISHED artifacts, not Nextflow's work dir.* `inferSssomCrossSet.nf`
  exposes the `from_infer` `-entry` workflow, which reads the published `assertedCorpus.nq` under
  `$OXO2_DATA` and re-runs `INFER_CROSS_SET`. Both orchestrators select it at `START_STAGE=infer` — HPC
  inline in `loadData.slurm`, locally via `determineInferences.nextflow`. (The
  `from_trace`/`from_explain`/`from_merge` entry points and their `crossSet/chunks/`,
  `crossSet/chunkChains/` published artifacts went away with explanations —
  [ADR-0020](../docs/adr/0020-defer-explanations-to-on-demand.md).) Because resume never depends on
  `NXF_WORK`, both orchestrators wipe the transient Nextflow dirs on every run — no fragile work-dir
  spelunking, and the result is independent of the container digest.
- *Stage-aware cleanup preserves earlier stages' outputs.* Each stage "owns" the artifact path(s) it
  regenerates (`OXO2_STAGE_OWNS` in `loadData.lib.sh`). A resume wipes only the owned paths of
  `START_STAGE` and later, keeping everything earlier stages produced as the resume inputs. Both the
  Solr on-disk index wipe **and** the Solr-config copy (`copySolrConfig.sh`, which deletes and recreates
  the core dirs) are gated on the same `should_wipe_solr` decision — true only when the asserted load
  (`index-asserted`) is in scope — so resuming at `inferences2json` / `index-inferred` / `archive` keeps
  the already-indexed asserted data the bare inferred-mapping indexer queries for entity details. (Gating
  the config copy on anything looser would re-wipe the asserted cores on a resume — a latent bug
  ADR-0022 fixed.) Solr is started only for runs that reach an indexing stage; `START_STAGE=archive` just
  re-archives the existing `$SOLR_HOME` (HPC only).

**Checkpoint.** After each stage, the orchestrator (via `record_stage` in `loadData.lib.sh`) writes the
stage name to `$OXO2_DATA/.oxo2-last-completed-stage`. On failure, set `START_STAGE` to that value to
resume. The checkpoint lives outside every stage's owned paths, so cleanup never removes it.

**Running locally from an arbitrary stage.** `START_STAGE` is an ordinary environment variable read
by `loadData.nextflow`, so resuming locally is just setting it before the invocation (defaults to
`download` = full run). For example, to re-index from scratch after the inference stage already
completed — wiping and rebuilding the Solr index, then continuing through `index-inferred` and
`archive`:

```sh
START_STAGE=index-asserted ./loadData.nextflow
```

The prerequisite artifacts for the chosen stage must already exist under `$OXO2_DATA` from a
previous run (e.g. `index-asserted` reads `sssom-as-json/` and the published `inferences.ttl`).
Because `index-asserted` is in scope here, `should_wipe_solr` is true, so `copySolrConfig.sh`
re-copies the Solr config and the asserted cores are rebuilt; starting at `inferences2json` or later
instead preserves the already-indexed asserted data.

**Jenkins.** The dataload is driven from a **parameterised Freestyle job** (not a `Jenkinsfile`): its
single build step is *Execute shell script on remote host using ssh* (the `ssh` plugin), targeting the
globally-configured **SSH site** for the login node — the same SSH site the `solr-data.tar.gz` copy
job uses. The hostname, HPC user, and key live in that Jenkins-global SSH site, so no credentials are
in the repo. (We use a Freestyle job because that build step has no pipeline DSL, and because
*Publish over SSH* — which a `Jenkinsfile` would have needed — is not installed on the controller and
cannot be added without disruption.)

The build step runs `loadData.jenkins.sh`, which reads everything from environment variables, so the
step body just exports the build parameters and invokes the wrapper on the login node:

```sh
export START_STAGE="$START_STAGE"
export OXO2_CONFIG="$OXO2_CONFIG"
export NF_CONTAINER="$NF_CONTAINER"
export HPC_TIME="$HPC_TIME" HPC_MEM="$HPC_MEM" HPC_CPUS="$HPC_CPUS"
export HPC_ACCOUNT="$HPC_ACCOUNT" POLL_INTERVAL="$POLL_INTERVAL"
bash -l "$OXO2_DATALOAD_DIR/loadData.jenkins.sh"
```

The `$VAR` references are Jenkins build parameters; the `ssh` plugin expands them before sending the
command, so the login node receives literal values. Configure the job with: a `START_STAGE` **choice**
parameter (`download` … `archive`, default `download`); string parameters `OXO2_DATALOAD_DIR` (the
checked-out `oxo2-dataload` dir on the login node), `OXO2_CONFIG`, `NF_CONTAINER`, `HPC_TIME`,
`HPC_MEM`, `HPC_CPUS`, `HPC_ACCOUNT` (blank = none), `POLL_INTERVAL`; and tick **Do not allow
concurrent builds** (so two dataloads never race on the checkpoint/jobid files and `$SOLR_HOME`).

`loadData.jenkins.sh` submits via `loadData.hpc`, then blocks — polling Slurm and streaming the job
log to the build console — and exits with the job's final state, so the build passes iff the dataload
`COMPLETED`. If a previous wrapper's job is still active (id recorded in
`$SLURM_LOGS/oxo2-dataload.current-jobid`), it reattaches and tails that job instead of submitting a
new one, so a dropped Jenkins build is recoverable by re-running the job. The repo is assumed already
checked out on the login node; resume is just re-running the job with a different `START_STAGE`. This
job stops at producing `solr-data.tar.gz` — copying it off-cluster and redeploying Solr to Kubernetes
is a separate Jenkins job.

### Configuration

- `OXO2_CONFIG` — absolute path to the JSON file listing SSSOM source URLs. Several variants live at the repo root (`oxo-config.json`, 
`oxo-config-evora.json`, `oxo-config-stress-test*.json`).
- `OXO2_DATA` — working directory for downloads and intermediate artifacts.
- `NEXTFLOW_DIR` — Nextflow workdir.
- `params.trace_chunk_size` (default 20000) — see `inferSssomCrossSet.nf`.

### Solr config

`solr-config/oxo2-mappings/` and `solr-config/oxo2-mappingsets/` hold the Solr collection configs. `copySolrConfig.sh` deploys 
them to `$SOLR_HOME` for local runs.

> **Reindex required (ADR-0011):** the schemas changed — `is_inferred` (boolean) became `inference_type`
> (string, default `ASSERTED`) on both cores, and `mapping_id` is now `indexed="true"` on `oxo2-mappings`
> (the explanation step looks up asserted premises by it). An existing index must be rebuilt; a normal
> `loadData.nextflow` run does a fresh load and so reindexes automatically.

> **Reindex required (ADR-0013):** `oxo2-mappings` gained a `spo_key` field (the same-SPO grouping key:
> a hash of subject_id + predicate_id + predicate_modifier + object_id). No pipeline code changed — it is
> a derived `Mapping.spoKey()` accessor that every serialised mapping document (asserted and inferred)
> carries automatically, so a normal `loadData.nextflow` run populates it. `stored="false"`, so it is never
> returned in query results.

> **Reindex required (ADR-0024):** `oxo2-mappings` gained `subject_prefix` and `object_prefix`
> (`string`, `indexed`, `docValues`) — the CURIE prefix of `subject_id` / `object_id`, the ontology
> identity that cross-ontology mapping filters and facets on
> ([ADR-0024](../docs/adr/0024-cross-ontology-mapping.md)). Like `spo_key`, they are derived accessors
> every serialised mapping document carries (asserted and inferred), so a normal `loadData.nextflow`
> run populates them; an existing index must be rebuilt. A bare IRI that never resolved to a CURIE has
> no prefix and is left empty.

### Input validation

Remote filenames sourced from registries (FTP listings, TAR entries — including the GitHub archive
tarballs fetched per [ADR-0007](../docs/adr/0007-github-registries-via-archive-tarball.md) — and the
filenames/registry-slugs derived from a `mapping_commons_registry` catalogue's `content_url`s per
[ADR-0014](../docs/adr/0014-mapping-commons-registry-via-specifications-json.md)) are untrusted:
they flow into `Paths.get`/`File` on disk and later into Bash interpolation in the Nextflow scripts, so an
unsanitised name enables both path traversal and command injection. All four downloaders validate names
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
