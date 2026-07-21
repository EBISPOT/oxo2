#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

echo Running json2nquadsNextflow.sh

# Check if the required arguments are provided
if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "Usage: $0 <input_file> <output_file> [min_confidence]"
  exit 1
fi

# Assign arguments to variables
INPUT_FILE=$1
OUTPUT_FILE=$2
# Confidence gate threshold (ADR-0037). Absent/0 disables it. Passed straight to JSON2NQuads -c.
MIN_CONFIDENCE=${3:-0.0}

# Define the path to the JAR file
JAR_FILE="$SCRIPT_DIR/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar"

# Check if the JAR file exists
if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

java -cp "$JAR_FILE" \
     uk.ac.ebi.spot.oxo.inferences.nemo.MainDispatcher json2nquads -f "$INPUT_FILE" -p "$OUTPUT_FILE" -c "$MIN_CONFIDENCE"
