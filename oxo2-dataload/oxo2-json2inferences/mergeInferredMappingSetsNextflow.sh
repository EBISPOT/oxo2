#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

echo Running mergeInferredMappingSetsNextflow.sh

# Union the per-bundle inferred MappingSet source sets into the single cross-set inferred set.
if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <output_file> <mapping_set_json> [mapping_set_json...]"
  exit 1
fi

OUTPUT_FILE=$1
shift 1

JAR_FILE="$SCRIPT_DIR/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

java $JAVA_OPTS -cp "$JAR_FILE" \
     uk.ac.ebi.spot.oxo.inferences.nemo.MainDispatcher mergeInferredMappingSets \
     -o "$OUTPUT_FILE" -i "$@"
