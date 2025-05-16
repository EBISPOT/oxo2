#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Check if the required arguments are provided
if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <input_file_1> <input_file_2> <output_file>"
  exit 1
fi

# Assign arguments to variables
INPUT_FILE_1=$1
INPUT_FILE_2=$2
OUTPUT_FILE=$3

# Define the path to the JAR file
JAR_FILE="./oxo2-json2rules/target/oxo2-json2rules-1.0.0-SNAPSHOT-jar-with-dependencies.jar"

# Check if the JAR file exists
if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

# Run the JSON2CSV class
#java -cp "$JAR_FILE" uk.ac.ebi.spot.oxo.json2rules.Result2InferenceToTrace -i "$INPUT_FILE" -o "$OUTPUT_FILE"
java -cp "$JAR_FILE" uk.ac.ebi.spot.oxo.inferences.nemo.Inferences2Trace -i1 "$INPUT_FILE_1" -i2 "$INPUT_FILE_2" -o "$OUTPUT_FILE"