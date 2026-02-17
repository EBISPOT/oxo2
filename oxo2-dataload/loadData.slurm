#!/bin/bash
#SBATCH --job-name=oxo2-dataload
#SBATCH --output=%x-%j.out
#SBATCH --error=%x-%j.err

set -euo pipefail

# SCRIPT_DIR is passed via --export from loadData.hpc (BASH_SOURCE unreliable in Slurm)
: "${SCRIPT_DIR:?SCRIPT_DIR must be set}"

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
mkdir -p "$OXO2_INFERENCES/inferredMappings" "$OXO2_INFERENCES/inferencesToTrace" "$OXO2_INFERENCES/inferenceChains" "$OXO2_INFERENCES/solr"
mkdir -p "$OXO2_DATA/tmp"

# --- Load Nextflow module ---
module load nextflow

echo "Nextflow version: $(nextflow -version 2>&1 | head -3)"

# --- Pull Singularity image ---
SIF_IMAGE="$NXF_SINGULARITY_CACHEDIR/oxo2-nextflow.sif"
if [ ! -f "$SIF_IMAGE" ]; then
    echo "Pulling Singularity image: $NF_CONTAINER"
    singularity pull "$SIF_IMAGE" "$NF_CONTAINER"
else
    echo "Using cached Singularity image: $SIF_IMAGE"
fi

# --- Copy Solr config ---
echo "###################" copySolrConfig...
singularity exec --bind "$SOLR_HOME:/opt/solr/server/solr" "$SIF_IMAGE" \
    /opt/oxo/oxo2-dataload/copySolrConfig.sh
sleep 2

# --- Start Solr as Singularity instance ---
echo "Starting Solr instance..."
mkdir -p "$SOLR_HOME/logs" "$SOLR_HOME/pid"
singularity instance start \
    --bind "$SOLR_HOME:/opt/solr/server/solr" \
    --bind "$SOLR_HOME/logs:/opt/solr/server/logs" \
    --bind "$OXO2_DATA/tmp:/tmp" \
    --env "SOLR_PID_DIR=$SOLR_HOME/pid" \
    "$SIF_IMAGE" solr_svc

singularity exec instance://solr_svc \
    /opt/solr/bin/solr start --user-managed -Djava.io.tmpdir=/tmp
sleep 10

# Verify Solr is running
if singularity exec instance://solr_svc curl -sf http://localhost:8983/solr/admin/info/system > /dev/null 2>&1; then
    echo "Solr started successfully"
else
    echo "WARNING: Solr may not be ready yet, continuing..."
fi

# --- Clean previous data ---
rm -rf "$OXO2_DATA/sssom/"* "$OXO2_DATA/sssom-as-json/"*

# --- Stage 1: Download mappings ---
start_time=$(date +%s)
echo "###################" downloadMappings...
nextflow -log "$NXF_LOGS/.nextflow.log" \
    -c "$SCRIPT_DIR/nextflow/nextflow.config" \
    run "$SCRIPT_DIR/downloadMappings.nf" \
    -profile "$NF_PROFILE"
end_time=$(date +%s)
echo "Download took: $((end_time - start_time)) seconds"

# --- Stage 2: SSSOM to JSON ---
start_time=$(date +%s)
echo "###################" sssom2json...
nextflow -log "$NXF_LOGS/.nextflow.log" \
    -c "$SCRIPT_DIR/nextflow/nextflow.config" \
    run "$SCRIPT_DIR/sssom2json.nf" \
    -profile "$NF_PROFILE"
end_time=$(date +%s)
echo "SSSOM to JSON took: $((end_time - start_time)) seconds"

# --- Stage 3: Infer and explain mappings ---
start_time=$(date +%s)
echo "###################" inferAndExplainMappings...
nextflow -log "$NXF_LOGS/.nextflow.log" \
    -c "$SCRIPT_DIR/nextflow/nextflow.config" \
    run "$SCRIPT_DIR/inferAndExplainMappings.nf" \
    -profile "$NF_PROFILE"
end_time=$(date +%s)
echo "Infer and explain took: $((end_time - start_time)) seconds"

# --- Stage 4: Index mappings to Solr ---
start_time=$(date +%s)
echo "###################" json2solr mappings...
singularity exec --bind "$OXO2_DATA:$OXO2_DATA" "$SIF_IMAGE" \
    /opt/oxo/oxo2-dataload/json2solr.sh "$OXO2_DATA/sssom-as-json/mapping" http://localhost:8983/solr/oxo2-mappings
echo "###################" json2solr mappingSets...
singularity exec --bind "$OXO2_DATA:$OXO2_DATA" "$SIF_IMAGE" \
    /opt/oxo/oxo2-dataload/json2solr.sh "$OXO2_DATA/sssom-as-json/mappingSet" http://localhost:8983/solr/oxo2-mappingsets
end_time=$(date +%s)
echo "Writing to Solr took: $((end_time - start_time)) seconds"
sleep 20

# --- Stage 5: Explanations to JSON ---
start_time=$(date +%s)
echo "###################" explanations2json...
(cd "$NXF_LOGS" && nextflow -log "$NXF_LOGS/.nextflow.log" \
    -c "$SCRIPT_DIR/nextflow/nextflow.config" \
    run "$SCRIPT_DIR/oxo2-json2inferences/explanations2json.nf" \
    -profile "$NF_PROFILE")
end_time=$(date +%s)
echo "Explanations to JSON took: $((end_time - start_time)) seconds"

# --- Stage 6: Index inferred mappings to Solr ---
echo "###################" json2solr inferred mappings...
singularity exec --bind "$OXO2_DATA:$OXO2_DATA" "$SIF_IMAGE" \
    /opt/oxo/oxo2-dataload/json2solr.sh "$OXO2_INFERENCES/solr" http://localhost:8983/solr/oxo2-mappings
sleep 20

# --- Stop Solr ---
echo "################### Stopping Solr..."
singularity exec instance://solr_svc /opt/solr/bin/solr stop || true
singularity instance stop solr_svc || true

# Ensure Solr data is accessible
chmod -R 777 "$SOLR_HOME"/* 2>/dev/null || true

echo "=== OXO2 HPC Dataload Complete ==="
echo "Date: $(date)"
