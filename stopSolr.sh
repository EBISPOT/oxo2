#!/bin/bash
# Stop the local Solr selected by the same environment as startSolr.sh.
# Source oxo2-env.sh from the intended worktree first (or let direnv load it).
set -euo pipefail

if [ "$#" -ne 0 ]; then
    echo "Usage: $0 (configure SOLR_SCRIPT and SOLR_PORT or SOLR_URL in the environment)" >&2
    exit 1
fi

# Match startSolr.sh: explicit SOLR_PORT, then SOLR_URL's port, then 8983.
if [ -z "${SOLR_PORT:-}" ]; then
    SOLR_PORT=$(printf '%s\n' "${SOLR_URL:-}" | sed -nE 's|^[a-zA-Z][a-zA-Z0-9+.-]*://[^:/]+:([0-9]+).*|\1|p')
    SOLR_PORT=${SOLR_PORT:-8983}
fi
if [[ ! "$SOLR_PORT" =~ ^[0-9]{1,5}$ ]] || (( 10#$SOLR_PORT < 1 || 10#$SOLR_PORT > 65535 )); then
    echo "SOLR_PORT must be a port number between 1 and 65535." >&2
    exit 1
fi
export SOLR_PORT

if [ -z "${SOLR_SCRIPT:-}" ]; then
    echo "SOLR_SCRIPT is not set (expected the Solr bin directory, e.g. /path/to/solr/bin)." >&2
    echo "Source oxo2-env.sh, or see CLAUDE.md § Running Locally." >&2
    exit 1
fi
if [ ! -x "$SOLR_SCRIPT/solr" ]; then
    echo "No executable solr launcher at $SOLR_SCRIPT/solr" >&2
    exit 1
fi

# Always specify the port: never let the launcher select another worktree's instance.
# Inherit SOLR_PID_DIR and other launcher settings, and preserve its exit status.
echo "Stopping local Solr on port $SOLR_PORT"
exec "$SOLR_SCRIPT/solr" stop -p "$SOLR_PORT"
