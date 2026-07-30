#!/bin/bash
# Shared resumable-pipeline contract for the OxO2 dataload orchestrators (ADR-0019).
#
# Both loadData.nextflow (local / integration) and loadData.slurm (HPC) SOURCE this file so they
# share one definition of:
#   - the ordered (sub)stage list and the "is stage X at or after START_STAGE?" test,
#   - the stage-aware artifact cleanup (wipe only START_STAGE and later; preserve earlier stages'
#     outputs as the resume inputs),
#   - the resume checkpoint file, and
#   - the Solr wipe / Solr needed decisions.
#
# What stays in each caller (because it is environment-specific): the stage *bodies* (local runs
# `nextflow` on the host; HPC wraps each step in `singularity exec` / `module load`), the transient
# Nextflow work-dir wipe, and the Solr lifecycle (local `$SOLR_SCRIPT/solr` + copySolrConfig.sh vs
# HPC's singularity foreground-and-background dance). This file owns the contract, not the mechanism.
#
# Contract for callers: export OXO2_DATA, OXO2_INFERENCES and START_STAGE BEFORE sourcing — the
# STAGE_OWNS path map is expanded at source time. An invalid START_STAGE aborts the calling script.
#
# See oxo2-dataload/CONTEXT.md § Resumable dataload.

: "${OXO2_DATA:?OXO2_DATA must be set before sourcing loadData.lib.sh}"
: "${OXO2_INFERENCES:?OXO2_INFERENCES must be set before sourcing loadData.lib.sh}"

START_STAGE="${START_STAGE:-download}"

# Canonical ordered (sub)stage list. A stage's position here defines "before"/"after". `archive` is
# HPC-only (it writes solr-data.tar.gz); loadData.nextflow has no body for it, so locally the archive
# stage is a no-op and its owned path simply never exists. Keeping it in the one canonical list lets
# cleanup and should_run reason over a single sequence for both orchestrators.
OXO2_STAGES=(download sssom2json nquads infer shard explain \
             index-asserted explanations2json index-inferred \
             mappings2entities index-entities archive)

# Each stage "owns" the artifact path(s) it (re)generates. On a run starting at START_STAGE we wipe
# the owned paths of START_STAGE and every later stage, and PRESERVE everything earlier stages
# produced (those are the inputs the resume builds on). Paths must not contain spaces.
declare -A OXO2_STAGE_OWNS=(
    [download]="$OXO2_DATA/sssom"
    [sssom2json]="$OXO2_DATA/sssom-as-json"
    [nquads]="$OXO2_DATA/assertedMappings $OXO2_INFERENCES/crossSet/assertedCorpus.nq"
    [infer]="$OXO2_INFERENCES/crossSet/inferences.ttl"
    [shard]="$OXO2_INFERENCES/crossSet/shards"
    [explain]="$OXO2_INFERENCES/crossSet/shardChains"
    [index-asserted]=""
    [explanations2json]="$OXO2_INFERENCES/solr"
    [index-inferred]=""
    [mappings2entities]="$OXO2_DATA/entities"
    [index-entities]=""
    [archive]="$OXO2_INFERENCES/solr-data.tar.gz"
)

# Checkpoint: the name of the last (sub)stage that completed this/last run. Written after each stage;
# an operator (or Jenkins) reads it to choose START_STAGE for a resume. It lives outside every
# stage's owned paths, so stage-aware cleanup never removes it.
OXO2_CHECKPOINT_FILE="$OXO2_DATA/.oxo2-last-completed-stage"

# oxo2_stage_index <stage>: echo the stage's position in OXO2_STAGES (0-based); non-zero on unknown.
oxo2_stage_index() {
    local target_stage=$1 current_index=0 stage_name
    for stage_name in "${OXO2_STAGES[@]}"; do
        if [ "$stage_name" = "$target_stage" ]; then echo "$current_index"; return 0; fi
        current_index=$((current_index + 1))
    done
    echo "ERROR: unknown stage '$target_stage'. Valid stages: ${OXO2_STAGES[*]}" >&2
    return 1
}

# Validate START_STAGE and cache its index for should_run / the Solr decisions.
if ! OXO2_START_INDEX=$(oxo2_stage_index "$START_STAGE"); then
    exit 1
fi

# should_run <stage>: true when <stage> is at or after START_STAGE.
should_run() {
    local stage_idx
    stage_idx=$(oxo2_stage_index "$1") || exit 1
    [ "$stage_idx" -ge "$OXO2_START_INDEX" ]
}

# record_stage <stage>: checkpoint that <stage> completed.
record_stage() {
    mkdir -p "$OXO2_DATA"
    echo "$1" > "$OXO2_CHECKPOINT_FILE"
    echo "=== STAGE COMPLETE: $1 (checkpoint -> $OXO2_CHECKPOINT_FILE) ==="
}

# Data release date (ADR-0043): the UTC instant identifying the corpus this run produces. Like the
# checkpoint it lives outside every stage's owned paths, so stage-aware cleanup never removes it.
OXO2_RELEASE_DATE_FILE="$OXO2_DATA/.oxo2-data-release-date"

# oxo2_resolve_release_date: set and export OXO2_RELEASE_DATE, the single timestamp every mapping set
# of this run is stamped with (ADR-0043). Called by both orchestrators after sourcing this file.
#
# The value is minted by the stage that writes it: sssom2json is what stamps the mapping-set JSON, so
# a run that re-runs sssom2json mints a fresh timestamp and persists it, while a resume entering
# LATER reuses the persisted one — the mapping-set JSON on disk already carries that value, and
# minting a new one would claim a release date the indexed data does not have. An operator can pin
# the value by exporting OXO2_RELEASE_DATE beforehand (a reproducible rebuild of a known release).
oxo2_resolve_release_date() {
    if [ -n "${OXO2_RELEASE_DATE:-}" ]; then
        echo "Data release date pinned by the environment: $OXO2_RELEASE_DATE"
    elif should_run sssom2json; then
        OXO2_RELEASE_DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
        mkdir -p "$OXO2_DATA"
        echo "$OXO2_RELEASE_DATE" > "$OXO2_RELEASE_DATE_FILE"
        echo "Data release date minted for this run: $OXO2_RELEASE_DATE"
    elif [ -s "$OXO2_RELEASE_DATE_FILE" ]; then
        OXO2_RELEASE_DATE=$(cat "$OXO2_RELEASE_DATE_FILE")
        echo "Data release date reused from $OXO2_RELEASE_DATE_FILE: $OXO2_RELEASE_DATE"
    else
        # A resume past sssom2json against data loaded before this file existed. The mapping-set JSON
        # carries no timestamp and this run will not rewrite it, so leave the value empty rather than
        # inventing one: an absent release date is honest, a fabricated one is not.
        OXO2_RELEASE_DATE=""
        echo "No data release date available (no $OXO2_RELEASE_DATE_FILE and sssom2json is not re-running)."
    fi
    export OXO2_RELEASE_DATE
}

# should_wipe_solr: true when the asserted load runs from scratch (START_STAGE at or before
# index-asserted). Resuming after that preserves the already-indexed asserted data, which
# explanations2json queries for entity details and asserted premises. Callers gate BOTH the on-disk
# index wipe AND the Solr-config copy (copySolrConfig.sh deletes the core dirs) on this — gating the
# config copy on anything looser would re-wipe the asserted cores on a resume.
should_wipe_solr() {
    [ "$OXO2_START_INDEX" -le "$(oxo2_stage_index index-asserted)" ]
}

# solr_needed: true when the run reaches a stage that talks to Solr (index-asserted /
# explanations2json / index-inferred / mappings2entities / index-entities). START_STAGE=archive
# re-archives an existing $SOLR_HOME without starting Solr. The shard/explain stages are pure
# reasoning and need no Solr, but they precede index-asserted, so a run entering at or before them
# still reaches the Solr stages. mappings2entities READS the mappings index (ADR-0034), so it needs
# Solr up just as much as the indexing stages do — hence index-entities, not index-inferred, is the
# last stage in this window.
solr_needed() {
    [ "$OXO2_START_INDEX" -le "$(oxo2_stage_index index-entities)" ]
}

# oxo2_clean_owned_artifacts: remove the artifacts owned by START_STAGE and every later stage,
# preserving everything earlier stages produced. Transient Nextflow dirs and the Solr index/cores
# are handled by the caller (environment-specific).
oxo2_clean_owned_artifacts() {
    local stage_name owned_path
    echo "Removing artifacts owned by stages at or after '$START_STAGE'..."
    for stage_name in "${OXO2_STAGES[@]}"; do
        if should_run "$stage_name"; then
            for owned_path in ${OXO2_STAGE_OWNS[$stage_name]}; do
                if [ -n "$owned_path" ]; then
                    echo "  rm -rf $owned_path"
                    rm -rf "$owned_path"
                fi
            done
        fi
    done
}
