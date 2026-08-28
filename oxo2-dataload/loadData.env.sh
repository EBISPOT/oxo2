#!/bin/bash
# Shared environment derivation for the OXO2 HPC dataload entry points (ADR-0050).
#
# Sourced (never executed) by loadData.hpc, loadData.jenkins.sh, and cleanup.hpc, so all three
# agree on where an environment lives — the same anti-drift role loadData.lib.sh plays for the
# stage contract. OXO2_ENV selects the value-set; every derived variable is a `${VAR:-...}`
# default, so an explicitly exported variable (e.g. a Jenkins job parameter such as OXO2_CONFIG
# or NF_CONTAINER) beats the derivation: OXO2_ENV picks the set, a named variable overrides a
# member.
#
# The environments are deliberately a whitelist — an unknown value must fail here rather than
# mkdir (or worse, `cleanup.hpc`-delete) a mistyped tree or pull an unintended image tag:
#
#   dev  (default) — /nfs|/hps .../oxo2/dev,  images tagged :dev    (built from the dev branch)
#   prod           — /nfs|/hps .../oxo2/prod, images tagged :stable (built from the stable branch)
#
# Each environment's NFS tree holds a checkout of its branch at $NFS_PATH/oxo2, so the config
# default resolves to the config version that matches the code actually running. See
# docs/adr/0050-production-data-release-channel.md and docs/hpc-data-release.md § Environments.

OXO2_ENV="${OXO2_ENV:-dev}"

case "$OXO2_ENV" in
    dev)  OXO2_IMAGE_TAG="dev" ;;
    prod) OXO2_IMAGE_TAG="stable" ;;
    *)
        echo "ERROR: unknown OXO2_ENV '$OXO2_ENV' (expected 'dev' or 'prod')" >&2
        return 1 2>/dev/null || exit 1
        ;;
esac

NFS_PATH="${NFS_PATH:-/nfs/production/parkinso/spot/oxo2/$OXO2_ENV}"
HPS_PATH="${HPS_PATH:-/hps/nobackup/parkinso/spot/oxo2/$OXO2_ENV}"
SLURM_LOGS="${SLURM_LOGS:-$NFS_PATH/logs}"
NEXTFLOW_DIR="${NEXTFLOW_DIR:-$HPS_PATH/nextflow}"
NF_CONTAINER="${NF_CONTAINER:-docker://ghcr.io/ebispot/oxo2-nextflow:$OXO2_IMAGE_TAG}"
OXO2_CONFIG="${OXO2_CONFIG:-$NFS_PATH/oxo2/oxo-config.json}"
OXO2_DATA="${OXO2_DATA:-$HPS_PATH/data}"
SOLR_HOME="${SOLR_HOME:-$HPS_PATH/solr-data}"

# Exported so a sourcing wrapper's child processes (loadData.jenkins.sh invokes loadData.hpc)
# see the same values it resolved, overrides included.
export OXO2_ENV NFS_PATH HPS_PATH SLURM_LOGS NEXTFLOW_DIR NF_CONTAINER OXO2_CONFIG OXO2_DATA SOLR_HOME
