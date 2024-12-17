#!/bin/bash

# Check if the correct number of arguments is provided
if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <config> <download-dir> <threads>"
  exit 1
fi

# Assign input parameters to variables
CONFIG_FILE=$1
DOWNLOAD_DIR=$2
THREADS=$3

# Root directory for scripts
SCRIPT_PATH=$(dirname $(readlink -f $0))


# Run the oxo2-downloader
java -jar $SCRIPT_PATH/oxo2downloasder/target/oxo2-downloader-1.0-SNAPSHOT.jar --config $CONFIG_FILE --download-dir $DOWNLOAD_DIR --threads $THREADS

# Check if the command was successful
if [ $? -eq 0 ]; then
  echo "oxo2-downloader ran successfully."
else
  echo "oxo2-downloader encountered an error."
fi