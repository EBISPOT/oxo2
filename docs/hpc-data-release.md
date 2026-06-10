# OxO2 HPC Data Release Pipeline

This document describes the end-to-end process for running an OxO2 data release on the EBI HPC (SLURM) cluster. The pipeline downloads SSSOM ontology mappings, converts them to JSON, runs inference and explanation generation via the Nemo rules engine, and indexes everything into Apache Solr.

## Architecture Overview

The HPC data release uses a **three-layer execution model**:

1. **Login node** (`loadData.hpc`) -- validates the environment and submits a SLURM batch job. No computation happens here.
2. **Compute node** (`loadData.slurm`) -- the main orchestrator allocated by SLURM. It initializes directories, manages Solr via Singularity, and launches Nextflow pipelines.
3. **Nextflow + SLURM sub-jobs** -- Nextflow submits individual tasks as SLURM jobs, running them inside Singularity containers pulled from `ghcr.io/ebispot/oxo2-nextflow:dev`.

```
 Login Node                    Compute Node (SLURM)              SLURM Sub-jobs
 ──────────                    ────────────────────              ───────────────
 loadData.hpc                  loadData.slurm                   Nextflow tasks
   │                             │                                │
   ├─ Validate env vars          ├─ Create directories            ├─ DOWNLOAD_REGISTRY (x N)
   ├─ Check container digest     ├─ Pull Singularity image        ├─ SSSOM2JSON (x 1)
   └─ sbatch ───────────────────>├─ Copy Solr config              ├─ JSON2NQUADS (x M)
                                 ├─ Start Solr (Singularity)      ├─ INFER_CROSS_SET (x 1)
                                 ├─ nextflow run (Stages 1-3)────>├─ DETERMINE_CROSS_SET_TRACE...
                                 ├─ json2solr (Stage 4)           ├─ EXPLAIN_CROSS_SET_CHUNK...
                                 ├─ nextflow run (Stage 5)───────>└─ EXPLANATIONS_TO_JSON
                                 ├─ json2solr (Stage 6)
                                 └─ Stop Solr
```

## Prerequisites

### Environment Variables

All variables are hardcoded with EBI Evora defaults in `loadData.hpc` but can be overridden:

| Variable | Default                                      | Description |
|----------|----------------------------------------------|-------------|
| `OXO2_DATA` | `/hpc/.../oxo2/dev/data`                     | Root directory for all pipeline data |
| `OXO2_CONFIG` | `/nfs/.../oxo-config-evora.json`  | JSON file listing mapping registries to download |
| `NEXTFLOW_DIR` | `/hps/.../nextflow`                 | Nextflow working directories and caches |
| `SOLR_HOME` | `/hps/.../solr-data`                | Solr index data (persists between runs) |
| `NF_CONTAINER` | `docker://ghcr.io/ebispot/oxo2-nextflow:dev` | Container image URI |
| `HPC_TIME` | `72:00:00`                                   | SLURM time limit for the main job |
| `HPC_MEM` | `16G`                                        | SLURM memory for the main orchestrator job |

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
./loadData.hpc
```

What happens:
1. Validates that all required environment variables are set.
2. Queries the GHCR registry for the latest container image digest. This runs on the login node because compute nodes typically lack internet access.
3. Builds `sbatch` arguments, forwarding all environment variables (including the remote digest) to the compute node.
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
  tmp/                   # Temporary files
  inferences/
    crossSet/                  # Concatenated corpus + Nemo inference output (TTL)
    inferenceChainsCrossSet/   # Explanation chains (JSON)
    solr/                # Final enriched JSON for Solr

$SOLR_HOME/
  oxo2-mappings/         # Solr core: individual mappings
  oxo2-mappingsets/      # Solr core: mapping sets
  logs/                  # Solr server logs
  pid/                   # Solr PID file
```

All directories except `NXF_SINGULARITY_CACHEDIR` are cleaned at the start of each run.

**Singularity image management:** The image is only re-pulled when:
- It doesn't exist locally, OR
- The remote GHCR digest differs from the locally cached digest

The digest is tracked in `$NFS_PATH/oxo2-nextflow.digest`.

**Solr startup:** Solr runs as a Singularity instance (long-running background container) on the compute node, bound to `$SOLR_HOME` for data persistence:

```bash
singularity instance start \
    --bind "$SOLR_HOME:/opt/solr/server/solr" \
    --bind "$SOLR_HOME/logs:/opt/solr/server/logs" \
    "$SIF_IMAGE" solr_svc

singularity exec instance://solr_svc \
    /opt/solr/bin/solr start --user-managed -Djetty.host=$(hostname)
```

Solr binds to $(hostname) so Nextflow sub-jobs on other nodes can reach it via `http://<compute-node-hostname>:8983/solr`.

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
| Memory | 16 GB   |
| Time | 2 hours |

**Input:** `$OXO2_DATA/sssom/` (all TSV files)  
**Output:** `$OXO2_DATA/sssom-as-json/mapping/*.json` and `$OXO2_DATA/sssom-as-json/mappingSet/*.json`

### Step 4: Stage 3 -- Infer Mappings (SSSOM, cross-set) (`inferSssomCrossSet.nf`)

This is the most complex and resource-intensive stage. SSSOM reasoning (ADR-0016) runs **once across all
mapping sets**: each set's JSON is converted to N-Quads, every set's N-Quads is concatenated into one
corpus, `nmo` runs `sssom.rls` over the whole corpus, and the inferred mappings are traced in chunks.

```
*.json ─> JSON2NQUADS ─> CONCAT_CORPUS ─> INFER_CROSS_SET ─> DETERMINE_CROSS_SET_TRACE ─> SPLIT_CROSS_SET_TRACE ─> EXPLAIN_CROSS_SET_CHUNK ─> MERGE_CROSS_SET_CHAIN
 (per file)   (per file)      (x 1)           (x 1)              (x 1)                       (x 1)                    (per chunk, fan-out)        (x 1)
```

`JSON2NQUADS` runs per file; everything downstream is a single run over the concatenated corpus.
`SPLIT_CROSS_SET_TRACE` divides the facts-to-trace file into chunks of `params.trace_chunk_size` mappings
(default 20000), `EXPLAIN_CROSS_SET_CHUNK` runs `nmo` per chunk concurrently (capped by
`executor.queueSize`), and `MERGE_CROSS_SET_CHAIN` recombines the per-chunk chain JSONs into one file.

> **Sizing is provisional.** The cross-set processes have not yet run at full scale on HPC; the values below
> are the `nextflow.config` `slurm` estimates and must be recalibrated to ~2x observed peak RSS after the
> first real HPC run captures cross-set traces.

#### Stage 3a: JSON to N-Quads (`JSON2NQUADS`)

Converts each JSON mapping file to N-Quads (`<s> <p> <o> <urn:uuid:mapping_id> .`, ADR-0010) using the
`oxo2-json2inferences` JAR, carrying source-mapping provenance through Nemo.

| Resource | Value |
|----------|-------|
| CPU | 2 |
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
| Memory | 32 GB |
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

#### Stage 3d: Determine Inferences to Trace (`DETERMINE_CROSS_SET_TRACE`)

Selects which inferred mappings need explanation chains, via the Java `MainDispatcher inferences2trace`
command.

| Resource | Value |
|----------|-------|
| CPU | 2 |
| Memory | 4 GB |
| Time | 4 hours |

**Input:** `$OXO2_INFERENCES/crossSet/inferences.ttl`
**Output:** `$OXO2_INFERENCES/crossSet/inferencesToTrace.txt`

#### Stage 3e: Split Trace Input (`SPLIT_CROSS_SET_TRACE`)

Splits the facts-to-trace file into chunks of `params.trace_chunk_size` mappings (default 20000) so the
trace step can fan out.

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | 1 GB |
| Time | 30 min |

#### Stage 3f: Explain Inference Chunk (`EXPLAIN_CROSS_SET_CHUNK`)

Runs Nemo with tracing enabled on a single chunk of the facts-to-trace file. One task per chunk; concurrency
capped by `executor.queueSize`. Each chunk re-loads the corpus and re-materialises the inferred graph, so
heap is sized from the corpus + inferred TTL size (floor 8 GB).

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | dynamic ((corpus + inferred) x 3, floor 8 GB) |
| Time | 8 hours |

#### Stage 3g: Merge Chain JSON (`MERGE_CROSS_SET_CHAIN`)

Deduplicates and concatenates the per-chunk chain JSONs into the single cross-set chain file matching the
schema `NemoInferenceReader` expects.

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | 32 GB |
| Time | 2 hours |

**Command:**
```bash
nmo sssom.rls \
    --param importfile=<assertedCorpus.nq> \
    --param exportfile=<inferences.ttl> \
    --trace-input-file <inferencesToTrace.txt> \
    --trace-output <chains.json>
```

**Input:** Corpus N-Quads + Inferred TTL + trace selection file
**Output:** `$OXO2_INFERENCES/inferenceChainsCrossSet/inferences-chains.json`


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

**Parallelism:** One SLURM sub-job per inference chain file.

Converts Nemo inference chains into enriched JSON mappings with explanations. The process queries the Solr index (populated in Stage 4) to enrich the output, which is why this stage must run after Stage 4.

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | 16 GB |
| Time | 12 hours |

Sized from observed peak RSS of ~8 GB (mondo, pre-RG/RI cleanup) across 231 mapping sets; mean was ~743 MB. 16 GB gives ~2× headroom. Slowest historical realtime was mondo at 11.9 h; post-RG/RI-cleanup workload is lighter, so 12 h is a comfortable upper bound.

Environment variables `SOLR_URL`, `no_proxy`, and `JAVA_OPTS` are whitelisted in the Singularity configuration and passed to the container so the Java process can reach Solr on the compute node.

**Input:** `$OXO2_INFERENCES/inferenceChainsCrossSet/inferences-chains.json`  
**Output:** `$OXO2_INFERENCES/solr/*-explained.json`

### Step 7: Stage 6 -- Index Inferred Mappings to Solr

Posts the enriched inferred mappings to the same `oxo2-mappings` core:

```bash
json2solr.sh "$OXO2_INFERENCES/solr" http://localhost:8983/solr/oxo2-mappings
```

### Step 8: Cleanup and Shutdown

1. Stops Solr: `solr stop` then `singularity instance stop solr_svc`
2. Sets permissions: `chmod -R 777 "$SOLR_HOME"/*` so downstream services (e.g., Kubernetes pods) can read the Solr data.

## Resource Summary

| Process | CPU | Memory | Time | Parallelism |
|---------|-----|--------|------|-------------|
| Main SLURM job | 1 | 16 GB | 72h | 1 (orchestrator) |
| DOWNLOAD_REGISTRY | 1 | 4 GB | 2h | N registries |
| SSSOM2JSON | 1 | 8 GB | 2h | 1 (batch) |
| JSON2NQUADS | 2 | 4 GB | 2h | M files |
| CONCAT_CORPUS | 1 | 4 GB | 2h | 1 |
| INFER_CROSS_SET | 1 | 32 GB | 8h | 1 |
| DETERMINE_CROSS_SET_TRACE | 2 | 4 GB | 4h | 1 |
| SPLIT_CROSS_SET_TRACE | 1 | 1 GB | 30m | 1 |
| EXPLAIN_CROSS_SET_CHUNK | 1 | dynamic (≥8 GB) | 8h | chunks |
| MERGE_CROSS_SET_CHAIN | 1 | 32 GB | 2h | 1 |
| EXPLANATIONS_TO_JSON | 1 | 16 GB | 12h | M files |

Nextflow concurrency limit: `executor.queueSize = 200` in the `slurm` profile — up to 200 sub-jobs queued/running concurrently. No `submitRateLimit` is set.

## Nextflow Configuration Highlights

Key settings from `nextflow/nextflow.config`:

- **SLURM profile:** `executor.name = 'slurm'`, `queueSize =50` (no `submitRateLimit` set)
- **Singularity:** `enabled = true`, `autoMounts = true`, whitelists `SOLR_URL,no_proxy,JAVA_OPTS`
- **Error handling:** Default `errorStrategy = 'ignore'` with `maxRetries = 1` (per-process overrides may apply)
- **Caching:** `cache = 'lenient'` allows Nextflow to reuse completed tasks on resume
- **Reports:** HTML execution report, timeline, and trace file written to `$NXF_LOGS/`

## Error Handling

- **Shell strict mode:** `set -euo pipefail` in `loadData.slurm` fails the pipeline on any error.
- **Nextflow retries:** Default `errorStrategy = 'ignore'` with `maxRetries = 1`; failing tasks are skipped rather than aborting the workflow.
- **Solr verification:** Stage 4 explicitly checks document counts and aborts if indexing failed.
- **Empty file handling:** All Nextflow processes remove output files smaller than 1 byte to prevent downstream issues.
- **Heap dumps:** The `explanations2json` process enables `-XX:+HeapDumpOnOutOfMemoryError` for post-mortem analysis.

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

To remove all pipeline data and start fresh:

```bash
./cleanup.hpc
```

This submits a quick SLURM job (`srun`, 1 hour, 8 GB) that deletes `$NEXTFLOW_DIR`, `$OXO2_DATA`, and `$SOLR_HOME`.

## Key Design Decisions

1. **Container digest tracking** -- The login node checks the GHCR registry for a new image digest before submitting the job. Compute nodes may lack internet access, so the digest is passed via `--export`. The Singularity image is only re-pulled when the digest changes.

2. **Solr as a Singularity instance** -- Solr runs as a long-lived Singularity instance on the compute node (not as a Nextflow task). This lets it persist across all six stages and be accessible from sub-jobs on other nodes via the compute node's hostname.

3. **Per-file pipelining in Stage 3** -- The four inference sub-stages are wired as a single Nextflow workflow with channels connecting them. Each file flows through independently, so file A can be in the explanation stage while file B is still being inferred.

4. **SSSOM-to-JSON runs as a single batch** -- Unlike other stages, this is intentionally NOT parallelized per-file because output filenames are derived from `mappingSetId`, and multiple input files from different registries can collide. The batch converter handles this with `getUniqueFilename()`.

5. **Two-pass Solr indexing** -- Asserted mappings are indexed first (Stage 4), then inferred mappings are added (Stage 6). Stage 5 (explanations2json) needs the asserted data in Solr to enrich inferred mappings, creating a necessary ordering dependency.
