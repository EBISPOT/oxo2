#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

SCRIPT_DIR=$(dirname $(readlink -f $0))

echo Running shardConclusionsNextflow.sh

# Partition the cross-set corpus into per-component explanation shards (ADR-0028).
if [ "$#" -lt 3 ]; then
  echo "Usage: $0 <asserted_corpus_nq> <inferences_ttl> <output_dir> [max_shard_entities]"
  exit 1
fi

CORPUS_FILE=$1
INFERENCES_TTL=$2
OUTPUT_DIR=$3
MAX_SHARD_ENTITIES=${4:-1200}

JAR_FILE="$SCRIPT_DIR/target/oxo2-json2inferences-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
  echo "Error: JAR file not found at $JAR_FILE. Please build the project first."
  exit 1
fi

java $JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=shardConclusions-heapdump.hprof -cp "$JAR_FILE" \
     uk.ac.ebi.spot.oxo.inferences.nemo.MainDispatcher shardConclusions \
     -c "$CORPUS_FILE" -i "$INFERENCES_TTL" -o "$OUTPUT_DIR" -n "$MAX_SHARD_ENTITIES"
