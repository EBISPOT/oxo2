#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

# Check if the required arguments are provided
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <input_tsv_file> <output_directory>"
  exit 1
fi

# Assign arguments to variables
INPUT_FILE=$1
OUTPUT_DIR=$2

# Define the path to the JAR file
JAR_FILE="$SCRIPT_DIR/oxo2-sssom2json/target/oxo2-sssom2json-1.0.0-SNAPSHOT.jar"

# Check if the JAR file exists
if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

# Check if input file exists
if [ ! -f "$INPUT_FILE" ]; then
  echo "Error: Input file does not exist: $INPUT_FILE"
  exit 1
fi


echo Running java $JAVA_OPTS -jar "$JAR_FILE" --inputFile "$INPUT_FILE" --outputDir "$OUTPUT_DIR"
java $JAVA_OPTS -jar "$JAR_FILE" --inputFile "$INPUT_FILE" --outputDir "$OUTPUT_DIR"

# Check if the command was successful
if [ $? -eq 0 ]; then
  echo "SSSOM2JSON processed file successfully: $INPUT_FILE"
else
  echo "SSSOM2JSON encountered an error processing file: $INPUT_FILE"
  exit 1
fi