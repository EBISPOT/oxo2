#!/bin/bash

set -euo pipefail

fix_owner() {
    local dir=$1
    mkdir -p "$dir"

    if [ "$(stat -c '%u:%g' "$dir")" != "8389:8389" ]; then
        chown -R oxo:oxo "$dir"
    fi
}

fix_owner /mnt/oxo/data
fix_owner /mnt/oxo/logs
fix_owner /opt/solr/server/solr

exec setpriv --reuid=8389 --regid=8389 --init-groups "$@"
