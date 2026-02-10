#!/bin/bash

# Sequential processing: for each inferred .ttl, determine inferences to trace
# then run nmo with trace options to generate explanation chains.
# Usage: traceAndExplainMappings.sh <asserted_dir> <inferred_dir> <trace_dir> <chains_dir>

ASSERTED_DIR=$1
INFERRED_DIR=$2
TRACE_DIR=$3
CHAINS_DIR=$4
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for inferred_file in $INFERRED_DIR/*.ttl; do
    if [ -s "$inferred_file" ]; then
        inferred_basename=$(basename "$inferred_file")
        asserted_filename="${inferred_basename/inferred/asserted}"
        asserted_file="$ASSERTED_DIR/$asserted_filename"

        trace_filename="${inferred_basename%.ttl}.txt"
        trace_file="$TRACE_DIR/$trace_filename"

        chains_filename="${inferred_basename%.ttl}-chains.json"
        chains_file="$CHAINS_DIR/$chains_filename"

        echo "Processing $inferred_basename"

        echo "  Determining inferences to trace -> $trace_filename"
        $SCRIPT_DIR/inferences2trace.sh "$inferred_file" "$trace_file"

        echo "  Generating inference chains -> $chains_filename"
        nmo $SCRIPT_DIR/chain-rules.rls --param importfile=\"$asserted_file\" \
            --param exportfile=\"$inferred_basename\" -o -v -D $INFERRED_DIR \
            --trace-input-file "$trace_file" \
            --trace-output "$chains_file"
    fi
done
