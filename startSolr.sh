#!/bin/bash
# Start the local Solr that serves an already-loaded OxO2 index, and verify it is actually usable
# before returning.
#
# loadData.nextflow stops Solr at the end of a SUCCESSFUL run, so Solr has to be started by hand
# before ./startBackend.sh every time. This script exists so that hand-start is not a footgun:
#
#   * --user-managed is mandatory. Solr 10 FLIPPED the default: under Solr 9 `solr start` meant
#     standalone and `-c` opted into SolrCloud, whereas under Solr 10 cloud is the default and
#     --user-managed opts into standalone. A plain `solr start` therefore now launches SolrCloud
#     with an embedded ZooKeeper. OxO2's cores are plain on-disk cores with no collection state in
#     ZooKeeper, so in cloud mode every one of them fails to load with "No coreNodeName for
#     CoreDescriptor" — while /admin/info/system still answers 200. The stack looks up and every
#     query 500s.
#
#   * SOLR_HEAP defaults to 4g. Solr's launcher ignores JAVA_OPTS and otherwise defaults to a
#     512 MB heap, which is too small for the full-index same-SPO collapse used by result views
#     (ADR-0023). loadData.nextflow makes the same default.
#
#   * The cores are probed after start, so a Solr that came up in the wrong mode or with a broken
#     core fails HERE, loudly, instead of silently poisoning the backend.
#
# Ports come from the environment (SOLR_PORT / SOLR_URL, both set by oxo2-env.sh), so per-worktree
# stacks start on their own port without editing anything.

SCRIPT_PATH=$(dirname "$(readlink -f "$0")")

OXO2_CORES=(oxo2-mappings oxo2-mappingsets oxo2-entities)

# bin/solr reads SOLR_PORT natively, so honour an explicit one first; otherwise take the port out of
# SOLR_URL the way loadData.nextflow does, so a checkout that only sets SOLR_URL still works.
if [ -z "$SOLR_PORT" ]; then
    SOLR_PORT=$(printf '%s\n' "$SOLR_URL" | sed -nE 's|^[a-zA-Z][a-zA-Z0-9+.-]*://[^:/]+:([0-9]+).*|\1|p')
    SOLR_PORT=${SOLR_PORT:-8983}
fi
SOLR_URL=${SOLR_URL:-http://localhost:$SOLR_PORT/solr}
export SOLR_PORT

# A SOLR_URL pointing somewhere other than the port we are about to bind means the backend and the
# dataload will query a Solr this script did not start. Warn rather than abort: pointing SOLR_URL at
# a remote Solr while starting a local one is unusual but not illegitimate.
SOLR_URL_PORT=$(printf '%s\n' "$SOLR_URL" | sed -nE 's|^[a-zA-Z][a-zA-Z0-9+.-]*://[^:/]+:([0-9]+).*|\1|p')
if [ -n "$SOLR_URL_PORT" ] && [ "$SOLR_URL_PORT" != "$SOLR_PORT" ]; then
    echo "WARNING: starting Solr on port $SOLR_PORT but SOLR_URL=$SOLR_URL targets port $SOLR_URL_PORT." >&2
    echo "         The backend will query $SOLR_URL, not the instance this script starts." >&2
fi

if [ -z "$SOLR_SCRIPT" ]; then
    echo "SOLR_SCRIPT is not set (expected the Solr bin directory, e.g. /path/to/solr/bin)." >&2
    echo "Source oxo2-env.sh, or see CLAUDE.md § Running Locally." >&2
    exit 1
fi
if [ ! -x "$SOLR_SCRIPT/solr" ]; then
    echo "No executable solr launcher at $SOLR_SCRIPT/solr" >&2
    exit 1
fi
if [ -z "$SOLR_HOME" ]; then
    echo "SOLR_HOME is not set (expected the Solr data directory holding the OxO2 cores)." >&2
    echo "Source oxo2-env.sh, or see CLAUDE.md § Running Locally." >&2
    exit 1
fi
if [ ! -d "$SOLR_HOME" ]; then
    echo "SOLR_HOME=$SOLR_HOME is not a directory." >&2
    exit 1
fi

# An empty SOLR_HOME means no dataload has laid the cores down yet. Solr would start perfectly
# happily with zero cores and the failure would surface later as empty search results.
for core_name in "${OXO2_CORES[@]}"; do
    if [ ! -f "$SOLR_HOME/$core_name/core.properties" ]; then
        echo "No core at $SOLR_HOME/$core_name (missing core.properties)." >&2
        echo "Run ./oxo2-dataload/loadData.nextflow first; see CLAUDE.md § Running Locally." >&2
        exit 1
    fi
done

# solr_mode: echo the running instance's mode ("std" for user-managed, "solrcloud" for cloud), or
# nothing when Solr does not answer. Parsed with grep to avoid depending on a JSON tool.
solr_mode() {
    curl -fsS -m 10 "$SOLR_URL/admin/info/system?wt=json" 2>/dev/null \
        | grep -o '"mode":"[^"]*"' | head -1 | cut -d'"' -f4
}

# report_wrong_mode: explain a cloud-mode instance and how to get out of it.
report_wrong_mode() {
    echo "Solr on port $SOLR_PORT is running in SolrCloud mode, not user-managed mode." >&2
    echo "OxO2's cores have no collection state in ZooKeeper, so they cannot load in cloud mode." >&2
    echo "Stop it and start it with this script:" >&2
    echo "    \$SOLR_SCRIPT/solr stop -p $SOLR_PORT && $SCRIPT_PATH/startSolr.sh" >&2
}

# Starting a second Solr on a bound port fails with "port already in use", so handle the
# already-running case explicitly: healthy is a no-op, wrong-mode is the actionable error.
RUNNING_MODE=$(solr_mode)
if [ -n "$RUNNING_MODE" ]; then
    if [ "$RUNNING_MODE" = "std" ]; then
        echo "Solr is already running in user-managed mode at $SOLR_URL; leaving it alone."
        exit 0
    fi
    report_wrong_mode
    exit 1
fi

export SOLR_HEAP="${SOLR_HEAP:-4g}"

# Match the tmpdir loadData.nextflow gives its Solr, so the serving instance spills large merges to
# the same place the loading instance did.
SOLR_TMP_OPTS=()
if [ -n "$OXO2_DATA" ] && mkdir -p "$OXO2_DATA/tmp" 2>/dev/null; then
    SOLR_TMP_OPTS=(-Djava.io.tmpdir="$OXO2_DATA/tmp")
fi

echo "Starting Solr (user-managed) on port $SOLR_PORT with SOLR_HEAP=$SOLR_HEAP, SOLR_HOME=$SOLR_HOME"
ulimit -n 65000 2>/dev/null || true
if ! "$SOLR_SCRIPT/solr" start --user-managed -p "$SOLR_PORT" "${SOLR_TMP_OPTS[@]}"; then
    echo "solr start failed. Check ${SOLR_LOGS_DIR:-\$SOLR_SCRIPT/../server/logs}/solr.log." >&2
    exit 1
fi

# Solr answers /admin/info/system long before its cores finish loading, so probe both — and probe
# each core's /select rather than /admin/cores?action=STATUS, which returns 200 for a core that
# failed to initialise (mirrors loadData.nextflow's wait_for_solr_core).
for attempt in {1..60}; do
    STARTED_MODE=$(solr_mode)
    [ -n "$STARTED_MODE" ] && break
    sleep 2
done
if [ -z "$STARTED_MODE" ]; then
    echo "Solr did not become ready at $SOLR_URL." >&2
    echo "Read the FIRST error in ${SOLR_LOGS_DIR:-\$SOLR_SCRIPT/../server/logs}/solr.log." >&2
    exit 1
fi
if [ "$STARTED_MODE" != "std" ]; then
    report_wrong_mode
    exit 1
fi

for core_name in "${OXO2_CORES[@]}"; do
    core_ready=false
    for attempt in {1..60}; do
        if curl -fsS -m 10 "$SOLR_URL/$core_name/select?q=*:*&rows=0" > /dev/null 2>&1; then
            core_ready=true
            break
        fi
        sleep 2
    done
    if [ "$core_ready" != true ]; then
        echo "Solr core $core_name failed to load." >&2
        curl -fsS -m 10 "$SOLR_URL/admin/cores?action=STATUS&wt=json" 2>/dev/null \
            | grep -o '"initFailures":{[^}]*}' >&2
        echo "Read the FIRST error in ${SOLR_LOGS_DIR:-\$SOLR_SCRIPT/../server/logs}/solr.log." >&2
        exit 1
    fi
    echo "Solr core $core_name is ready"
done

echo "Solr is up at $SOLR_URL. Start the backend with $SCRIPT_PATH/startBackend.sh"
