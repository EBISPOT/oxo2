#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

: "${SOLR_HOME:?SOLR_HOME must be set. Please set the environment variable and retry.}"

# Modes:
#   all            (default) wipe and lay down every core's config. Destructive: the caller must
#                  already have decided the index is being rebuilt from scratch (should_wipe_solr).
#   entities-only  wipe and lay down ONLY the oxo2-entities core, leaving oxo2-mappings and
#                  oxo2-mappingsets (and their data) untouched.
#
#                  This exists because oxo2-entities (ADR-0034) is a read model rebuilt on its own
#                  with START_STAGE=mappings2entities against an already-indexed oxo2-mappings — a
#                  path that does NOT wipe Solr, so `all` never runs, and the core directory would
#                  otherwise never be created and wait_for_solr_core would hang.
#
#                  It WIPES rather than skipping an existing core, because the run that follows
#                  rebuilds the collection in full from oxo2-mappings. Skipping would (a) keep the old
#                  managed-schema, so a new entity field — the ADR-0035 predicate buckets, say — would
#                  never exist and the suggest would query a field Solr does not have; and (b) leave
#                  entities behind that the current mappings no longer derive. Wiping a read model
#                  costs nothing: everything in it is a pure function of oxo2-mappings.
MODE="${1:-all}"

mkdir -p "$SOLR_HOME"

copy_core() {
    local core=$1
    cp -r "$SCRIPT_DIR/solr-config/$core" "$SOLR_HOME/$core"
}

if [ "$MODE" = "entities-only" ]; then
    # Solr must be DOWN here: both callers invoke this before `solr start`.
    echo "Rebuilding the oxo2-entities core config (preserving oxo2-mappings and oxo2-mappingsets)..."
    rm -rf "$SOLR_HOME/oxo2-entities"
    copy_core oxo2-entities
    exit 0
fi

if [ "$MODE" != "all" ]; then
    echo "ERROR: unknown mode '$MODE'. Valid modes: all, entities-only" >&2
    exit 1
fi

# Delete previous config
rm -rf "$SOLR_HOME/oxo2-mappings" "$SOLR_HOME/oxo2-mappingsets" "$SOLR_HOME/oxo2-entities"

# Copy config
cp "$SCRIPT_DIR/solr-config/solr.xml" "$SOLR_HOME/solr.xml"
cp "$SCRIPT_DIR/solr-config/zoo.cfg" "$SOLR_HOME/zoo.cfg"
copy_core oxo2-mappings
copy_core oxo2-mappingsets
copy_core oxo2-entities
