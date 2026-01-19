#!/bin/bash

OXO2_INFERENCES=$OXO2_DATA/inferences
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo OXO2_INFERENCES=$OXO2_INFERENCES
echo SCRIPT_DIR=$SCRIPT_DIR

# Generate ttl from json files
echo "################### OXO2_DATA=$OXO2_DATA"
echo "################### OXO2_INFERENCES=$OXO2_INFERENCES"
echo "################### SCRIPT_DIR=$SCRIPT_DIR"
echo "################### Generating ttl from json files to $OXO2_DATA/assertedMapping.ttl"
mkdir -p $OXO2_DATA/assertedMappings
start_time=$(date +%s)
$SCRIPT_DIR/oxo2-json2inferences/json2ttl.sh $OXO2_DATA/sssom-as-json/mapping $OXO2_DATA/assertedMappings
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Making inferences: Writing ttl took: ${elapsed} seconds"


# Run nmo to get initial inferences for each asserted mapping file
# -o: overwrites existing files
# -e idb: trace idb inferences. See https://github.com/knowsys/nemo/issues/670
# -v: show progress of inferencing. See https://github.com/knowsys/nemo/issues/676
# -D: directory to export inferences to.
# See https://github.com/knowsys/nemo-examples/tree/main/examples/rdf-conversion as example externalizing import/export
echo "################### Running nmo to get initial inferences for each asserted mapping file"
mkdir -p $OXO2_INFERENCES/inferredMappings
start_time=$(date +%s)

# Iterate over all .ttl files in the assertedMappings directory
for asserted_file in $OXO2_DATA/assertedMappings/*.ttl; do
  if [ -s "$asserted_file" ]; then
    # Extract the basename (filename without path)
    asserted_basename=$(basename "$asserted_file")
    # Create corresponding inferred mapping filename
    inferred_filename="${asserted_basename}"
    inferred_file="$OXO2_INFERENCES/inferredMappings/$inferred_filename"
    
    echo "Processing $asserted_basename -> $inferred_filename"
    nmo $SCRIPT_DIR/oxo2-json2inferences/chain-rules.rls --param importfile=\"$asserted_file\" \
      --param exportfile=\"$inferred_filename\" -o -v -D $OXO2_INFERENCES/inferredMappings
  fi
done

end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Making inferences: Nemo inferencing took: ${elapsed} seconds"

# Determine mappings for which we want explanations and get explanations
# Iterate over each inferred mapping file to generate corresponding trace files
echo "################### Determining mappings for which we want explanations and generating inferencechains"
mkdir -p $OXO2_INFERENCES/inferencesToTrace
mkdir -p $OXO2_INFERENCES/inferenceChains
start_time=$(date +%s)

# Iterate over all .ttl files in the inferredMappings directory
for inferred_file in $OXO2_INFERENCES/inferredMappings/*.ttl; do
  if [ -s "$inferred_file" ]; then
    # Extract the basename (filename without path)
    inferred_basename=$(basename "$inferred_file")
    # Find corresponding asserted file by replacing "inferred" with "asserted"
    asserted_filename="${inferred_basename/inferred/asserted}"
    asserted_file="$OXO2_DATA/assertedMappings/$asserted_filename"
    
    # Create corresponding inferencesToTrace filename (replace .ttl with .txt)
    trace_filename="${inferred_basename%.ttl}.txt"
    trace_file="$OXO2_INFERENCES/inferencesToTrace/$trace_filename"
    
    # Create corresponding inferences-chains filename (replace .ttl with -chains.json)
    chains_filename="${inferred_basename%.ttl}-chains.json"
    chains_file="$OXO2_INFERENCES/inferenceChains/$chains_filename"
    
    echo "Processing $inferred_basename"
    
    # Determine mappings for which we want explanations
    echo "  Determining inferences to trace -> $trace_filename"
    $SCRIPT_DIR/oxo2-json2inferences/inferences2trace.sh "$inferred_file" "$trace_file"
    
    # Get explanations for facts to trace from nmo.
    # IDB - Intensional Database Predicates are those that appear in the head of a rule, i.e. (head <- tail)
    echo "  Generating inference chains -> $chains_filename"
    nmo $SCRIPT_DIR/oxo2-json2inferences/chain-rules.rls --param importfile=\"$asserted_file\" \
      --param exportfile=\"$inferred_basename\" -o -v -D $OXO2_INFERENCES/inferredMappings \
      --trace-input-file "$trace_file" \
      --trace-output "$chains_file"
  fi
done

end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "Making inferences: Determining inference to trace and generating chains took: ${elapsed} seconds"
