#!/bin/bash

# Check if the correct number of arguments is provided
if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <inputDir> <outputDir>"
  exit 1
fi

# Assign input parameters to variables
INPUT_DIR=$1
OUTPUT_DIR=$2

# Root directory for scripts
SCRIPT_PATH=$(dirname $(readlink -f $0))

# Run the SSSOM2JSON
java -jar $SCRIPT_PATH/oxo2-sssom2json/target/sssom2json-1.0-SNAPSHOT.jar -i $INPUT_DIR -o $OUTPUT_DIR

# Check if the command was successful
if [ $? -eq 0 ]; then
  echo "SSSOM2JSON ran successfully."
else
  echo "SSSOM2JSON encountered an error."
fi