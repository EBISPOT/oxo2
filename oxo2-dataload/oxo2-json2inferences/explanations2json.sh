#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Check if the required arguments are provided
if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <nemo_inferences> <input_directory> <output_file>"
  exit 1
fi

# Assign arguments to variables
NEMO_INFERENCES=$1
INPUT_DIRECTORY=$2
OUTPUT_FILE=$3

# Define the path to the JAR file
JAR_FILE="oxo2-json2inferences/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar"

# Check if the JAR file exists
if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

java -Xms1024m -Xmx8192m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=explanations-heapdump.hprof -cp "$JAR_FILE" \
     uk.ac.ebi.spot.oxo.inferences.nemo.MainDispatcher explanations2json -n "$NEMO_INFERENCES" -i "$INPUT_DIRECTORY" -o "$OUTPUT_FILE"
