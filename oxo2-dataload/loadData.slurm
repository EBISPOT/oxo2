#!/bin/bash
#SBATCH --job-name=oxo2-dataload
#SBATCH --output=%x-%j.out
#SBATCH --error=%x-%j.err

#Set BASH strict mode:
# -e: fails on any error
# -u: fails on any variable that has not been set
# -o pipefail: fails complete pipeline on any failure in the pipeline.
set -euo pipefail

echo "=== OXO2 HPC Dataload Started ==="
dataload_start_time=$(date +%s)
echo "OxO2 Dataload started at: $dataload_start_time"

# SCRIPT_DIR is passed via --export from loadData.hpc (BASH_SOURCE unreliable in Slurm)
: "${SCRIPT_DIR:?SCRIPT_DIR must be set}"

# ---------------------------------------------------------------------------
# Resumable pipeline: START_STAGE selects where the run (re)enters the pipeline.
# A full run is START_STAGE=download (the default). Any later value resumes from that
# (sub)stage, reusing the PUBLISHED artifacts of the earlier stages (which the stage-aware
# cleanup below deliberately preserves). The `nquads`/`infer` substages map to `-entry` points in
# inferSssomCrossSet.nf, and `shard`/`explain` to `-entry` points in explainSssomCrossSet.nf — the
# component-sharded chase+trace that restores precomputed explanations (ADR-0028, superseding
# ADR-0020). `explanations2json` then builds the inferred-mapping JSON from the shard traces.
# See oxo2-dataload/CONTEXT.md § Resumable dataload.
# ---------------------------------------------------------------------------
START_STAGE="${START_STAGE:-download}"

# OXO2_INFERENCES is derived from OXO2_DATA and is referenced by the shared OXO2_STAGE_OWNS map, so
# set it before sourcing the helper (the map is expanded at source time).
OXO2_INFERENCES=$OXO2_DATA/inferences
export OXO2_INFERENCES

# Shared resumable-pipeline contract (ADR-0019): the ordered stage list, should_run, stage-aware
# cleanup (oxo2_clean_owned_artifacts), the resume checkpoint, and the should_wipe_solr / solr_needed
# decisions. loadData.nextflow (local/integration) sources the very same file, so the two
# orchestrators cannot drift on the stage sequence or the cleanup rules.
. "$SCRIPT_DIR/loadData.lib.sh"

echo "START_STAGE=$START_STAGE (index $OXO2_START_INDEX of ${#OXO2_STAGES[@]} stages: ${OXO2_STAGES[*]})"

export SOLR_URL="http://$(hostname):8983/solr"
export no_proxy="localhost,127.0.0.1,$(hostname)"
# -XX:MaxRAMPercentage=75 — default is 25%, which caps heap at ~4 GB for a 16 GB cgroup.
# Large mapping sets (e.g. NCBI taxon) OOM'd on the default. 75% lets each stage's JVM
# use most of its Nextflow-declared cgroup without hard-coding -Xmx per stage.
export JAVA_OPTS="${JAVA_OPTS:-} -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dhttp.nonProxyHosts=localhost|127.0.0.1|$(hostname)"

echo "=== OXO2 HPC Dataload ==="
echo "Job ID: $SLURM_JOB_ID"
echo "Node: $(hostname)"
echo "Date: $(date)"
echo "SCRIPT_DIR=$SCRIPT_DIR"
echo "OXO2_DATA=$OXO2_DATA"
echo "OXO2_CONFIG=$OXO2_CONFIG"
echo "NEXTFLOW_DIR=$NEXTFLOW_DIR"
echo "SOLR_HOME=$SOLR_HOME"
echo "NF_CONTAINER=$NF_CONTAINER"

# --- Setup Nextflow directories ---
NF_PROFILE=slurm
export NF_PROFILE NF_CONTAINER

NXF_WORK=$NEXTFLOW_DIR/NXF_WORK
NXF_HOME=$NEXTFLOW_DIR/NXF_HOME
NXF_TEMP=$NEXTFLOW_DIR/NXF_TEMP
NXF_CACHE_DIR=$NEXTFLOW_DIR/NXF_CACHE_DIR
NXF_SINGULARITY_CACHEDIR=${HPC_PATH:-$NEXTFLOW_DIR}/NXF_SINGULARITY_CACHEDIR
NXF_LOGS=$NEXTFLOW_DIR/logs
export NXF_WORK NXF_HOME NXF_TEMP NXF_CACHE_DIR NXF_SINGULARITY_CACHEDIR NXF_LOGS

# verify_solr_indexed [core...]: assert each named core has documents. Defaults to the two mapping
# cores. oxo2-entities is NOT in the default set on purpose: it is derived from the mappings AFTER
# they are indexed (ADR-0034), so at every call site before index-entities it is legitimately empty
# and asserting on it would fail a healthy run. The index-entities stage verifies it explicitly.
verify_solr_indexed() {
    local cores=("$@")
    if [ ${#cores[@]} -eq 0 ]; then
        cores=(oxo2-mappings oxo2-mappingsets)
    fi
    echo "Verifying Solr indexing (${cores[*]})..."
    local core count
    for core in "${cores[@]}"; do
        count=$(curl -sf "$SOLR_URL/$core/select?q=*:*&rows=0" \
            | grep -o '"numFound":[0-9]*' | grep -o '[0-9]*')
        echo "  $core: ${count:-0} documents"
        if [ -z "$count" ] || [ "$count" -eq 0 ]; then
            echo "ERROR: No documents found in $core after indexing" >&2
            exit 1
        fi
    done
    echo "Solr indexing verified successfully."
}

if [ -f "$OXO2_CHECKPOINT_FILE" ]; then
    echo "Previous run's last completed stage: $(cat "$OXO2_CHECKPOINT_FILE")"
fi

# Resolve the one timestamp every mapping set of this run is stamped with (ADR-0043). Exported so
# sssom2json.nf can pass it to the SSSOM2JSON JAR.
oxo2_resolve_release_date

# ---------------------------------------------------------------------------
# Stage-aware cleanup.
# Transient Nextflow dirs are always wiped — resume reads PUBLISHED artifacts under $OXO2_DATA,
# never NXF_WORK, so each run gets a clean work dir. The Singularity image cache is NOT wiped here
# (only refreshed below when a newer image is available).
# ---------------------------------------------------------------------------
echo "Cleaning transient Nextflow working dirs..."
rm -rf "$NXF_WORK"/* "$NXF_HOME"/* "$NXF_TEMP"/* "$NXF_CACHE_DIR"/* 2>/dev/null || true
rm -rf "$NXF_LOGS"/* "$NXF_LOGS"/.*[!.]* 2>/dev/null || true

# Stage-aware cleanup (shared loadData.lib.sh): wipe only the artifacts owned by START_STAGE and
# every later stage, preserving everything earlier stages produced as the resume inputs.
oxo2_clean_owned_artifacts

# Solr's on-disk index is wiped only when the asserted load will run from scratch (START_STAGE at
# or before index-asserted). Resuming at explanations2json / index-inferred / archive must KEEP the
# asserted data already indexed by an earlier run — explanations2json queries Solr for entity details.
WIPE_SOLR=false
if should_wipe_solr; then
    WIPE_SOLR=true
fi
if [ "$WIPE_SOLR" = true ]; then
    echo "Wiping Solr home (asserted load will run from scratch)..."
    rm -rf "$SOLR_HOME"/*
else
    echo "Preserving Solr home (resuming after asserted data was indexed)."
fi

# --- (Re)create the standard directory structure (after cleanup, so dirs always exist) ---
mkdir -p "$NXF_WORK" "$NXF_HOME" "$NXF_TEMP" "$NXF_CACHE_DIR" "$NXF_SINGULARITY_CACHEDIR" "$NXF_LOGS"
mkdir -p "$OXO2_DATA/sssom" "$OXO2_DATA/sssom-as-json" "$OXO2_DATA/assertedMappings"
mkdir -p "$OXO2_INFERENCES/solr/mapping" "$OXO2_INFERENCES/solr/mappingSet"
mkdir -p "$OXO2_DATA/entities"
mkdir -p "$OXO2_INFERENCES/crossSet"
mkdir -p "$OXO2_DATA/tmp"
mkdir -p "$SOLR_HOME"

# Solr is needed for the indexing stages (index-asserted, explanations2json, index-inferred):
# explanations2json resolves entity CURIEs/labels from the asserted index. We start it for any run
# that reaches one of those; START_STAGE=archive (a re-archive of an existing $SOLR_HOME) does not
# start Solr.
SOLR_NEEDED=false
if solr_needed; then
    SOLR_NEEDED=true
fi

# --- Load Nextflow module ---
module load nextflow

echo "Nextflow version: $(nextflow -version 2>&1 | head -3)"

# --- Pull Singularity image (update if newer version available) ---
# Always needed: every Nextflow stage runs in this image and the archive step uses its pigz.
# REMOTE_DIGEST and SIF_DIGEST_FILE are passed from loadData.hpc (login node has internet access, compute nodes do not)
SIF_IMAGE="$NXF_SINGULARITY_CACHEDIR/oxo2-nextflow.sif"
echo "Remote image digest: ${REMOTE_DIGEST:-not available}"

LOCAL_DIGEST=""
if [ -f "$SIF_DIGEST_FILE" ]; then
    LOCAL_DIGEST=$(cat "$SIF_DIGEST_FILE")
fi

if [ ! -f "$SIF_IMAGE" ]; then
    echo "Pulling Singularity image: $NF_CONTAINER"
    singularity pull "$SIF_IMAGE" "$NF_CONTAINER"
    [ -n "$REMOTE_DIGEST" ] && echo "$REMOTE_DIGEST" > "$SIF_DIGEST_FILE"
elif [ -n "$REMOTE_DIGEST" ] && [ "$REMOTE_DIGEST" != "$LOCAL_DIGEST" ]; then
    echo "New image version detected (remote: $REMOTE_DIGEST, local: $LOCAL_DIGEST), pulling update..."
    rm -f "$SIF_IMAGE"
    rm -f "$NXF_SINGULARITY_CACHEDIR"/*.img
    singularity pull "$SIF_IMAGE" "$NF_CONTAINER"
    echo "$REMOTE_DIGEST" > "$SIF_DIGEST_FILE"
else
    echo "Using cached Singularity image: $SIF_IMAGE (digest: ${LOCAL_DIGEST:-unknown})"
fi

if [ "$SOLR_NEEDED" = true ]; then
    # --- Copy Solr config (only when wiping) ---
    # copySolrConfig.sh deletes the oxo2-mappings / oxo2-mappingsets core dirs (config AND data) and
    # re-copies empty config. Gate it on WIPE_SOLR, NOT just SOLR_NEEDED: on a resume that preserves
    # the asserted index (START_STAGE after index-asserted) we must keep the cores, or explanations2json
    # would query an empty index for entity CURIEs/labels (ADR-0019).
    if [ "$WIPE_SOLR" = true ]; then
        echo "###################" copySolrConfig...
        singularity exec --bind "$SOLR_HOME:/opt/solr/server/solr" "$SIF_IMAGE" \
            /opt/oxo/oxo2-dataload/copySolrConfig.sh
        sleep 2
    else
        echo "Preserving Solr cores (resuming after asserted data was indexed); skipping copySolrConfig."
        # ...except the oxo2-entities core. oxo2-entities is a read model (ADR-0034) whose headline
        # resume path is START_STAGE=mappings2entities against an already-indexed oxo2-mappings — a
        # path that does NOT wipe, so the full copySolrConfig above never runs and the core dir would
        # never be created (wait_for_solr_core would then hang). entities-only rebuilds JUST that core
        # (config and all), leaving oxo2-mappings and oxo2-mappingsets untouched: the run about to
        # happen re-derives every entity from the mappings, so its config must be current and its old
        # documents must go. Core dirs must be laid down while Solr is DOWN, so this precedes the start.
        singularity exec --bind "$SOLR_HOME:/opt/solr/server/solr" "$SIF_IMAGE" \
            /opt/oxo/oxo2-dataload/copySolrConfig.sh entities-only
    fi

    # --- Start Solr as a backgrounded foreground process ---
    # Run bin/solr in foreground (-f) inside a plain `singularity exec`, then background
    # it from the slurm shell with `&`. Solr's Java is a direct child of singularity exec
    # which is a direct child of this slurm batch step — everything stays inside the slurm
    # cgroup, with no detached sinit. We captured the host PID for cleanup.
    #
    # Previous attempts used `singularity instance start solr_svc` (detached sinit). On
    # this HPC, that pattern was being SIGTERM'd silently — sacct showed no signal at job
    # level, dmesg showed no kernel OOM, and the instance vanished mid-startup (last Solr
    # log line was core-loading; no shutdown message). Whatever was sending the kill only
    # saw the detached sinit. The foreground-and-background pattern keeps Solr visible to
    # slurm's process tracking and out of reach of that path.
    #
    # Bind Jetty to 0.0.0.0 — singularity uses host networking, so all of localhost, the
    # host's loopback, and the FQDN-resolved IPs reach the same listener.
    echo "Starting Solr (foreground via singularity exec, backgrounded by slurm shell)..."
    mkdir -p "$SOLR_HOME/logs" "$SOLR_HOME/pid"
    singularity exec \
        --bind "$SOLR_HOME:/opt/solr/server/solr" \
        --bind "$SOLR_HOME/logs:/opt/solr/server/logs" \
        --bind "$OXO2_DATA/tmp:/tmp" \
        --env "SOLR_PID_DIR=/opt/solr/server/solr/pid" \
        --env "SOLR_JETTY_HOST=0.0.0.0" \
        "$SIF_IMAGE" \
        /opt/solr/bin/solr start -f --user-managed -Djava.io.tmpdir=/tmp &
    SOLR_PID=$!
    echo "Solr running as PID $SOLR_PID (singularity exec wrapping bin/solr -f)"

    # Trap any exit (success, failure, signal) and tear Solr down. Without this, an early
    # `exit 1` from the wait loop would leave Solr running and the host's :8983 listener
    # would block the next slurm allocation on this node.
    cleanup_solr() {
        if [ -n "${SOLR_PID:-}" ] && kill -0 "$SOLR_PID" 2>/dev/null; then
            echo "Cleanup: stopping Solr (PID=$SOLR_PID)..."
            kill -TERM "$SOLR_PID" 2>/dev/null || true
            for _ in {1..30}; do
                kill -0 "$SOLR_PID" 2>/dev/null || break
                sleep 1
            done
            kill -KILL "$SOLR_PID" 2>/dev/null || true
            wait "$SOLR_PID" 2>/dev/null || true
        fi
    }
    trap cleanup_solr EXIT

    # --- Diagnostics: capture per-node network state for intermittent-failure forensics ---
    echo "=== Solr network diagnostics (host) ==="
    echo "hostname        : $(hostname)"
    echo "hostname -f     : $(hostname -f 2>/dev/null || echo n/a)"
    echo "hostname -I     : $(hostname -I 2>/dev/null || echo n/a)"
    echo "getent hosts    : $(getent hosts "$(hostname)" 2>/dev/null || echo 'no resolution')"
    echo "--- /etc/hosts ---"
    cat /etc/hosts 2>/dev/null || echo "n/a"
    echo "--- ip addr (host) ---"
    if command -v ip >/dev/null 2>&1; then
        ip -o addr show 2>/dev/null | awk '{print $2, $3, $4}'
    else
        echo "ip command not available"
    fi
    echo "--- listeners on :8983 (host) ---"
    if command -v ss >/dev/null 2>&1; then
        ss -ltnp 2>/dev/null | grep -E '(:8983|State)' || echo "ss: no listener on :8983"
    elif command -v lsof >/dev/null 2>&1; then
        lsof -iTCP:8983 -sTCP:LISTEN -P -n 2>/dev/null || echo "lsof: no listener on :8983"
    else
        echo "no ss/lsof on host"
    fi
    echo "--- SOLR_PID liveness ---"
    # Quick check that the backgrounded singularity exec hasn't already died. If it's
    # already dead at this point, Solr never even got out of bin/solr's preamble.
    if kill -0 "$SOLR_PID" 2>/dev/null; then
        echo "SOLR_PID $SOLR_PID is alive"
    else
        echo "WARNING: SOLR_PID $SOLR_PID is already dead before wait loop"
    fi
    echo "=== End Solr network diagnostics ==="

    # --- Verify Solr is reachable; abort if not ---
    # Two distinct probes, both via host curl (singularity uses host networking, so the
    # host shell and the container share the same network namespace — no need to exec
    # into the container for HTTP probes):
    #   1. localhost — confirms Solr is responding to HTTP at all
    #   2. $SOLR_URL (FQDN) — confirms cross-node reachability for Nextflow sub-jobs that
    #      will run on other compute nodes
    # Retry up to ~120s — bin/solr -f starts Solr asynchronously; we have to poll for the
    # listener to come up rather than relying on bin/solr's own wait check (which only
    # applies when running daemonized).
    wait_for_solr_url() {
        local url=$1
        local description=$2

        for attempt in {1..60}; do
            if curl -sf "$url" > /dev/null 2>&1; then
                echo "$description is ready at $url (attempt $attempt)"
                return 0
            fi
            # Fail fast if Solr's parent process died — no point waiting 120s for a dead Solr.
            if ! kill -0 "$SOLR_PID" 2>/dev/null; then
                echo "ERROR: SOLR_PID $SOLR_PID died while waiting for $description" >&2
                return 1
            fi
            sleep 2
        done

        echo "ERROR: $description did not become ready at $url after 120s" >&2
        return 1
    }

    # Dumps whatever we can grab about Solr/JVM state to stderr. Called immediately before
    # exiting on a wait_for_solr_url failure so the slurm .err file always captures
    # forensic state, regardless of which probe failed.
    dump_failure_state() {
        {
            echo ""
            echo "=== Failure-time diagnostics ==="
            echo "--- SOLR_PID ($SOLR_PID) alive? ---"
            if kill -0 "$SOLR_PID" 2>/dev/null; then
                echo "yes"
                echo "--- ps for SOLR_PID and descendants ---"
                ps -o pid,ppid,pgid,sid,stat,etime,comm -g "$SOLR_PID" 2>&1 \
                    || ps -ef 2>&1 | grep -E "(PID|$SOLR_PID|solr|java)" | head -40
            else
                echo "NO — Solr's parent process has died"
            fi
            echo "--- listeners on :8983 (host) ---"
            if command -v ss >/dev/null 2>&1; then
                ss -ltnp 2>/dev/null | grep -E '(:8983|State)' || echo "ss: no listener on :8983"
            elif command -v lsof >/dev/null 2>&1; then
                lsof -iTCP:8983 -sTCP:LISTEN -P -n 2>/dev/null || echo "lsof: no listener on :8983"
            fi
            echo "--- Solr PID file ($SOLR_HOME/pid/solr-8983.pid) ---"
            if [ -f "$SOLR_HOME/pid/solr-8983.pid" ]; then
                cat "$SOLR_HOME/pid/solr-8983.pid"
            else
                echo "no solr-8983.pid file"
            fi
            echo "--- JVM crash files ($SOLR_HOME/logs/jvm_crash_*.log, hs_err_*.log) ---"
            ls -la "$SOLR_HOME/logs"/jvm_crash_*.log "$SOLR_HOME/logs"/hs_err_*.log 2>/dev/null \
                || echo "no JVM crash/hs_err files"
            echo "--- tail -n 100 $SOLR_HOME/logs/solr.log ---"
            if [ -f "$SOLR_HOME/logs/solr.log" ]; then
                tail -n 100 "$SOLR_HOME/logs/solr.log"
            else
                echo "$SOLR_HOME/logs/solr.log does not exist"
            fi
            echo "--- last 20 entries of $SOLR_HOME/logs (if any) ---"
            ls -lt "$SOLR_HOME/logs" 2>/dev/null | head -20 || echo "logs dir not readable"
            echo "--- dmesg tail (kernel ring buffer; may require CAP_SYSLOG) ---"
            if dmesg --help 2>&1 | grep -q -- '--ctime'; then
                dmesg --ctime 2>&1 | tail -200 || echo "dmesg failed (restricted?)"
            else
                dmesg 2>&1 | tail -200 || echo "dmesg failed (restricted?)"
            fi
            echo "=== End failure-time diagnostics ==="
        } >&2
    }

    if ! wait_for_solr_url "http://localhost:8983/solr/admin/info/system" "Solr (via localhost)"; then
        dump_failure_state
        echo "ERROR: Solr is not responding to HTTP on localhost. Aborting." >&2
        exit 1
    fi

    if ! wait_for_solr_url "$SOLR_URL/admin/info/system" "Solr (via hostname)"; then
        dump_failure_state
        echo "ERROR: Solr is not reachable via hostname \"$SOLR_URL\" — Nextflow sub-jobs on other nodes will fail. Aborting." >&2
        exit 1
    fi

    # --- Wait for the asserted cores to finish loading ---
    # The probes above hit /admin/info/system, which is core-independent and answers as soon as the
    # admin handler is up — BEFORE the oxo2-mappings / oxo2-mappingsets cores finish loading. The
    # first json2solr POST then races core-loading: a still-loading core returns HTTP 503, the
    # curl -fsS in json2solr.sh exits non-zero, set -e aborts this script, and the EXIT trap SIGTERMs
    # Solr mid-load (surfacing as "CoreContainer has been closed"). Gate on each core being queryable
    # to close that race. select?q=*:*&rows=0 mirrors verify_solr_indexed and 503s until the core loads.
    # oxo2-entities is probed too — it is empty at this point, which is fine: this asserts the core is
    # LOADED (a 200 on an empty core), not that it has documents.
    for core in oxo2-mappings oxo2-mappingsets oxo2-entities; do
        if ! wait_for_solr_url "$SOLR_URL/$core/select?q=*:*&rows=0" "Solr core '$core'"; then
            dump_failure_state
            echo "ERROR: Solr core '$core' did not finish loading. Aborting." >&2
            exit 1
        fi
    done
fi  # SOLR_NEEDED

# --- Stage: download mappings ---
if should_run download; then
    start_time=$(date +%s)
    echo "###################" downloadMappings...
    nextflow -log "$NXF_LOGS/downloadMappings.nextflow.log" \
        -c "$SCRIPT_DIR/nextflow/nextflow.config" \
        run "$SCRIPT_DIR/downloadMappings.nf" \
        -profile "$NF_PROFILE" \
        -with-trace    "$NXF_LOGS/downloadMappings-trace.txt" \
        -with-report   "$NXF_LOGS/downloadMappings-report.html" \
        -with-timeline "$NXF_LOGS/downloadMappings-timeline.html"
    end_time=$(date +%s)
    echo "Download took: $((end_time - start_time)) seconds"
    record_stage download
fi

# --- Stage: SSSOM to JSON ---
if should_run sssom2json; then
    start_time=$(date +%s)
    echo "###################" sssom2json...
    nextflow -log "$NXF_LOGS/sssom2json.nextflow.log" \
        -c "$SCRIPT_DIR/nextflow/nextflow.config" \
        run "$SCRIPT_DIR/sssom2json.nf" \
        -profile "$NF_PROFILE" \
        -with-trace    "$NXF_LOGS/sssom2json-trace.txt" \
        -with-report   "$NXF_LOGS/sssom2json-report.html" \
        -with-timeline "$NXF_LOGS/sssom2json-timeline.html"
    end_time=$(date +%s)
    echo "SSSOM to JSON took: $((end_time - start_time)) seconds"
    record_stage sssom2json
fi

# --- Stage: inference (nquads -> infer), ADR-0016 / ADR-0020 ---
# A single Nextflow invocation runs the cross-set inference (JSON -> N-Quads -> corpus -> infer ->
# inferences.ttl). There is no trace/explain/merge — explanations are deferred to on-demand
# (ADR-0020). A resume at START_STAGE=infer re-enters at `-entry from_infer`, reading the published
# assertedCorpus.nq (see inferSssomCrossSet.nf). The block runs whenever any inference substage is
# in scope (START_STAGE at or before `infer`).
if should_run infer; then
    # Safe empty-array expansion (`${arr[@]+...}`) so `set -u` tolerates the full-run case.
    INFER_ENTRY_ARGS=()
    case "$START_STAGE" in
        infer)   INFER_ENTRY_ARGS=(-entry from_infer) ;;
        *)       INFER_ENTRY_ARGS=() ;;  # download|sssom2json|nquads -> full pipeline from JSON
    esac
    start_time=$(date +%s)
    echo "###################" "inferSssomCrossSet (SSSOM, cross-set) entry=${START_STAGE}..."
    nextflow -log "$NXF_LOGS/inferSssomCrossSet.nextflow.log" \
        -c "$SCRIPT_DIR/nextflow/nextflow.config" \
        run "$SCRIPT_DIR/inferSssomCrossSet.nf" \
        -profile "$NF_PROFILE" \
        ${INFER_ENTRY_ARGS[@]+"${INFER_ENTRY_ARGS[@]}"} \
        -with-trace    "$NXF_LOGS/inferSssomCrossSet-trace.txt" \
        -with-report   "$NXF_LOGS/inferSssomCrossSet-report.html" \
        -with-timeline "$NXF_LOGS/inferSssomCrossSet-timeline.html"
    end_time=$(date +%s)
    echo "SSSOM cross-set inference took: $((end_time - start_time)) seconds"
    # nquads + infer run inside one Nextflow process graph; we record the boundary `infer`.
    record_stage infer
fi

# --- Stage: explanation (shard -> explain), ADR-0028 ---
# Component-sharded chase+trace: SHARD_CONCLUSIONS partitions assertedCorpus.nq into per-component
# shards and assigns every conclusion of inferences.ttl to one; EXPLAIN_SHARD then chases each shard's
# tiny corpus and traces all of its conclusions. Pure reasoning — no Solr — so it runs BEFORE
# index-asserted. A resume at START_STAGE=explain re-enters at `-entry from_explain_shard`, reading
# the published crossSet/shards/. The block runs whenever any explanation substage is in scope
# (START_STAGE at or before `explain`).
if should_run explain; then
    # Safe empty-array expansion (`${arr[@]+...}`) so `set -u` tolerates the full-run case.
    EXPLAIN_ENTRY_ARGS=()
    case "$START_STAGE" in
        explain) EXPLAIN_ENTRY_ARGS=(-entry from_explain_shard) ;;
        *)       EXPLAIN_ENTRY_ARGS=() ;;  # download|...|shard -> shard first, then explain
    esac
    start_time=$(date +%s)
    echo "###################" "explainSssomCrossSet (component-sharded chase+trace) entry=${START_STAGE}..."
    nextflow -log "$NXF_LOGS/explainSssomCrossSet.nextflow.log" \
        -c "$SCRIPT_DIR/nextflow/nextflow.config" \
        run "$SCRIPT_DIR/explainSssomCrossSet.nf" \
        -profile "$NF_PROFILE" \
        ${EXPLAIN_ENTRY_ARGS[@]+"${EXPLAIN_ENTRY_ARGS[@]}"} \
        -with-trace    "$NXF_LOGS/explainSssomCrossSet-trace.txt" \
        -with-report   "$NXF_LOGS/explainSssomCrossSet-report.html" \
        -with-timeline "$NXF_LOGS/explainSssomCrossSet-timeline.html"
    end_time=$(date +%s)
    echo "Component-sharded explanation took: $((end_time - start_time)) seconds"

    # Post-condition: one chain file per shard. Nextflow exits 0 when a *workflow operator* (as
    # opposed to a task) throws, so a silent zero-shard run would otherwise index every inferred
    # mapping with no explanation. Assert the invariant rather than trust the exit status. A corpus
    # with no inferences produces no shards and no chains, which satisfies 0 == 0.
    #
    # mkdir first: under `set -o pipefail`, `find` on a missing directory fails the whole pipeline,
    # and a failing command substitution in an assignment aborts under `set -e`.
    mkdir -p "$OXO2_INFERENCES/crossSet/shards" "$OXO2_INFERENCES/crossSet/shardChains"
    expected_chains=$(find "$OXO2_INFERENCES/crossSet/shards" -maxdepth 1 -name 'shard*-targets.txt' | wc -l)
    actual_chains=$(find "$OXO2_INFERENCES/crossSet/shardChains" -maxdepth 1 -name 'shard*-chains.json' | wc -l)
    if [ "$expected_chains" -ne "$actual_chains" ]; then
        echo "ERROR: explanation stage produced $actual_chains chain file(s) for $expected_chains shard(s)." >&2
        echo "       Every shard must yield a trace, or its conclusions ship unexplained." >&2
        exit 1
    fi
    echo "Explained $actual_chains shard(s)."

    # shard + explain run inside one Nextflow process graph; record both boundaries.
    record_stage shard
    record_stage explain
fi

# --- Stage: index asserted mappings to Solr ---
if should_run index-asserted; then
    start_time=$(date +%s)
    echo "###################" json2solr mappings...
    singularity exec --bind "$OXO2_DATA:$OXO2_DATA" "$SIF_IMAGE" \
        /opt/oxo/oxo2-dataload/json2solr.sh "$OXO2_DATA/sssom-as-json/mapping" "$SOLR_URL/oxo2-mappings"
    echo "###################" json2solr mappingSets...
    singularity exec --bind "$OXO2_DATA:$OXO2_DATA" "$SIF_IMAGE" \
        /opt/oxo/oxo2-dataload/json2solr.sh "$OXO2_DATA/sssom-as-json/mappingSet" "$SOLR_URL/oxo2-mappingsets"
    end_time=$(date +%s)
    echo "Writing to Solr took: $((end_time - start_time)) seconds"
    sleep 20
    verify_solr_indexed
    record_stage index-asserted
fi

# --- Stage: explained inferred mappings to JSON (SSSOM reasoning across all sets, ADR-0028) ---
# Runs after the asserted data is indexed: explanations2json resolves each inferred entity's
# CURIE/label — and every asserted premise of every chain — from the asserted Solr index, so the
# asserted mappings must already be in Solr. Each EXPLANATIONS_TO_JSON task runs as its own SLURM job
# on another node, where Solr is not on localhost, so the main node's Solr URL ($SOLR_URL, the FQDN
# the readiness probe confirmed reachable cross-node) is passed explicitly: explanations2json.nf
# otherwise defaults params.solr_url to http://localhost:8983 and exports it over any inherited
# SOLR_URL, so the task would dial its own node (ConnectException in DataloadSolr).
SHARD_CHAINS_DIR="$OXO2_INFERENCES/crossSet/shardChains"
if should_run explanations2json && [ -z "$(find "$SHARD_CHAINS_DIR" -maxdepth 1 -name '*-chains.json' -print -quit 2>/dev/null)" ]; then
    # A corpus with no inferences produces no shards and no chains. explanations2json.nf's
    # checkIfExists would throw on the empty glob, so skip it and let index-inferred find nothing.
    echo "No shard chains under $SHARD_CHAINS_DIR (no inferences); skipping explanations2json."
    record_stage explanations2json
elif should_run explanations2json; then
    start_time=$(date +%s)
    echo "###################" "explanations2json (SSSOM, cross-set, explained)..."
    (cd "$NXF_LOGS" && nextflow -log "$NXF_LOGS/explanations2jsonCrossSet.nextflow.log" \
        -c "$SCRIPT_DIR/nextflow/nextflow.config" \
        run "$SCRIPT_DIR/oxo2-json2inferences/explanations2json.nf" \
        -profile "$NF_PROFILE" \
        --shard_chains_dir "$OXO2_INFERENCES/crossSet/shardChains" \
        --inference_type SSSOM_INFERENCE \
        --solr_url "$SOLR_URL" \
        -with-trace    "$NXF_LOGS/explanations2jsonCrossSet-trace.txt" \
        -with-report   "$NXF_LOGS/explanations2jsonCrossSet-report.html" \
        -with-timeline "$NXF_LOGS/explanations2jsonCrossSet-timeline.html")
    end_time=$(date +%s)
    echo "Explained inferred mappings to JSON (SSSOM cross-set) took: $((end_time - start_time)) seconds"
    record_stage explanations2json
fi

# --- Stage: index inferred mappings and mapping sets to Solr ---
if should_run index-inferred; then
    echo "###################" json2solr inferred mappings...
    singularity exec --bind "$OXO2_DATA:$OXO2_DATA" "$SIF_IMAGE" \
        /opt/oxo/oxo2-dataload/json2solr.sh "$OXO2_INFERENCES/solr/mapping" "$SOLR_URL/oxo2-mappings"
    echo "###################" json2solr inferred mappingSets...
    singularity exec --bind "$OXO2_DATA:$OXO2_DATA" "$SIF_IMAGE" \
        /opt/oxo/oxo2-dataload/json2solr.sh "$OXO2_INFERENCES/solr/mappingSet" "$SOLR_URL/oxo2-mappingsets"
    sleep 20
    verify_solr_indexed
    record_stage index-inferred
fi

# --- Stage: derive the entity collection from the indexed mappings (ADR-0034) ---
# One document per DISTINCT entity, folded out of the denormalised mappings index and sharded by CURIE
# prefix. Runs AFTER index-inferred, not before: an inferred mapping's subject may appear only as an
# OBJECT in the asserted data, so deriving earlier would drop it from the subject-side suggest.
#
# Like explanations2json, each task is its own SLURM job on another node where Solr is not on
# localhost, so $SOLR_URL (the cross-node FQDN the readiness probe confirmed) is passed explicitly —
# mappings2entities.nf otherwise defaults params.solr_url to http://localhost:8983 and the task would
# dial its own node.
if should_run mappings2entities; then
    start_time=$(date +%s)
    echo "###################" mappings2entities...
    # Checked: this stage's failure mode is SILENT — an unchecked failure records the stage and
    # carries on, leaving an empty oxo2-entities collection, which reads as "the typeahead finds
    # nothing" rather than as a broken load.
    if ! (cd "$NXF_LOGS" && nextflow -log "$NXF_LOGS/mappings2entities.nextflow.log" \
        -c "$SCRIPT_DIR/nextflow/nextflow.config" \
        run "$SCRIPT_DIR/mappings2entities.nf" \
        -profile "$NF_PROFILE" \
        --solr_url "$SOLR_URL" \
        --output_dir "$OXO2_DATA/entities" \
        -with-trace    "$NXF_LOGS/mappings2entities-trace.txt" \
        -with-report   "$NXF_LOGS/mappings2entities-report.html" \
        -with-timeline "$NXF_LOGS/mappings2entities-timeline.html"); then
        echo "mappings2entities failed; oxo2-entities would be empty or partial. See $NXF_LOGS/mappings2entities.nextflow.log" >&2
        exit 1
    fi
    end_time=$(date +%s)
    echo "Deriving entities from the mappings index took: $((end_time - start_time)) seconds"
    record_stage mappings2entities
fi

# --- Stage: index entities to Solr ---
if should_run index-entities; then
    echo "###################" json2solr entities...
    singularity exec --bind "$OXO2_DATA:$OXO2_DATA" "$SIF_IMAGE" \
        /opt/oxo/oxo2-dataload/json2solr.sh "$OXO2_DATA/entities" "$SOLR_URL/oxo2-entities"
    sleep 20
    verify_solr_indexed oxo2-entities
    record_stage index-entities
fi

# --- Stage: archive (always the final step) ---
# Stop Solr cleanly (only if we started it) so the on-disk index is durable, then tar+pigz
# $SOLR_HOME. With START_STAGE=archive, Solr was never started and this just re-archives the
# existing $SOLR_HOME.
if [ "$SOLR_NEEDED" = true ]; then
    # Archive the Solr data only after a clean shutdown: SIGTERM lets Solr commit and close the
    # index so the on-disk state in $SOLR_HOME is durable and self-consistent. cleanup_solr (the
    # EXIT-trap handler installed above) is idempotent — SIGTERM, wait up to 30s, then SIGKILL. We
    # invoke it explicitly here so the stop happens before the tar, then disarm the trap so it does
    # not run a second time on exit.
    echo "################### Stopping Solr before archiving..."
    cleanup_solr
    trap - EXIT
fi

# Ensure Solr data is accessible
chmod -R 777 "$SOLR_HOME"/* 2>/dev/null || true

# --- Archive Solr data for the Kubernetes import volume ---
# A separate Jenkins job copies this tarball to the NFS export that the dev cluster's Solr init
# container mounts read-only and extracts into /var/solr (see
# k8chart-dev/oxo2/templates/solr-deployment.yaml). The archive holds the *contents* of $SOLR_HOME
# (created with `-C "$SOLR_HOME" .`) so that `tar -xzf ... -C /var/solr` on the cluster reproduces
# the layout. The run-local logs/ and pid/ dirs are excluded — they are meaningless in the cluster
# and only bloat the archive.
#
# Compression runs inside the Singularity image (which ships pigz) rather than on the host, so
# parallel gzip is guaranteed available. pigz uses $SLURM_CPUS_PER_TASK threads (set via
# --cpus-per-task in loadData.hpc; falls back to 1 — plain single-threaded gzip — if unset). Both
# $SOLR_HOME and $OXO2_DATA (which contains $OXO2_INFERENCES) are bound at their host paths so the
# same absolute paths resolve inside the container. The inner `set -o pipefail` makes a tar failure
# propagate through the pigz pipe rather than being masked by pigz's exit.
SOLR_ARCHIVE="$OXO2_INFERENCES/solr-data.tar.gz"
PIGZ_THREADS="${SLURM_CPUS_PER_TASK:-1}"
echo "################### Archiving Solr data to $SOLR_ARCHIVE (pigz -p $PIGZ_THREADS) ..."
archive_start_time=$(date +%s)
rm -f "$SOLR_ARCHIVE"
singularity exec \
    --bind "$SOLR_HOME:$SOLR_HOME" \
    --bind "$OXO2_DATA:$OXO2_DATA" \
    "$SIF_IMAGE" \
    bash -c "set -o pipefail; tar -cf - -C '$SOLR_HOME' --exclude=./logs --exclude=./pid . | pigz -p $PIGZ_THREADS > '$SOLR_ARCHIVE'"
archive_end_time=$(date +%s)
echo "Solr data archived in $((archive_end_time - archive_start_time)) seconds ($(du -h "$SOLR_ARCHIVE" | cut -f1))"
record_stage archive

echo "=== OXO2 HPC Dataload Complete ==="
echo "Date: $(date)"
dataload_end_time=$(date +%s)
duration=$(echo "scale=2; ($dataload_end_time - $dataload_start_time) / 3600" | bc)
echo "OxO2 Dataload took: $duration hours"
