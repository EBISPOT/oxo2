# OxO2 HPC Data Release Pipeline

This document describes the end-to-end process for running an OxO2 data release on the EBI HPC (SLURM) cluster. The pipeline downloads SSSOM ontology mappings, converts them to JSON, runs cross-set SSSOM inference via the Nemo rules engine, and indexes the asserted and (bare) inferred mappings into Apache Solr. Explanations are no longer precomputed in the dataload — they are deferred to a future on-demand service ([ADR-0020](adr/0020-defer-explanations-to-on-demand.md)).

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
   ├─ Check container digest      ├─ Pull Singularity image        ├─ SSSOM2JSON (x 1)
   └─ sbatch ───────────────────>├─ Copy Solr config              ├─ JSON2NQUADS (x M)
                                 ├─ Start Solr (Singularity)      ├─ CONCAT_CORPUS (x 1)
                                 ├─ nextflow run (download)       ├─ INFER_CROSS_SET (x 1)
                                 ├─ nextflow run (sssom2json)     └─ INFERENCES_TO_JSON (x 1)
                                 ├─ nextflow run (infer)─────────>
                                 ├─ json2solr (index-asserted)
                                 ├─ nextflow run (inferences2json)
                                 ├─ json2solr (index-inferred)
                                 ├─ archive (solr-data.tar.gz)
                                 └─ Stop Solr
```

The orchestrator's stages are resumable via `START_STAGE`
([ADR-0019](adr/0019-resumable-hpc-dataload.md)): `download`, `sssom2json`, `nquads`, `infer`,
`index-asserted`, `inferences2json`, `index-inferred`, `archive`.

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
    crossSet/            # Concatenated corpus + Nemo inference output (TTL)
    solr/                # Bare inferred-mapping JSON for Solr (mapping/ and mappingSet/)

$SOLR_HOME/
  oxo2-mappings/         # Solr core: individual mappings
  oxo2-mappingsets/      # Solr core: mapping sets
  logs/                  # Solr server logs
  pid/                   # Solr PID file
```

All directories except `NXF_SINGULARITY_CACHEDIR` are cleaned at the start of a full run.

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
| Memory | 4 GB    |
| Time | 2 hours |

**Input:** `$OXO2_DATA/sssom/` (all TSV files)  
**Output:** `$OXO2_DATA/sssom-as-json/mapping/*.json` and `$OXO2_DATA/sssom-as-json/mappingSet/*.json`

### Step 4: Stage 3 -- Infer Mappings (SSSOM, cross-set) (`inferSssomCrossSet.nf`)

This is the most resource-intensive stage. SSSOM reasoning (ADR-0016) runs **once across all
mapping sets**: each set's JSON is converted to N-Quads, every set's N-Quads is concatenated into one
corpus, and `nmo` runs `sssom.rls` over the whole corpus to produce the inferred mappings. No trace or
explanation is computed — explanations are deferred to an on-demand service (ADR-0020).

```
*.json ─> JSON2NQUADS ─> CONCAT_CORPUS ─> INFER_CROSS_SET
 (per file)   (per file)      (x 1)           (x 1)
```

`JSON2NQUADS` runs per file; `CONCAT_CORPUS` and `INFER_CROSS_SET` are each a single run over the
concatenated corpus. The output `inferences.ttl` is the inferred mappings themselves; it is turned
into bare Solr JSON later by `inferences2json.nf` (Step 6), after the asserted mappings are indexed.

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

### Step 6: Stage 5 -- Inferences to JSON (bare) (`inferences2json.nf`)

**Parallelism:** A single process over the one cross-set `inferences.ttl`.

Builds **bare** inferred-mapping JSON straight from `inferences.ttl` (ADR-0020): one document per
inferred mapping with subject/predicate/object, CURIE/label, and `inference_type` — no explanation
chain, distance, or asserted evidence. The process queries the Solr index (populated in Stage 4) to
resolve each inferred entity's CURIE and label, which is why it must run after Stage 4.

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

### Step 8: Archive and Shutdown

1. **Archive:** Packs the contents of `$SOLR_HOME` into `$OXO2_INFERENCES/solr-data.tar.gz` (using the
   image's `pigz`). This archive is what the separate copy-to-NFS + Kubernetes redeploy job consumes; the
   dataload itself stops here and does not deploy.
2. **Stop Solr:** `solr stop` then `singularity instance stop solr_svc`.
3. **Permissions:** `chmod -R 777 "$SOLR_HOME"/*` so downstream services (e.g., Kubernetes pods) can read the Solr data.

## Resource Summary

| Process | CPU | Memory | Time | Parallelism |
|---------|-----|--------|------|-------------|
| Main SLURM job | 1 | 16 GB | 72h | 1 (orchestrator) |
| DOWNLOAD_REGISTRY | 1 | 4 GB | 2h | N registries |
| SSSOM2JSON | 1 | 4 GB | 2h | 1 (batch) |
| JSON2NQUADS | 1 | 4 GB | 2h | M files |
| CONCAT_CORPUS | 1 | 4 GB | 2h | 1 |
| INFER_CROSS_SET | 1 | 24 GB | 8h | 1 |
| INFERENCES_TO_JSON | 1 | 16 GB | 8h | 1 |

Nextflow concurrency limit: `executor.queueSize = 150` in the `slurm` profile — up to 150 sub-jobs queued/running concurrently. No `submitRateLimit` is set.

## Nextflow Configuration Highlights

Key settings from `nextflow/nextflow.config`:

- **SLURM profile:** `executor.name = 'slurm'`, `queueSize = 150` (no `submitRateLimit` set)
- **Singularity:** `enabled = true`, `autoMounts = true`, whitelists `SOLR_URL,no_proxy,JAVA_OPTS`
- **Error handling:** Default `errorStrategy = 'ignore'` with `maxRetries = 1`; `INFERENCES_TO_JSON` overrides this to `terminate` (single cross-set output — fail loud rather than silently drop all inferred mappings)
- **Caching:** `cache = 'lenient'` allows Nextflow to reuse completed tasks on resume
- **Reports:** HTML execution report, timeline, and trace file written to `$NXF_LOGS/`

## Error Handling

- **Shell strict mode:** `set -euo pipefail` in `loadData.slurm` fails the pipeline on any error.
- **Nextflow retries:** Default `errorStrategy = 'ignore'` with `maxRetries = 1`; failing tasks are skipped rather than aborting the workflow (except `INFERENCES_TO_JSON`, which terminates).
- **Solr verification:** Stage 4 explicitly checks document counts and aborts if indexing failed.
- **Empty file handling:** All Nextflow processes remove output files smaller than 1 byte to prevent downstream issues.
- **Heap dumps:** The `inferences2json` process enables `-XX:+HeapDumpOnOutOfMemoryError` for post-mortem analysis.

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

3. **Cross-set inference in Stage 3** -- `JSON2NQUADS` runs per file, then `CONCAT_CORPUS` and `INFER_CROSS_SET` reason **once** over the concatenated all-sets corpus (ADR-0016). The inferred mappings are indexed bare; explanations are deferred to an on-demand service (ADR-0020).

4. **SSSOM-to-JSON runs as a single batch** -- Unlike other stages, this is intentionally NOT parallelized per-file because output filenames are derived from `mappingSetId`, and multiple input files from different registries can collide. The batch converter handles this with `getUniqueFilename()`.

5. **Two-pass Solr indexing** -- Asserted mappings are indexed first (Stage 4), then inferred mappings are added (Stage 6). Stage 5 (`inferences2json`) needs the asserted data in Solr to resolve each inferred entity's CURIE/label, creating a necessary ordering dependency.
</content>
</invoke>
