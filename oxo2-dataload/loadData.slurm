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

# --- Start Solr as Singularity instance ---
echo "Starting Solr instance..."
mkdir -p "$SOLR_HOME/logs" "$SOLR_HOME/pid"
# Bind Jetty to 0.0.0.0 so Solr listens on every interface (loopback for in-container
# probes, internal cluster IP for cross-node Nextflow sub-jobs). Earlier attempts bound
# to $(hostname): the FQDN sometimes resolves to an IP that is not configured on any
# local interface on the allocated compute node, leaving Jetty bound nowhere reachable
# while bin/solr's localhost-based wait check still printed "Started Solr server".
# Compute nodes are firewalled, so 0.0.0.0 does not expose Solr externally.
singularity instance start \
    --bind "$SOLR_HOME:/opt/solr/server/solr" \
    --bind "$SOLR_HOME/logs:/opt/solr/server/logs" \
    --bind "$OXO2_DATA/tmp:/tmp" \
    --env "SOLR_PID_DIR=/opt/solr/server/solr/pid" \
    --env "SOLR_JETTY_HOST=0.0.0.0" \
    "$SIF_IMAGE" solr_svc

singularity exec \
    --env "SOLR_PID_DIR=/opt/solr/server/solr/pid" \
    --env "SOLR_JETTY_HOST=0.0.0.0" \
    instance://solr_svc \
    /opt/solr/bin/solr start --user-managed -Djava.io.tmpdir=/tmp

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
echo "--- listeners on :8983 (inside solr_svc container) ---"
singularity exec instance://solr_svc bash -c '
    if command -v lsof >/dev/null 2>&1; then
        lsof -iTCP:8983 -sTCP:LISTEN -P -n 2>/dev/null || echo "lsof: no listener on :8983"
    else
        echo "lsof not available in container"
    fi
' 2>/dev/null || echo "diagnostic exec failed"
echo "=== End Solr network diagnostics ==="

# --- Verify Solr is reachable; abort if not ---
# Retry up to ~120s — Solr's "Started Solr server" message uses bin/solr's localhost
# probe, which can succeed before Jetty has finished binding to the requested host.
wait_for_solr_url() {
    local url=$1
    local description=$2
    local probe_cmd=$3

    for attempt in {1..60}; do
        if eval "$probe_cmd" > /dev/null 2>&1; then
            echo "$description is ready at $url (attempt $attempt)"
            return 0
        fi
        sleep 2
    done

    echo "ERROR: $description did not become ready at $url after 120s" >&2
    return 1
}

if ! wait_for_solr_url "$SOLR_URL/admin/info/system" "Solr (in-container)" \
        "singularity exec instance://solr_svc curl -sf \"$SOLR_URL/admin/info/system\""; then
    echo "ERROR: Solr is not reachable from inside the singularity instance. Aborting." >&2
    exit 1
fi

if ! wait_for_solr_url "$SOLR_URL/admin/info/system" "Solr (via hostname)" \
        "curl -sf \"$SOLR_URL/admin/info/system\""; then
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
    count=$(singularity exec instance://solr_svc \
        curl -sf "$SOLR_URL/$core/select?q=*:*&rows=0" \
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
    count=$(singularity exec instance://solr_svc \
        curl -sf "$SOLR_URL/$core/select?q=*:*&rows=0" \
        | grep -o '"numFound":[0-9]*' | grep -o '[0-9]*')
    echo "  $core: ${count:-0} documents"
    if [ -z "$count" ] || [ "$count" -eq 0 ]; then
        echo "ERROR: No documents found in $core after indexing" >&2
        exit 1
    fi
done
echo "Solr indexing verified successfully."


# --- Stop Solr ---
echo "################### Stopping Solr..."
singularity exec --env "SOLR_PID_DIR=/opt/solr/server/solr/pid" instance://solr_svc /opt/solr/bin/solr stop || true
singularity instance stop solr_svc || true

# Ensure Solr data is accessible
chmod -R 777 "$SOLR_HOME"/* 2>/dev/null || true

echo "=== OXO2 HPC Dataload Complete ==="
echo "Date: $(date)"
dataload_end_time=$(date +%s)
duration=$(echo "scale=2; ($dataload_end_time - $dataload_start_time) / 3600" | bc)
echo "OxO2 Dataload took: $duration hours"
