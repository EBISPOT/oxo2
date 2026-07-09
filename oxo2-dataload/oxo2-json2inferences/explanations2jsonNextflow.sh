#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

echo Running explanations2jsonNextflow.sh

# Turn a BUNDLE of per-shard nmo trace files into inferred-mapping JSON carrying the explanation
# chain, asserted evidence and explanation_length (ADR-0028). Needs SOLR_URL: entity CURIEs/labels
# and the asserted premises are resolved from the already-indexed asserted mappings.
if [ "$#" -lt 4 ]; then
  echo "Usage: $0 <output_file> <mapping_set_output_file> <inference_type> <trace_file> [trace_file...]"
  echo "  inference_type: SSSOM_INFERENCE"
  exit 1
fi

OUTPUT_FILE=$1
MAPPING_SET_OUTPUT_FILE=$2
INFERENCE_TYPE=$3
shift 3
# Remaining arguments are the bundle's trace files.

JAR_FILE="$SCRIPT_DIR/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

# -x (crossSet): every inference lands in the single https://www.ebi.ac.uk/oxo2/inferences set, whose
# mapping_set_source is the union of the sets that contributed an asserted premise. Each bundle sees
# only its own shards, so this union is PARTIAL and mergeInferredMappingSets unions them afterwards.
java $JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=explanations2json-heapdump.hprof -cp "$JAR_FILE" \
     uk.ac.ebi.spot.oxo.inferences.nemo.MainDispatcher explanations2json \
     -f "$OUTPUT_FILE" -m "$MAPPING_SET_OUTPUT_FILE" -t "$INFERENCE_TYPE" -x \
     -i "$@"
