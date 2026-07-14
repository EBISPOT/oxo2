#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

: "${SOLR_HOME:?SOLR_HOME must be set. Please set the environment variable and retry.}"

# Modes:
#   all            (default) wipe and lay down every core's config. Destructive: the caller must
#                  already have decided the index is being rebuilt from scratch (should_wipe_solr).
#   entities-only  lay down ONLY the oxo2-entities core, and only if it is not already there.
#                  Never wipes. This exists because oxo2-entities (ADR-0034) is a read model that can
#                  be rebuilt on its own with START_STAGE=mappings2entities against an already-indexed
#                  oxo2-mappings — but that path does NOT wipe Solr, so `all` never runs, and without
#                  this the core directory would never be created and wait_for_solr_core would hang.
MODE="${1:-all}"

mkdir -p "$SOLR_HOME"

copy_core() {
    local core=$1
    cp -r "$SCRIPT_DIR/solr-config/$core" "$SOLR_HOME/$core"
}

if [ "$MODE" = "entities-only" ]; then
    if [ -d "$SOLR_HOME/oxo2-entities" ]; then
        echo "oxo2-entities core config already present; leaving it (and any indexed data) alone."
        exit 0
    fi
    echo "Laying down the oxo2-entities core config (preserving the existing cores)..."
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
