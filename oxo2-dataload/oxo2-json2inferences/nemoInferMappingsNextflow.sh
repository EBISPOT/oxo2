#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

echo Running nemoInferMappingsNextflow.sh

# Check if the required arguments are provided
if [ "$#" -ne 4 ]; then
  echo "Usage: $0 <rules_definition> <input_file> <output_file> <export_dir>"
  exit 1
fi

# Assign arguments to variables
RULES_DEFINITION=$1
INPUT_FILE=$2
OUTPUT_FILE=$3
EXPORT_DIR=$4


nmo $RULES_DEFINITION --param importfile=\"$INPUT_FILE\" \
       --param exportfile=\"$OUTPUT_FILE\" -o -v -D $EXPORT_DIR

