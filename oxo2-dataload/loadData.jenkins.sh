#!/bin/bash
# Login-node orchestration wrapper for the OXO2 HPC dataload. A Jenkins Freestyle job runs this on the
# login node via the "ssh" plugin's "Execute shell script on remote host using ssh" build step,
# pointed at a Jenkins-global SSH site (host + HPC user + key live there, not in the repo) — the same
# SSH site the solr-data.tar.gz copy job uses. The build step exports the job parameters (START_STAGE,
# OXO2_DATALOAD_DIR, OXO2_CONFIG, NF_CONTAINER, HPC_*, POLL_INTERVAL) and invokes this wrapper. It
# submits the dataload via loadData.hpc, then BLOCKS — polling Slurm and streaming the job's log to
# stdout (which the ssh build step captures into the Jenkins console) — and exits with the job's final
# status, so the Jenkins build passes iff the dataload COMPLETED. See oxo2-dataload/CONTEXT.md
# § Resumable dataload for the Freestyle job configuration.
#
# Resume: set START_STAGE (download|sssom2json|nquads|infer|index-asserted|inferences2json|
# index-inferred|archive). Default download = full run. The nquads/infer substages map to `-entry`
# points in inferSssomCrossSet.nf (explanations are deferred to on-demand, ADR-0020). See
# oxo2-dataload/CONTEXT.md § Resumable dataload.
#
# Reattach: if a previous invocation's job for this checkout is still active (its id recorded in
# $CURRENT_JOBID_FILE), this wrapper tails+polls THAT job instead of submitting a new one — so a
# dropped SSH session (the Jenkins build died, not the Slurm job) is recoverable by simply
# re-running the Jenkins job.
#
# This wrapper does NOT copy the resulting solr-data.tar.gz anywhere or deploy: it stops at
# producing the archive. The copy-to-NFS + Kubernetes redeploy is a separate Jenkins job.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
START_STAGE="${START_STAGE:-download}"
POLL_INTERVAL="${POLL_INTERVAL:-30}"

echo "=== OXO2 dataload (Jenkins -> HPC) ==="
echo "Host        : $(hostname)"
echo "Date        : $(date)"
echo "START_STAGE : $START_STAGE"
echo "Script dir  : $SCRIPT_DIR"

# Where loadData.hpc writes its Slurm logs (kept in sync with loadData.hpc's NFS_PATH/SLURM_LOGS).
NFS_PATH="${NFS_PATH:-/nfs/production/parkinso/spot/oxo2/dev}"
SLURM_LOGS="${SLURM_LOGS:-$NFS_PATH/logs}"
CURRENT_JOBID_FILE="$SLURM_LOGS/oxo2-dataload.current-jobid"

job_active() {
    local job_id=$1
    [ -n "$job_id" ] || return 1
    squeue -h -j "$job_id" -o '%T' 2>/dev/null | grep -q .
}

JOB_ID=""
JOB_OUT=""

# --- Reattach to an already-active job, or submit a new one ---
if [ -f "$CURRENT_JOBID_FILE" ] && job_active "$(cat "$CURRENT_JOBID_FILE" 2>/dev/null || true)"; then
    JOB_ID=$(cat "$CURRENT_JOBID_FILE")
    JOB_OUT="$SLURM_LOGS/oxo2-dataload-$JOB_ID.out"
    echo "Reattaching to already-active job $JOB_ID (recorded in $CURRENT_JOBID_FILE)."
else
    echo "Submitting dataload via loadData.hpc..."
    submit_out=$(START_STAGE="$START_STAGE" "$SCRIPT_DIR/loadData.hpc")
    printf '%s\n' "$submit_out"
    JOB_ID=$(printf '%s\n' "$submit_out" | sed -n 's/^OXO2_JOB_ID=\([0-9][0-9]*\)$/\1/p' | tail -1)
    JOB_OUT=$(printf '%s\n' "$submit_out" | sed -n 's/^OXO2_JOB_OUT=\(.*\)$/\1/p' | tail -1)
    if [ -z "$JOB_ID" ]; then
        echo "ERROR: could not determine the submitted Slurm job id from loadData.hpc output." >&2
        exit 1
    fi
    mkdir -p "$SLURM_LOGS"
    echo "$JOB_ID" > "$CURRENT_JOBID_FILE"
fi

echo "Tracking Slurm job $JOB_ID; log: ${JOB_OUT:-<unknown>}"

# --- Stream the job's log to our stdout while it runs ---
TAIL_PID=""
start_tail() {
    [ -n "$JOB_OUT" ] || return 0
    # Wait briefly for Slurm to create the .out file, then follow it from the top.
    local _
    for _ in {1..30}; do [ -f "$JOB_OUT" ] && break; sleep 2; done
    if [ -f "$JOB_OUT" ]; then
        tail -n +1 -F "$JOB_OUT" &
        TAIL_PID=$!
    fi
}
stop_tail() {
    if [ -n "$TAIL_PID" ]; then
        kill "$TAIL_PID" 2>/dev/null || true
        TAIL_PID=""
    fi
}
trap stop_tail EXIT
start_tail

# --- Poll until the job leaves the queue, then read its terminal state ---
final_state=""
while squeue -h -j "$JOB_ID" -o '%T' 2>/dev/null | grep -q .; do
    sleep "$POLL_INTERVAL"
done

# sacct can lag a few seconds behind squeue, so retry briefly before giving up. Prefer the
# .batch step's state (the actual script's exit), falling back to the job-level state.
for _ in {1..10}; do
    final_state=$(sacct -j "${JOB_ID}.batch" --noheader --format=State%30 2>/dev/null \
        | head -1 | awk '{print $1}')
    if [ -z "$final_state" ]; then
        final_state=$(sacct -j "$JOB_ID" --noheader --format=State%30 2>/dev/null \
            | head -1 | awk '{print $1}')
    fi
    [ -n "$final_state" ] && break
    sleep 3
done

# Let the tail flush the final lines, then stop it.
sleep 2
stop_tail
trap - EXIT

echo "=== Slurm job $JOB_ID final state: ${final_state:-UNKNOWN} ==="
case "$final_state" in
    COMPLETED)
        rm -f "$CURRENT_JOBID_FILE"
        echo "OXO2 dataload COMPLETED. solr-data.tar.gz is ready for the deploy job."
        exit 0
        ;;
    *)
        echo "OXO2 dataload did NOT complete cleanly (state: ${final_state:-UNKNOWN})." >&2
        echo "Inspect ${JOB_OUT:-the Slurm logs under $SLURM_LOGS}, then resume by re-running this" >&2
        echo "job with START_STAGE set to the last completed stage (the run writes that name to" >&2
        echo ".oxo2-last-completed-stage in the data dir)." >&2
        exit 1
        ;;
esac
