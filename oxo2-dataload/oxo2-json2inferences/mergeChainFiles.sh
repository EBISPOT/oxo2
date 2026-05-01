#!/bin/bash
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

# Merge per-chunk Nemo chain trace JSON files into one per-mapping-set chain file.
# Usage: mergeChainFiles.sh <output_file> <input_file1> [input_file2] ...

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <output_file> <input_file1> [input_file2] ..."
  exit 1
fi

OUTPUT_FILE=$1
shift

JAR_FILE="$SCRIPT_DIR/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

java -cp "$JAR_FILE" \
     uk.ac.ebi.spot.oxo.inferences.nemo.MainDispatcher mergeChainFiles \
     -o "$OUTPUT_FILE" -i "$@"
