#!/bin/bash

OXO2_INFERENCES=$OXO2_DATA/inferences
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export SCRIPT_DIR

echo OXO2_INFERENCES=$OXO2_INFERENCES
echo SCRIPT_DIR=$SCRIPT_DIR

# Generate ttl from json files
mkdir -p $OXO2_DATA/assertedMappings

start_time=$(date +%s)
$SCRIPT_DIR/oxo2-json2inferences/json2ttl.sh $OXO2_DATA/sssom-as-json/mapping $OXO2_DATA/assertedMappings
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Writing ttl took: ${elapsed} seconds"

# Run nmo to get inferences for each asserted mapping file
# -o: overwrites existing files
# -e idb: trace idb inferences. See https://github.com/knowsys/nemo/issues/670
# -v: show progress of inferencing. See https://github.com/knowsys/nemo/issues/676
# -D: directory to export inferences to.
# See https://github.com/knowsys/nemo-examples/tree/main/examples/rdf-conversion as example externalizing import/export
echo "################### Running nmo to get initial inferences for each asserted mapping file"
mkdir -p $OXO2_INFERENCES/inferredMappings

start_time=$(date +%s)
$SCRIPT_DIR/oxo2-json2inferences/inferMappings.sh $OXO2_DATA/assertedMappings $OXO2_INFERENCES/inferredMappings
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Nemo inferencing took: ${elapsed} seconds"

# Determine mappings for which we want explanations and get explanations
# Iterate over each inferred mapping file to generate corresponding trace files
echo "################### Inferred mappings to inferences to trace and generate explanations as inference chains"
mkdir -p $OXO2_INFERENCES/inferencesToTrace
mkdir -p $OXO2_INFERENCES/inferenceChains

start_time=$(date +%s)
$SCRIPT_DIR/oxo2-json2inferences/traceAndExplainMappings.sh \
    $OXO2_DATA/assertedMappings \
    $OXO2_INFERENCES/inferredMappings \
    $OXO2_INFERENCES/inferencesToTrace \
    $OXO2_INFERENCES/inferenceChains
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Determining inferences to trace and generating chains took: ${elapsed} seconds"
