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
   └─ sbatch ───────────────────>├─ Copy Solr config              ├─ JSON2TTL (x M)
                                 ├─ Start Solr (Singularity)      ├─ INFER_MAPPINGS (x M)
                                 ├─ nextflow run (Stages 1-3)────>├─ DETERMINE_INFERENCES...
                                 ├─ json2solr (Stage 4)           ├─ EXPLAIN_INFERENCES...
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
  assertedMappings/      # RDF Turtle representations
  tmp/                   # Temporary files
  inferences/
    inferredMappings/    # Nemo inference output (TTL)
    inferencesToTrace/   # Selected inferences for explanation
    inferenceChains/     # Explanation chains (JSON)
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

### Step 4: Stage 3 -- Infer and Explain Mappings (`inferAndExplainMappings.nf`)

This is the most complex and resource-intensive stage. It is a **four-process pipelined workflow** where each input file flows independently through all four sub-stages:

```
*.json ──> JSON2TTL ──> INFER_MAPPINGS ──> DETERMINE_INFERENCES_TO_TRACE ──> EXPLAIN_INFERENCES_TO_TRACE
 (per file)  (per file)    (per file)              (per file)                        (per file)
```

Because each file moves through the pipeline independently, later files can begin Stage 3a while earlier files are still in Stage 3c. This **per-file pipelining** significantly improves throughput.

#### Stage 3a: JSON to Turtle (`JSON2TTL`)

Converts each JSON mapping file to RDF Turtle format using the `oxo2-json2inferences` JAR (`MainDispatcher json2ttl`).

| Resource | Value |
|----------|-------|
| CPU | 2 |
| Memory | 8 GB |
| Time | 2 hours |

**Input:** `$OXO2_DATA/sssom-as-json/mapping/*.json`  
**Output:** `$OXO2_DATA/assertedMappings/*.ttl`

#### Stage 3b: Nemo Inference (`INFER_MAPPINGS`)

Runs the Nemo rules engine (`nmo`) with `chain-rules.rls` to infer transitive mappings. This is the **heaviest task in the entire pipeline**.

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | **64 GB** |
| Time | **24 hours** |

**Command:**
```bash
nmo chain-rules.rls \
    --param importfile=<asserted.ttl> \
    --param exportfile=<output.ttl> \
    -o -v -D <export_dir>
```

**Input:** `$OXO2_DATA/assertedMappings/*.ttl`  
**Output:** `$OXO2_INFERENCES/inferredMappings/*.ttl`

#### Stage 3c: Determine Inferences to Trace (`DETERMINE_INFERENCES_TO_TRACE`)

Selects which inferred mappings need explanation chains, using the Java `MainDispatcher inferences2trace` command.

| Resource | Value |
|----------|-------|
| CPU | 4 |
| Memory | 12 GB |
| Time | 4 hours |

**Input:** `$OXO2_INFERENCES/inferredMappings/*.ttl`  
**Output:** `$OXO2_INFERENCES/inferencesToTrace/*.txt`

#### Stage 3d: Explain Inferences (`EXPLAIN_INFERENCES_TO_TRACE`)

Runs Nemo with tracing enabled to generate human-readable explanation chains for each selected inference.

| Resource | Value |
|----------|-------|
| CPU | 1 |
| Memory | 64 GB |
| Time | 24 hours |

**Command:**
```bash
nmo chain-rules.rls \
    --param importfile=<asserted.ttl> \
    --param exportfile=<inferred.ttl> \
    --trace-input-file <inferences.txt> \
    --trace-output <chains.json>
```

**Input:** Asserted TTL + Inferred TTL + trace selection file  
**Output:** `$OXO2_INFERENCES/inferenceChains/*-chains.json`

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
| Memory | 64 GB |
| Time | 24 hours |

Environment variables `SOLR_URL`, `no_proxy`, and `JAVA_OPTS` are whitelisted in the Singularity configuration and passed to the container so the Java process can reach Solr on the compute node.

**Input:** `$OXO2_INFERENCES/inferenceChains/*-chains.json`  
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
| JSON2TTL | 2 | 8 GB | 2h | M files |
| **INFER_MAPPINGS** | 1 | **64 GB** | **24h** | **M files** |
| DETERMINE_INFERENCES_TO_TRACE | 4 | 12 GB | 4h | M files |
| EXPLAIN_INFERENCES_TO_TRACE | 1 | 64 GB | 24h | M files |
| EXPLANATIONS_TO_JSON | 1 | 64 GB | 24h | M files |

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

5. **Two-phase Solr indexing** -- Asserted mappings are indexed first (Stage 4), then inferred mappings are added (Stage 6). Stage 5 (explanations2json) needs the asserted data in Solr to enrich inferred mappings, creating a necessary ordering dependency.
