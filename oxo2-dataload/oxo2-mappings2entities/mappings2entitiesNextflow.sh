#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

echo Running mappings2entitiesNextflow.sh

# Derive the oxo2-entities collection from the already-indexed oxo2-mappings (ADR-0034). Needs
# SOLR_URL: this reads the mappings index, it does not read the JSON on disk (the index is the only
# place asserted AND inferred mappings are both present).
#
# Two modes, mirroring the JAR:
#   list-prefixes <output_file>            the distinct CURIE prefixes, one per line
#   entities      <output_file> <prefix>   the entity documents for one prefix shard
if [ "$#" -lt 2 ]; then
  echo "Usage: $0 list-prefixes <output_file>"
  echo "       $0 entities <output_file> <prefix>"
  exit 1
fi

MODE=$1
OUTPUT_FILE=$2

JAR_FILE="$SCRIPT_DIR/target/oxo2-mappings2entities-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

case "$MODE" in
  list-prefixes)
    java $JAVA_OPTS -jar "$JAR_FILE" --list-prefixes -o "$OUTPUT_FILE"
    ;;
  entities)
    if [ "$#" -lt 3 ]; then
      echo "Usage: $0 entities <output_file> <prefix>"
      exit 1
    fi
    java $JAVA_OPTS -jar "$JAR_FILE" -p "$3" -o "$OUTPUT_FILE"
    ;;
  *)
    echo "Error: unknown mode '$MODE'. Valid modes: list-prefixes, entities"
    exit 1
    ;;
esac
