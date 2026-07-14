# oxo2-dataload — Module Context

See [`/CONTEXT.md`](../CONTEXT.md) for the project-wide glossary and cross-cutting constraints. This document covers what this 
module specifically owns.

## Purpose

`oxo2-dataload` is the pipeline that turns a configured list of SSSOM mapping-set URLs into populated Solr collections. 
It downloads SSSOM TSVs, converts them to OxO2 JSON, runs the Nemo rules engine to derive inferred mappings, traces each
one's explanation chain against its explanation shard, and loads everything into Solr. Orchestration is by Nextflow ([ADR-0003](../docs/adr/0003-nextflow-as-sole-dataload-path.md)).

## Vocabulary introduced here

The cross-cutting terms `inferred mapping`, `chain rule`, `explanation`, `explanation chain`, `explanation shard`,
and `facts to trace` (defined in `/CONTEXT.md` § Glossary) originate in this module. All are live again: the dataload
precomputes every inferred mapping's explanation by component-sharded chase+trace
([ADR-0028](../docs/adr/0028-component-sharded-explanation-precompute.md), superseding ADR-0020).

Module-local artifact names worth knowing:

- **Per-set N-Quads fact file** — `<s> <p> <o> <urn:uuid:mapping_id> .` quads generated from a mapping set's JSON,
  fed to Nemo as input. The `mapping_id` graph term carries source-mapping provenance through Nemo
  ([ADR-0010](../docs/adr/0010-carry-mapping-provenance-via-nquads.md)). Produced by `json2nquadsNextflow.sh`.
- **Cross-set corpus** — the concatenation of every set's N-Quads into one file (`assertedCorpus.nq`); the input
  to SSSOM cross-set reasoning, and the corpus `shardConclusions` partitions into explanation shards. Produced by
  `inferSssomCrossSet.nf`.
- **Inferred mappings TTL** — `inferences.ttl`, the Turtle export of `INFER_CROSS_SET`: exactly the inferred
  mappings (asserted echoes already excluded by the `~assertedTriple` rule). It supplies the *what*; `shardConclusions`
  turns each of its non-self triples into a trace target so the `explain` stage can supply the *why*.
- **Explanation shard** — `crossSet/shards/shardNNNNN.nq` plus its `shardNNNNN-targets.txt`: one connected component
  of the corpus's strong-predicate edges, with every asserted quad whose subject and object are both inside it, and the
  conclusions it owns. Chased and traced independently ([ADR-0028](../docs/adr/0028-component-sharded-explanation-precompute.md)).
- **Chain file** — `crossSet/shardChains/shardNNNNN-chains.json`, one shard's `nmo --trace-output`: the derivation DAG
  of every conclusion that shard owns. `explanations2json` interprets bundles of these into inferred-mapping JSON.

## Depends on

External:
- **Nextflow** — workflow orchestration ([ADR-0003](../docs/adr/0003-nextflow-as-sole-dataload-path.md)).
- **Nemo (`nmo` CLI)** — rules engine; used for both inference and trace stages.
- **Apache Solr** — destination data store ([ADR-0002](../docs/adr/0002-solr-as-sole-data-store.md)).

Internal (sub-modules):
- `oxo2-downloader` — fetches SSSOM TSVs from the URLs listed in `OXO2_CONFIG`.
- `oxo2-sssom2json` — TSV → JSON conversion.
- `oxo2-json2inferences` — JSON → N-Quads → Nemo infer → shard → per-shard trace → explanations JSON. Contains the SSSOM ruleset `sssom.rls`, applied across all mapping sets per [ADR-0016](../docs/adr/0016-single-pass-sssom-reasoning.md), and `OXOInferenceConstants.STRONG_PREDICATES`, which must list every predicate appearing in a `sssom.rls` rule body.
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

This is also where the **mapping set category** is stamped
([ADR-0027](../docs/adr/0027-config-driven-mapping-set-category.md)). The category — `ONTOLOGY`
or `CURATED` — is not in the SSSOM data; it is the `category` key on the config's
`mapping_registries` entry. Since the downloader writes each registry into
`<sssom root>/<registry id>/`, `sssom2json.nf` reads `$OXO2_CONFIG` (via
`lib/MappingSetCategories.groovy`), resolves each TSV's registry from its first relative path
segment, and passes `-c <category>` to the JAR, which denormalises it onto the set and every one of
its mappings. An untagged registry — or an unreadable `$OXO2_CONFIG` — is `CURATED`; an unrecognised
category fails the run. Inferred mappings get no category: an inference chains premises from several
sets. The JAR's whole-tree `-i` mode applies one category to everything it finds, so it is only
correct for a single-registry tree.

**3. Inference** — `determineInferences.nextflow` runs the single SSSOM cross-set pass
([ADR-0016](../docs/adr/0016-single-pass-sssom-reasoning.md)) via `inferSssomCrossSet.nf`: `json2nquads`
converts each set's JSON to N-Quads carrying `mapping_id`
([ADR-0010](../docs/adr/0010-carry-mapping-provenance-via-nquads.md)); every set's N-Quads is concatenated
into one corpus (`assertedCorpus.nq`); `nmo` runs `sssom.rls` (strong-predicate transitivity + role chains)
over the whole corpus to derive mappings that may chain across sets, exported as `inferences.ttl` (the single
`https://www.ebi.ac.uk/oxo2/inferences` set). Every derivation rule guards its own head with
`~assertedTriple(s, p, o)` so the chase never derives a nil-UUID copy of an already-asserted triple — the
invariant that keeps explanations well-founded once the `mapping_id` is projected away
([ADR-0033](../docs/adr/0033-well-founded-explanations.md)); `AssertedTripleGuardTest` fails the build if a
rule loses its guard. A set whose mappings yield no
quads — all using non-inference predicates (e.g. the `ebi-text-mappings` sets are `skos:closeMatch`) or
lacking a subject/object IRI — produces no `.nq` and is logged (it is still indexed as asserted; it just does
not enter the inference corpus).

**4. Explanation** — `determineExplanations.nextflow` runs `explainSssomCrossSet.nf`
([ADR-0028](../docs/adr/0028-component-sharded-explanation-precompute.md)). `SHARD_CONCLUSIONS` union-finds
`assertedCorpus.nq` over the strong predicates, packs whole components into shards capped by **entity count**
(`--maxShardEntities`, default 1200 — per-trace cost is linear in a shard's dictionary size, not its fact count),
routes each asserted quad to the shard holding both endpoints, and writes each shard's conclusions from
`inferences.ttl` as a semicolon-separated trace-target file (self-mappings skipped, since the indexer drops them).
`EXPLAIN_SHARD` then runs one `nmo` per shard: chase its tiny corpus once, then trace every conclusion it owns.
Needs no Solr, so it precedes the asserted load. On the dev corpus: 3,607 shards, 29 s to shard, 6.35 CPU-h to
trace, 20 GB of chain JSON.

Both orchestrators assert `#chain files == #shards` afterwards — Nextflow exits 0 when a *workflow operator* (as
opposed to a task) throws, so a silently empty explain stage would otherwise index every inferred mapping unexplained.

**5. Solr load** — `json2solr.sh` (logic in `oxo2-solr-dataload-client`) indexes the asserted mapping-set and
mapping JSON first, so `explanations2json` can resolve each inferred subject/object's CURIE and label — and every
asserted premise of every chain — from the already-indexed asserted documents (`DataloadSolr.prefetchMappingsByIds`).
`EXPLANATIONS_TO_JSON` then interprets each *bundle* of shard chain files (default 100 shards per JVM, so process
startup and the Solr connection amortise) into inferred mappings carrying subject/object/predicate + ids/labels,
`inference_type = SSSOM_INFERENCE` ([ADR-0011](../docs/adr/0011-inference-type-replaces-is-inferred.md)), `spo_key`,
the `explanation` chain, its `asserted_mappings` evidence, `explanation_length`, and `distance` —
the mapping's ontology span (distinct CURIE prefixes across the explanation DAG minus one, floored at 1;
[ADR-0031](../docs/adr/0031-inferred-mapping-distance-as-ontology-span.md)).
Each bundle's inferred `MappingSet` carries only its own shards' contributing sources, so
`MERGE_INFERRED_MAPPING_SETS` unions them into the one cross-set set. The Solr client caches `EntityDetails` to avoid
redundant queries during load.

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
download → sssom2json → nquads → infer → shard → explain
         → index-asserted → explanations2json → index-inferred → archive
```

`nquads`/`infer` are the substages of the single cross-set inference (ADR-0016), and `shard`/`explain`
the substages of the component-sharded chase+trace
([ADR-0028](../docs/adr/0028-component-sharded-explanation-precompute.md)); the rest are the
download / Solr-load / archive stages. `explanations2json` interprets the shard chain files into
inferred-mapping JSON (querying the asserted Solr index for entity details and asserted premises),
and `index-inferred` posts it to Solr. On HPC, `loadData.hpc` forwards `START_STAGE` to the
batch job via `--export`; locally it is just an environment variable read by `loadData.nextflow`. Either
way `loadData.lib.sh` validates it against the list and computes which stages to run. `loadData.nextflow`
has no body for `archive`, so locally that final stage is a no-op (no `solr-data.tar.gz`).

**How resume stays correct.** Two mechanisms:

- *Substage resume reads PUBLISHED artifacts, not Nextflow's work dir.* `inferSssomCrossSet.nf`
  exposes the `from_infer` `-entry` workflow, which reads the published `assertedCorpus.nq` under
  `$OXO2_DATA` and re-runs `INFER_CROSS_SET`. Likewise `explainSssomCrossSet.nf` exposes
  `from_explain_shard`, which reads the published `crossSet/shards/` and re-runs `EXPLAIN_SHARD`
  without re-sharding the corpus. Both orchestrators select them at `START_STAGE=infer` / `=explain` —
  HPC inline in `loadData.slurm`, locally via `determineInferences.nextflow` /
  `determineExplanations.nextflow`. Because resume never depends on
  `NXF_WORK`, both orchestrators wipe the transient Nextflow dirs on every run — no fragile work-dir
  spelunking, and the result is independent of the container digest.
- *Stage-aware cleanup preserves earlier stages' outputs.* Each stage "owns" the artifact path(s) it
  regenerates (`OXO2_STAGE_OWNS` in `loadData.lib.sh`). A resume wipes only the owned paths of
  `START_STAGE` and later, keeping everything earlier stages produced as the resume inputs. Both the
  Solr on-disk index wipe **and** the Solr-config copy (`copySolrConfig.sh`, which deletes and recreates
  the core dirs) are gated on the same `should_wipe_solr` decision — true only when the asserted load
  (`index-asserted`) is in scope — so resuming at `explanations2json` / `index-inferred` / `archive` keeps
  the already-indexed asserted data `explanations2json` queries for entity details. (Gating
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
re-copies the Solr config and the asserted cores are rebuilt; starting at `explanations2json` or later
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
- `params.max_shard_entities` (default 1200) — cap on an explanation shard's entity count; bounds
  per-trace cost. See `explainSssomCrossSet.nf`.
- `params.explain_bundle_size` (default 100) — shards per `EXPLANATIONS_TO_JSON` JVM. See
  `explanations2json.nf`.

### Solr config

`solr-config/oxo2-mappings/` and `solr-config/oxo2-mappingsets/` hold the Solr collection configs. `copySolrConfig.sh` deploys 
them to `$SOLR_HOME` for local runs.

The Solr query/index URL is `$SOLR_URL` (default `http://localhost:8983/solr`), threaded into every
indexing step **and** into `explanations2json.nf` via `--solr_url` (the inferred-entity CURIE/label
lookups and asserted-premise lookups query the asserted index, so they must hit the same Solr the run
indexed into — a non-default port would otherwise silently fall back to 8983).

`OXO2_SOLR_UNMANAGED` (default `false`): when `true`, the caller owns the Solr process and the
collection wipe, so `loadData.nextflow` skips `copySolrConfig.sh` / `solr start` / `solr stop` and only
indexes into the already-running, already-cleared collections (it still runs the readiness probes).
Set by the integration-test harness, which runs one Solr for the whole suite and clears collections
between fixtures with a `delete *:*` (see `oxo2-integration-tests/CONTEXT.md` § Solr lifecycle).

> **Reindex required (ADR-0011):** the schemas changed — `is_inferred` (boolean) became `inference_type`
> (string, default `ASSERTED`) on both cores, and `mapping_id` is now `indexed="true"` on `oxo2-mappings`
> (the explanation step looks up asserted premises by it). An existing index must be rebuilt; a normal
> `loadData.nextflow` run does a fresh load and so reindexes automatically.

> **Reindex required (ADR-0013):** `oxo2-mappings` gained a `spo_key` field (the same-SPO grouping key:
> a hash of subject_id + predicate_id + predicate_modifier + object_id). No pipeline code changed — it is
> a derived `Mapping.spoKey()` accessor that every serialised mapping document (asserted and inferred)
> carries automatically, so a normal `loadData.nextflow` run populates it. `stored="false"`, so it is never
> returned in query results.

> **Reindex required (ADR-0028):** explanations are precomputed again, so every inferred mapping doc
> now carries `explanation`, `asserted_mappings` and a computed `explanation_length`. `asserted_mappings`
> also changes to `indexed="false"` (it is retrieve-only, like `explanation`): nothing queries, facets or
> sorts on it, and inverting a ~5.7 kB JSON blob per inferred mapping would cost a large index for no
> query. A normal `loadData.nextflow` run does a fresh load and so reindexes automatically.

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
