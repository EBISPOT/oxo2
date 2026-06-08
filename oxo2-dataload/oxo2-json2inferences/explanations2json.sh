#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

# Check if the required arguments are provided
if [ "$#" -ne 4 ]; then
  echo "Usage: $0 <nemo_inferences_dir> <mapping_output_dir> <mapping_set_output_dir> <mapping_set_json_dir>"
  echo "  nemo_inferences_dir   : directory of *-chains.json files"
  echo "  mapping_output_dir    : where to write inferred-mapping JSON files (e.g. inferences/solr/mapping)"
  echo "  mapping_set_output_dir: where to write inferred-MappingSet JSON files (e.g. inferences/solr/mappingSet)"
  echo "  mapping_set_json_dir  : sssom-as-json mappingSet directory used to resolve source mapping set IDs"
  exit 1
fi

# Assign arguments to variables
NEMO_INFERENCES_DIR=$1
OUTPUT_DIR=$2
MAPPING_SET_OUTPUT_DIR=$3
MAPPING_SET_JSON_DIR=$4

# Define the path to the JAR file
JAR_FILE="$SCRIPT_DIR/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar"

# Check if the JAR file exists
if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

# Directory mode is the per-set phase 1 (OWL) batch path; stamp OWL_INFERENCE accordingly.
java $JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=explanations-heapdump.hprof -cp "$JAR_FILE" \
     uk.ac.ebi.spot.oxo.inferences.nemo.MainDispatcher explanations2json \
     -n "$NEMO_INFERENCES_DIR" -o "$OUTPUT_DIR" \
     -q "$MAPPING_SET_OUTPUT_DIR" -d "$MAPPING_SET_JSON_DIR" \
     -t OWL_INFERENCE
