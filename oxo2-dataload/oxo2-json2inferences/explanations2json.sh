#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Check if the required arguments are provided
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <input_file> <output_file>"
  exit 1
fi

# Assign arguments to variables
INPUT_FILE=$1
OUTPUT_FILE=$2

# Define the path to the JAR file
JAR_FILE="oxo2-json2inferences/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar"

# Check if the JAR file exists
if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

java -cp "$JAR_FILE" \
     uk.ac.ebi.spot.oxo.inferences.nemo.MainDispatcher explanations2json -i "$INPUT_FILE" -o "$OUTPUT_FILE"
