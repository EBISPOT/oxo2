#!/bin/bash

# Sequential processing of all .ttl files through nmo to generate inferred mappings.
# Usage: inferMappings.sh <asserted_mappings_dir> <inferred_mappings_dir>

ASSERTED_MAPPINGS_DIR=$1
INFERRED_MAPPINGS_DIR=$2
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for asserted_file in $ASSERTED_MAPPINGS_DIR/*.ttl; do
    if [ -s "$asserted_file" ]; then
        asserted_basename=$(basename "$asserted_file")
        inferred_filename="${asserted_basename}"

        echo "Processing $asserted_basename -> $inferred_filename"
        nmo $SCRIPT_DIR/chain-rules.rls --param importfile=\"$asserted_file\" \
            --param exportfile=\"$inferred_filename\" -o -v -D $INFERRED_MAPPINGS_DIR
    fi
done
