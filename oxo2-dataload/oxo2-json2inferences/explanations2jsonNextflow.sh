#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

echo Running explanations2jsonNextflow.sh

# Check if the required arguments are provided
if [ "$#" -lt 5 ]; then
  echo "Usage: $0 <input_file> <output_file> <mapping_set_output_file> <inference_type> <cross_set> [source_mapping_set_id]"
  echo "  inference_type: SSSOM_INFERENCE"
  echo "  cross_set: true | false  (when true, source_mapping_set_id is not required)"
  exit 1
fi

# Assign arguments to variables
INPUT_FILE=$1
OUTPUT_FILE=$2
MAPPING_SET_OUTPUT_FILE=$3
INFERENCE_TYPE=$4
CROSS_SET=$5
SOURCE_MAPPING_SET_ID=$6

# Define the path to the JAR file
JAR_FILE="$SCRIPT_DIR/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar"

# Check if the JAR file exists
if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

# Cross-set inference lands every inference in the single oxo2/inferences set and recovers the
# source-set union from per-leaf mapping_id, so it takes -x instead of a single -s source.
EXTRA_ARGS=(-t "$INFERENCE_TYPE")
if [ "$CROSS_SET" = "true" ]; then
  EXTRA_ARGS+=(-x)
else
  EXTRA_ARGS+=(-s "$SOURCE_MAPPING_SET_ID")
fi

java $JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=explanations-heapdump.hprof -cp "$JAR_FILE" \
     uk.ac.ebi.spot.oxo.inferences.nemo.MainDispatcher explanations2json \
     -i "$INPUT_FILE" -f "$OUTPUT_FILE" \
     -m "$MAPPING_SET_OUTPUT_FILE" "${EXTRA_ARGS[@]}"
