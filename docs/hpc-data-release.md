# OxO2 HPC Data Release Pipeline

This document describes the end-to-end process for running an OxO2 data release on the EBI HPC
(SLURM) cluster. The pipeline downloads SSSOM ontology mappings, converts them to JSON, runs
cross-set SSSOM inference via the Nemo rules engine, precomputes every inferred mapping's
explanation by component-sharded chase+trace
([ADR-0028](adr/0028-component-sharded-explanation-precompute.md)), indexes the asserted and
explained inferred mappings into Apache Solr, and derives the per-entity typeahead collection. The
same pipeline serves two environments — `dev` and `prod` — selected by `OXO2_ENV` (see
§ Environments and [ADR-0050](adr/0050-production-data-release-channel.md)).

## Architecture Overview

The HPC data release uses a **three-layer execution model**:

1. **Login node** (`loadData.hpc`) -- validates the environment and submits a SLURM batch job. No computation happens here.
2. **Compute node** (`loadData.slurm`) -- the main orchestrator allocated by SLURM. It initializes directories, manages Solr via Singularity, and launches Nextflow pipelines.
3. **Nextflow + SLURM sub-jobs** -- Nextflow submits individual tasks as SLURM jobs, running them
   inside Singularity containers pulled from `ghcr.io/ebispot/oxo2-nextflow` at the environment's
   tag (`:dev` or `:stable` — see § Environments).

```
 Login Node                    Compute Node (SLURM)              SLURM Sub-jobs
 ──────────                    ────────────────────              ───────────────
 loadData.hpc                  loadData.slurm                   Nextflow tasks
   │                             │                                │
   ├─ Validate env vars          ├─ Create directories            ├─ DOWNLOAD_REGISTRY (x N)
   ├─ Check container digest      ├─ Pull Singularity image        ├─ SSSOM2JSON (x 1)
   └─ sbatch ───────────────────>├─ Copy Solr config              ├─ JSON2NQUADS (x M)
                                 ├─ Start Solr (Singularity)      ├─ CONCAT_CORPUS (x 1)
                                 ├─ nextflow run (download)       ├─ INFER_CROSS_SET (x 1)
                                 ├─ nextflow run (sssom2json)     ├─ SHARD_CONCLUSIONS (x 1)
                                 ├─ nextflow run (infer)─────────>├─ EXPLAIN_SHARD (x S)
                                 ├─ nextflow run (explain)        ├─ EXPLANATIONS_TO_JSON (x S/100)
                                 ├─ json2solr (index-asserted)    ├─ MERGE_INFERRED_MAPPING_SETS (x 1)
                                 ├─ nextflow run (explanations2json)
                                 ├─ json2solr (index-inferred)    └─ ENTITIES_FOR_PREFIX (x P)
                                 ├─ nextflow run (mappings2entities)
                                 ├─ json2solr (index-entities)
                                 ├─ Stop Solr
                                 └─ archive (solr-data.tar.gz)
```

The orchestrator's stages are resumable via `START_STAGE`
([ADR-0019](adr/0019-resumable-hpc-dataload.md)): `download`, `sssom2json`, `nquads`, `infer`,
`shard`, `explain`, `index-asserted`, `explanations2json`, `index-inferred`, `mappings2entities`,
`index-entities`, `archive`.

## Prerequisites

### Environments

`OXO2_ENV` selects which environment a run targets. The shared `loadData.env.sh` — sourced by
`loadData.hpc`, `loadData.jenkins.sh`, and `cleanup.hpc` — derives every path, the config, and the
container image from it ([ADR-0050](adr/0050-production-data-release-channel.md)). The value is
whitelisted: anything other than `dev` or `prod` fails before any directory is created or deleted.

| | `dev` (default) | `prod` |
|---|---|---|
| Branch of the NFS checkout | `dev` | `stable` |
| Image tag | `:dev` | `:stable` |
| NFS tree | `/nfs/production/parkinso/spot/oxo2/dev` | `/nfs/production/parkinso/spot/oxo2/prod` |
| HPS tree | `/hps/nobackup/parkinso/spot/oxo2/dev` | `/hps/nobackup/parkinso/spot/oxo2/prod` |

The NFS tree holds the logs, the image digest file, and the repo checkout (`<NFS tree>/oxo2` —
whose `oxo-config.json` is the environment's config); the HPS tree holds the pipeline data,
Nextflow dirs, and Solr home. The prod HPS tree is created by the scripts on first run. Dev and
prod runs may execute concurrently — the trees are disjoint — but if SLURM places both main jobs
on one compute node, the second Solr cannot bind :8983 and that run aborts at the readiness probe;
resume it with `START_STAGE` once the node frees.

### Environment Variables

All variables are derived per environment by `loadData.env.sh`; an explicitly exported variable
overrides its derived default (`OXO2_ENV` picks the set, a named variable overrides a member):

| Variable | Default                                      | Description |
|----------|----------------------------------------------|-------------|
| `OXO2_ENV` | `dev`                                      | Target environment (`dev` or `prod`) — selects every derived default below |
| `OXO2_DATA` | `<HPS tree>/data`                         | Root directory for all pipeline data |
| `OXO2_CONFIG` | `<NFS tree>/oxo2/oxo-config.json`       | JSON file listing mapping registries to download |
| `NEXTFLOW_DIR` | `<HPS tree>/nextflow`                  | Nextflow working directories and caches |
| `SOLR_HOME` | `<HPS tree>/solr-data`                    | Solr index data (persists between runs) |
| `NF_CONTAINER` | `docker://ghcr.io/ebispot/oxo2-nextflow:<tag>` | Container image URI (`:dev` / `:stable`) |
| `HPC_TIME` | `72:00:00`                                   | SLURM time limit for the main job |
| `HPC_MEM` | `32G`                                        | SLURM memory for the main orchestrator job |
| `HPC_CPUS` | `8`                                          | CPUs for the main job (Solr + Nextflow driver + inline stages + pigz) |
| `SOLR_HEAP` | `4g`                                        | Heap for the Solr instance the batch step hosts |

### Storage Layout

The pipeline uses two filesystem tiers:

- **NFS** (`/nfs/...`) -- persistent, shared, visible from login and compute nodes. Stores config, logs, container digests, and the source code.
- **HPS** (`/hps/...`) -- high-performance scratch. Stores pipeline data, Solr indices, and Nextflow working directories. Not backed up.

### Required Software

- SLURM (job scheduler)
- Singularity (container runtime, available on compute nodes)
- Nextflow (loaded via `module load nextflow`)
- `curl` and `jq` on the login node (for container digest check)

## Step-by-Step Pipeline Execution

### Step 0: Launch from Login Node (`loadData.hpc`)

```bash
./loadData.hpc                  # dev (the default)
OXO2_ENV=prod ./loadData.hpc    # production
```

What happens:
1. Sources `loadData.env.sh`, which validates `OXO2_ENV` and derives the environment's paths,
   config, and container image (explicit exports override the derived defaults).
2. Queries the GHCR registry for the configured image's latest digest (repository and tag are
   parsed out of `NF_CONTAINER`). This runs on the login node because compute nodes typically lack
   internet access.
3. Builds `sbatch` arguments — including a per-environment job name, `oxo2-dataload-<env>` —
   forwarding all environment variables (including the remote digest) to the compute node.
4. Submits `loadData.slurm` as a SLURM batch job.

### Step 1: Compute Node Initialization (`loadData.slurm`)

Once SLURM allocates a compute node, `loadData.slurm` runs with `set -euo pipefail` (fail-fast on any error).

**Directory setup:** Creates and cleans the following directory tree:

```
$NEXTFLOW_DIR/
  NXF_WORK/              # Nextflow task working directories
  NXF_HOME/              # Nextflow home
  NXF_TEMP/              # Temporary files
  NXF_CACHE_DIR/         # Nextflow cache
  NXF_SINGULARITY_CACHEDIR/   # Singularity image cache (NOT cleaned between runs)
  logs/                  # Nextflow reports and logs

$OXO2_DATA/
  sssom/                 # Downloaded SSSOM TSV files
  sssom-as-json/         # Converted JSON (mapping/ and mappingSet/ subdirs)
  assertedMappings/      # Per-set N-Quads facts
  entities/              # Per-entity JSON derived from the mappings index (ADR-0034)
  tmp/                   # Temporary files
  inferences/
    crossSet/            # Concatenated corpus, Nemo inference output, shards + shard chains
    solr/                # Explained inferred-mapping JSON for Solr (mapping/ and mappingSet/)

$SOLR_HOME/
  oxo2-mappings/         # Solr core: individual mappings
  oxo2-mappingsets/      # Solr core: mapping sets
  oxo2-entities/         # Solr core: distinct entities for the typeahead (ADR-0034)
  logs/                  # Solr server logs
  pid/                   # Solr PID file
```

All directories except `NXF_SINGULARITY_CACHEDIR` are cleaned at the start of a full run.

**Singularity image management:** The image is only re-pulled when:
- It doesn't exist locally, OR
- The remote GHCR digest differs from the locally cached digest

The digest is tracked in `$NFS_PATH/oxo2-nextflow.digest`.

**Solr startup:** Solr runs in the foreground inside a plain `singularity exec`, backgrounded by
the batch-step shell, bound to `$SOLR_HOME` for data persistence and with `SOLR_HEAP` (default 4g)
set explicitly — the launcher's fixed 512m default cannot index the corpus:

```bash
singularity exec \
    --bind "$SOLR_HOME:/opt/solr/server/solr" \
    --bind "$SOLR_HOME/logs:/opt/solr/server/logs" \
    --env "SOLR_JETTY_HOST=0.0.0.0" \
    --env "SOLR_HEAP=${SOLR_HEAP:-4g}" \
    "$SIF_IMAGE" \
    /opt/solr/bin/solr start -f --user-managed &
```

This keeps Solr's JVM inside the SLURM cgroup and visible to its process tracking; an EXIT trap
tears it down on any failure. (The earlier `singularity instance start` pattern was abandoned —
the detached sinit was being SIGTERM'd silently on this HPC.) Jetty binds 0.0.0.0 so Nextflow
sub-jobs on other nodes can reach Solr via `http://<compute-node-hostname>:8983/solr`; readiness
is probed via localhost, the FQDN, and each core before any stage runs.

### Step 2: Stage 1 -- Download Mappings (`downloadMappings.nf`)

**Parallelism:** One SLURM sub-job per mapping registry.

The Nextflow workflow parses `$OXO2_CONFIG` (a JSON file listing mapping registries) and creates a channel with one entry per registry. Each `DOWNLOAD_REGISTRY` process runs the `oxo2-downloader` JAR to fetch SSSOM files from URLs or GitHub repositories.

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | 4 GB |
| Time | 2 hours |

**Input:** `$OXO2_CONFIG` (e.g., `oxo-config-evora.json`)  
**Output:** `$OXO2_DATA/sssom/*.tsv`

### Step 3: Stage 2 -- SSSOM to JSON (`sssom2json.nf`)

**Parallelism:** Single batch operation (NOT per-file).

This is intentionally run as a single process because multiple SSSOM TSV files across different registries can produce the same output filename (derived from `mappingSetId`). Directory-mode processing lets the converter's `getUniqueFilename()` logic handle collisions safely.

| Resource | Value   |
|----------|---------|
| CPU | 1       |
| Memory | 4 GB    |
| Time | 2 hours |

**Input:** `$OXO2_DATA/sssom/` (all TSV files)  
**Output:** `$OXO2_DATA/sssom-as-json/mapping/*.json` and `$OXO2_DATA/sssom-as-json/mappingSet/*.json`

### Step 4: Stage 3 -- Infer Mappings (SSSOM, cross-set) (`inferSssomCrossSet.nf`)

This is the most resource-intensive stage. SSSOM reasoning (ADR-0016) runs **once across all
mapping sets**: each set's JSON is converted to N-Quads, every set's N-Quads is concatenated into one
corpus, and `nmo` runs `sssom.rls` over the whole corpus to produce the inferred mappings. No trace
or explanation is computed in this stage — the `shard`/`explain` stages trace every conclusion
afterwards (ADR-0028).

```
*.json ─> JSON2NQUADS ─> CONCAT_CORPUS ─> INFER_CROSS_SET
 (per file)   (per file)      (x 1)           (x 1)
```

`JSON2NQUADS` runs per file; `CONCAT_CORPUS` and `INFER_CROSS_SET` are each a single run over the
concatenated corpus. The output `inferences.ttl` is the inferred mappings themselves — the *what*. The
*why* (each mapping's explanation chain) is traced by the `explain` stage, and both are turned into Solr
JSON by `explanations2json.nf` (Step 7), after the asserted mappings are indexed.

> **Sizing is provisional.** The cross-set processes have not yet run at full scale on HPC; the values below
> are the `nextflow.config` `slurm` estimates and must be recalibrated to ~2x observed peak RSS after the
> first real HPC run.

#### Stage 3a: JSON to N-Quads (`JSON2NQUADS`)

Converts each JSON mapping file to N-Quads (`<s> <p> <o> <urn:uuid:mapping_id> .`, ADR-0010) using the
`oxo2-json2inferences` JAR, carrying source-mapping provenance through Nemo.

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | 4 GB |
| Time | 2 hours |

**Input:** `$OXO2_DATA/sssom-as-json/mapping/*.json`
**Output:** `$OXO2_DATA/assertedMappings/*.nq`

#### Stage 3b: Concatenate Corpus (`CONCAT_CORPUS`)

Concatenates every set's N-Quads into one cross-set corpus, the reasoning input.

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | 4 GB |
| Time | 2 hours |

**Input:** `$OXO2_DATA/assertedMappings/*.nq`
**Output:** `$OXO2_INFERENCES/crossSet/assertedCorpus.nq`

#### Stage 3c: Nemo Inference (`INFER_CROSS_SET`)

Runs the Nemo rules engine (`nmo`) with `sssom.rls` over the whole corpus to infer mappings that chain
across sets. The single heaviest task, as it loads and materialises the all-sets corpus.

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | 24 GB |
| Time | 8 hours |

**Command:**
```bash
nmo sssom.rls \
    --param importfile=<assertedCorpus.nq> \
    --param exportfile=<inferences.ttl> \
    -o -v -D <export_dir>
```

**Input:** `$OXO2_INFERENCES/crossSet/assertedCorpus.nq`
**Output:** `$OXO2_INFERENCES/crossSet/inferences.ttl`

### Step 5: Stage 4 -- Index Asserted Mappings to Solr

Runs `json2solr.sh` inside a Singularity container to POST JSON files to Solr via `curl`:

```bash
# Mappings
json2solr.sh "$OXO2_DATA/sssom-as-json/mapping" http://localhost:8983/solr/oxo2-mappings

# Mapping sets
json2solr.sh "$OXO2_DATA/sssom-as-json/mappingSet" http://localhost:8983/solr/oxo2-mappingsets
```

**Verification:** After indexing, the pipeline queries each Solr core for `numFound` and **fails the entire pipeline** if any core has zero documents.

### Step 6: Stage 5 -- Explanations to JSON (`explanations2json.nf`)

**Parallelism:** One `EXPLANATIONS_TO_JSON` process per *bundle* of 100 explanation shards
(`params.explain_bundle_size`), then a single `MERGE_INFERRED_MAPPING_SETS`.

Interprets the per-shard `nmo` trace files produced by the `explain` stage
([ADR-0028](adr/0028-component-sharded-explanation-precompute.md)) into inferred-mapping JSON: one
document per inferred mapping with subject/predicate/object, CURIE/label, `inference_type`, the
`explanation` chain, its `asserted_mappings` evidence, and `explanation_length`. The process queries the
Solr index (populated in Stage 4) to resolve each inferred entity's CURIE/label and each asserted
premise, which is why it must run after Stage 4.

Each bundle's inferred `MappingSet` carries only its own shards' contributing sources, so
`MERGE_INFERRED_MAPPING_SETS` unions them into the one cross-set `MappingSet`.

Expect ~85 GB of `inferences-explained-*.json` on the current corpus (14.9M inferred mappings at
~5.7 kB each), on top of the 20 GB of shard chain JSON the `explain` stage leaves behind.

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | 16 GB |
| Time | 8 hours |

16 GB is a generous starting tier — the bare build only holds an IRI→CURIE/label entity cache for the
entities the inferred mappings reference, far lighter than the former out-of-core explanation step;
recalibrate to ~2× observed peak RSS after the first real HPC run. The process runs with
`errorStrategy = 'terminate'` (overriding the global `ignore`): there is a single cross-set output, so a
swallowed failure would silently drop **all** inferred mappings.

Environment variables `SOLR_URL`, `no_proxy`, and `JAVA_OPTS` are whitelisted in the Singularity configuration and passed to the container so the Java process can reach Solr on the compute node.

**Input:** `$OXO2_INFERENCES/crossSet/inferences.ttl`  
**Output:** `$OXO2_INFERENCES/solr/mapping/inferences-explained.json` and `$OXO2_INFERENCES/solr/mappingSet/inferences-mappingSet.json`

### Step 7: Stage 6 -- Index Inferred Mappings to Solr

Posts the bare inferred mappings and their inferred mapping set to the same Solr cores as the asserted
data:

```bash
json2solr.sh "$OXO2_INFERENCES/solr/mapping"    http://localhost:8983/solr/oxo2-mappings
json2solr.sh "$OXO2_INFERENCES/solr/mappingSet" http://localhost:8983/solr/oxo2-mappingsets
```

### Step 8: Shutdown and Archive

1. **Stop Solr:** SIGTERM to the backgrounded `singularity exec` (the same idempotent handler the
   EXIT trap uses), so Solr commits and closes the index and the on-disk state in `$SOLR_HOME` is
   durable before it is packed.
2. **Permissions:** `chmod -R 777 "$SOLR_HOME"/*` so downstream services (e.g., Kubernetes pods) can read the Solr data.
3. **Archive:** Packs the contents of `$SOLR_HOME` (excluding the run-local `logs/` and `pid/`)
   into `$OXO2_INFERENCES/solr-data.tar.gz` using the image's `pigz` at `$SLURM_CPUS_PER_TASK`
   threads. This archive is what the separate copy-to-NFS + Kubernetes redeploy job consumes; the
   dataload itself stops here and does not deploy.

## Cutting a Production Release

Production runs the **stable** branch ([ADR-0050](adr/0050-production-data-release-channel.md)):
the prod NFS checkout is of `stable`, and CI publishes the `:stable` image tags on every push to
`stable`. To cut a release:

1. **Open a pull request merging `dev` into `stable`** and merge it. The push to `stable` triggers
   the image builds (`.github/workflows/docker.yml`) — wait for them to publish the `:stable` tags.
2. **Update the prod checkout** on the login node:

   ```bash
   git -C /nfs/production/parkinso/spot/oxo2/prod/oxo2 pull
   ```

3. **Run the prod dataload** — either via the prod Jenkins job (a clone of the dev job whose build
   step exports `OXO2_ENV=prod`; see `oxo2-dataload/CONTEXT.md` § Resumable dataload) or by hand
   from the prod checkout's `oxo2-dataload/`:

   ```bash
   OXO2_ENV=prod ./loadData.hpc
   ```

   The digest probe notices the moved `:stable` tag, so the compute node re-pulls the image.
4. The archive lands at `<prod HPS tree>/data/inferences/solr-data.tar.gz` — the same contract as
   dev. Copying it off-cluster and deploying to the Kubernetes clusters (failover first, then
   prod) is the separate deploy job's concern.

## Resource Summary

| Process | CPU | Memory | Time | Parallelism |
|---------|-----|--------|------|-------------|
| Main SLURM job | 8 | 32 GB | 72h | 1 (orchestrator; hosts Solr + the Nextflow driver) |
| DOWNLOAD_REGISTRY | 1 | 4 GB | 2h | N registries |
| SSSOM2JSON | 1 | 4 GB | 2h | 1 (batch) |
| JSON2NQUADS | 1 | 4 GB | 2h | M files |
| CONCAT_CORPUS | 1 | 4 GB | 2h | 1 |
| INFER_CROSS_SET | 1 | 24 GB | 8h | 1 |
| SHARD_CONCLUSIONS | 1 | 16 GB | 2h | 1 |
| EXPLAIN_SHARD | 1 | 6 GB | 2h | S shards (3,607 on the current corpus) |
| EXPLANATIONS_TO_JSON | 1 | 16 GB | 8h | S/100 bundles |
| MERGE_INFERRED_MAPPING_SETS | 1 | 2 GB | 1h | 1 |

Nextflow concurrency limit: `executor.queueSize = 150` in the `slurm` profile — up to 150 sub-jobs queued/running concurrently. No `submitRateLimit` is set.

## Nextflow Configuration Highlights

Key settings from `nextflow/nextflow.config`:

- **SLURM profile:** `executor.name = 'slurm'`, `queueSize = 150` (no `submitRateLimit` set)
- **Singularity:** `enabled = true`, `autoMounts = true`, whitelists `SOLR_URL,no_proxy,JAVA_OPTS`
- **Error handling:** Default `errorStrategy = 'ignore'` with `maxRetries = 1`; every cross-set explanation process (`SHARD_CONCLUSIONS`, `EXPLAIN_SHARD`, `EXPLANATIONS_TO_JSON`, `MERGE_INFERRED_MAPPING_SETS`) overrides this to `terminate` — fail loud rather than silently drop inferred mappings or their explanations
- **Caching:** `cache = 'lenient'` allows Nextflow to reuse completed tasks on resume
- **Reports:** HTML execution report, timeline, and trace file written to `$NXF_LOGS/`

## Error Handling

- **Shell strict mode:** `set -euo pipefail` in `loadData.slurm` fails the pipeline on any error.
- **Nextflow retries:** Default `errorStrategy = 'ignore'` with `maxRetries = 1`; failing tasks are skipped rather than aborting the workflow (except the explanation processes, which terminate).
- **Explain post-condition:** Nextflow exits 0 when a *workflow operator* (as opposed to a task) throws, so `loadData.slurm` asserts `#chain files == #shards` after the `explain` stage rather than trusting the exit status. A mismatch aborts the run.
- **Solr verification:** Stage 4 explicitly checks document counts and aborts if indexing failed.
- **Empty file handling:** All Nextflow processes remove output files smaller than 1 byte to prevent downstream issues.
- **Heap dumps:** The `shardConclusions` and `explanations2json` processes enable `-XX:+HeapDumpOnOutOfMemoryError` for post-mortem analysis.

## Logging

| Log | Location |
|-----|----------|
| SLURM stdout | `$NFS_PATH/logs/oxo2-dataload-<job_id>.out` |
| SLURM stderr | `$NFS_PATH/logs/oxo2-dataload-<job_id>.err` |
| Nextflow log | `$NXF_LOGS/.nextflow.log` |
| Execution report | `$NXF_LOGS/report.html` |
| Timeline | `$NXF_LOGS/timeline.html` |
| Trace | `$NXF_LOGS/trace.txt` |
| Solr logs | `$SOLR_HOME/logs/` |

## Cleanup

To remove one environment's pipeline data and start fresh:

```bash
./cleanup.hpc                  # dev
OXO2_ENV=prod ./cleanup.hpc    # production
```

This submits a quick SLURM job (`srun`, 1 hour, 8 GB) that deletes that environment's
`$NEXTFLOW_DIR`, `$OXO2_DATA`, and `$SOLR_HOME`. It sources the same `loadData.env.sh`, so an
unknown `OXO2_ENV` fails before anything is deleted.

## Key Design Decisions

1. **Container digest tracking** -- The login node checks the GHCR registry for a new image digest before submitting the job. Compute nodes may lack internet access, so the digest is passed via `--export`. The Singularity image is only re-pulled when the digest changes.

2. **Solr as a Singularity instance** -- Solr runs as a long-lived Singularity instance on the compute node (not as a Nextflow task). This lets it persist across all six stages and be accessible from sub-jobs on other nodes via the compute node's hostname.

3. **Cross-set inference in Stage 3** -- `JSON2NQUADS` runs per file, then `CONCAT_CORPUS` and `INFER_CROSS_SET` reason **once** over the concatenated all-sets corpus (ADR-0016). Explanations are then precomputed by component-sharded chase+trace (ADR-0028), which fans out over thousands of independent shards rather than re-reasoning over the whole corpus per chunk.

4. **SSSOM-to-JSON runs as a single batch** -- Unlike other stages, this is intentionally NOT parallelized per-file because output filenames are derived from `mappingSetId`, and multiple input files from different registries can collide. The batch converter handles this with `getUniqueFilename()`.

5. **Two-pass Solr indexing** -- Asserted mappings are indexed first (Stage 4), then inferred mappings are added (Stage 6). Stage 5 (`explanations2json`) needs the asserted data in Solr to resolve each inferred entity's CURIE/label and each asserted premise of every chain, creating a necessary ordering dependency. The `shard`/`explain` stages need no Solr, so they run before Stage 4.
</content>
</invoke>
