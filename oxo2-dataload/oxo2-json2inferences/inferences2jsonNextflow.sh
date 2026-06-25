#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

echo Running inferences2jsonNextflow.sh

# Build BARE inferred-mapping JSON from the inferred-mappings TTL (ADR-0020): no explanations.
if [ "$#" -lt 4 ]; then
  echo "Usage: $0 <inferences_ttl> <output_file> <mapping_set_output_file> <inference_type>"
  echo "  inference_type: SSSOM_INFERENCE"
  exit 1
fi

# Assign arguments to variables
INFERENCES_TTL=$1
OUTPUT_FILE=$2
MAPPING_SET_OUTPUT_FILE=$3
INFERENCE_TYPE=$4

# Define the path to the JAR file
JAR_FILE="$SCRIPT_DIR/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar"

# Check if the JAR file exists
if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

java $JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=inferences2json-heapdump.hprof -cp "$JAR_FILE" \
     uk.ac.ebi.spot.oxo.inferences.nemo.MainDispatcher inferences2json \
     -i "$INFERENCES_TTL" -f "$OUTPUT_FILE" \
     -m "$MAPPING_SET_OUTPUT_FILE" -t "$INFERENCE_TYPE"
