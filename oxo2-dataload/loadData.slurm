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

OXO2_INFERENCES=$OXO2_DATA/inferences
export OXO2_INFERENCES

mkdir -p "$NXF_WORK" "$NXF_HOME" "$NXF_TEMP" "$NXF_CACHE_DIR" "$NXF_SINGULARITY_CACHEDIR" "$NXF_LOGS"
mkdir -p "$OXO2_DATA/sssom" "$OXO2_DATA/sssom-as-json" "$OXO2_DATA/assertedMappings"
mkdir -p "$OXO2_INFERENCES/inferredMappings" "$OXO2_INFERENCES/inferencesToTrace" "$OXO2_INFERENCES/inferenceChains" "$OXO2_INFERENCES/solr/mapping" "$OXO2_INFERENCES/solr/mappingSet"
mkdir -p "$OXO2_DATA/tmp"
mkdir -p "$NEXTFLOW_DIR/logs"
mkdir -p "$SOLR_HOME"

# --- Clean directories from previous runs ---
# (NXF_SINGULARITY_CACHEDIR excluded — only updated when a new image is available)
rm -rf "$NXF_WORK"/*
rm -rf "$NXF_HOME"/*
rm -rf "$NXF_TEMP"/*
rm -rf "$NXF_CACHE_DIR"/*
#rm -rf "$NXF_SINGULARITY_CACHEDIR"/*
rm -rf "$NXF_LOGS"/* "$NXF_LOGS"/.*[!.]*
rm -rf "$OXO2_DATA/sssom"/*
rm -rf "$OXO2_DATA/sssom-as-json"/*
rm -rf "$OXO2_DATA/assertedMappings"/*
rm -rf "$OXO2_INFERENCES/inferredMappings"/*
rm -rf "$OXO2_INFERENCES/inferencesToTrace"/*
rm -rf "$OXO2_INFERENCES/inferenceChains"/*
rm -rf "$OXO2_INFERENCES/solr"/*
rm -rf "$OXO2_DATA/tmp"/*
rm -rf "$SOLR_HOME"/*

# --- Load Nextflow module ---
module load nextflow

echo "Nextflow version: $(nextflow -version 2>&1 | head -3)"

# --- Pull Singularity image (update if newer version available) ---
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

# --- Copy Solr config ---
echo "###################" copySolrConfig...
singularity exec --bind "$SOLR_HOME:/opt/solr/server/solr" "$SIF_IMAGE" \
    /opt/oxo/oxo2-dataload/copySolrConfig.sh
sleep 2

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

# --- Stage 1: Download mappings ---
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

# --- Stage 2: SSSOM to JSON ---
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

# --- Stage 3: Infer and explain mappings ---
start_time=$(date +%s)
echo "###################" inferAndExplainMappings...
nextflow -log "$NXF_LOGS/inferAndExplainMappings.nextflow.log" \
    -c "$SCRIPT_DIR/nextflow/nextflow.config" \
    run "$SCRIPT_DIR/inferAndExplainMappings.nf" \
    -profile "$NF_PROFILE" \
    -with-trace    "$NXF_LOGS/inferAndExplainMappings-trace.txt" \
    -with-report   "$NXF_LOGS/inferAndExplainMappings-report.html" \
    -with-timeline "$NXF_LOGS/inferAndExplainMappings-timeline.html"
end_time=$(date +%s)
echo "Infer and explain took: $((end_time - start_time)) seconds"

# --- Stage 4: Index mappings to Solr ---
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

# --- Verify Stage 4: Check documents were indexed ---
echo "Verifying Solr indexing..."
for core in oxo2-mappings oxo2-mappingsets; do
    count=$(curl -sf "$SOLR_URL/$core/select?q=*:*&rows=0" \
        | grep -o '"numFound":[0-9]*' | grep -o '[0-9]*')
    echo "  $core: ${count:-0} documents"
    if [ -z "$count" ] || [ "$count" -eq 0 ]; then
        echo "ERROR: No documents found in $core after indexing" >&2
        exit 1
    fi
done
echo "Solr indexing verified successfully."

# --- Stage 5: Explanations to JSON ---
start_time=$(date +%s)
echo "###################" explanations2json...
(cd "$NXF_LOGS" && nextflow -log "$NXF_LOGS/explanations2json.nextflow.log" \
    -c "$SCRIPT_DIR/nextflow/nextflow.config" \
    run "$SCRIPT_DIR/oxo2-json2inferences/explanations2json.nf" \
    -profile "$NF_PROFILE" \
    --solr_url "$SOLR_URL" \
    -with-trace    "$NXF_LOGS/explanations2json-trace.txt" \
    -with-report   "$NXF_LOGS/explanations2json-report.html" \
    -with-timeline "$NXF_LOGS/explanations2json-timeline.html")
end_time=$(date +%s)
echo "Explanations to JSON took: $((end_time - start_time)) seconds"

# --- Stage 6: Index inferred mappings and mapping sets to Solr ---
echo "###################" json2solr inferred mappings...
singularity exec --bind "$OXO2_DATA:$OXO2_DATA" "$SIF_IMAGE" \
    /opt/oxo/oxo2-dataload/json2solr.sh "$OXO2_INFERENCES/solr/mapping" "$SOLR_URL/oxo2-mappings"
echo "###################" json2solr inferred mappingSets...
singularity exec --bind "$OXO2_DATA:$OXO2_DATA" "$SIF_IMAGE" \
    /opt/oxo/oxo2-dataload/json2solr.sh "$OXO2_INFERENCES/solr/mappingSet" "$SOLR_URL/oxo2-mappingsets"
sleep 20

# --- Verify Stage 7: Check inferred documents were indexed ---
echo "Verifying Solr indexing..."
for core in oxo2-mappings oxo2-mappingsets; do
    count=$(curl -sf "$SOLR_URL/$core/select?q=*:*&rows=0" \
        | grep -o '"numFound":[0-9]*' | grep -o '[0-9]*')
    echo "  $core: ${count:-0} documents"
    if [ -z "$count" ] || [ "$count" -eq 0 ]; then
        echo "ERROR: No documents found in $core after indexing" >&2
        exit 1
    fi
done
echo "Solr indexing verified successfully."


# --- Stop Solr ---
# Cleanup is handled by the EXIT trap (cleanup_solr) installed near the top of the
# script — it sends SIGTERM, waits up to 30s, then SIGKILL. No explicit stop needed
# here, and explicitly running it would race with the trap.
echo "################### Solr stop handled by EXIT trap"

# Ensure Solr data is accessible
chmod -R 777 "$SOLR_HOME"/* 2>/dev/null || true

echo "=== OXO2 HPC Dataload Complete ==="
echo "Date: $(date)"
dataload_end_time=$(date +%s)
duration=$(echo "scale=2; ($dataload_end_time - $dataload_start_time) / 3600" | bc)
echo "OxO2 Dataload took: $duration hours"
