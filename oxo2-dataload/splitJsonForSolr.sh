#!/bin/bash

# Script to split a large JSON array file into multiple smaller JSON array files
# Usage: splitJsonForSolr.sh <input_file> <output_directory> [chunk_size]
#   input_file: The large JSON array file to split
#   output_directory: Directory where split files will be written
#   chunk_size: Number of items per chunk (default: 10000)

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <input_file> <output_directory> [chunk_size]"
  echo "  chunk_size defaults to 10000 if not specified"
  exit 1
fi

INPUT_FILE=$1
OUTPUT_DIR=$2
CHUNK_SIZE=${3:-10000}

if [ ! -f "$INPUT_FILE" ]; then
  echo "Error: Input file '$INPUT_FILE' does not exist"
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

# Check if jq is available
if command -v jq &> /dev/null; then
  echo "Using jq to split JSON file..."
  
  # Get total number of items in the array
  TOTAL_ITEMS=$(jq 'length' "$INPUT_FILE")
  echo "Total items in JSON array: $TOTAL_ITEMS"
  
  # Calculate number of chunks needed
  NUM_CHUNKS=$(( (TOTAL_ITEMS + CHUNK_SIZE - 1) / CHUNK_SIZE ))
  echo "Splitting into $NUM_CHUNKS chunks of up to $CHUNK_SIZE items each..."
  
  # Split the array
  for ((i=0; i<NUM_CHUNKS; i++)); do
    START=$((i * CHUNK_SIZE))
    END=$((START + CHUNK_SIZE))
    OUTPUT_FILE="$OUTPUT_DIR/inferred-mappings-part-$(printf "%04d" $i).json"
    
    echo "Creating chunk $((i+1))/$NUM_CHUNKS: $OUTPUT_FILE (items $START to $((END-1)))"
    jq ".[$START:$END]" "$INPUT_FILE" > "$OUTPUT_FILE"
  done
  
  echo "Successfully split JSON file into $NUM_CHUNKS chunks"

  echo "Successfully split JSON file into $NUM_CHUNKS chunks"
  
elif command -v python3 &> /dev/null; then
  echo "Using Python to split JSON file..."
  
  python3 << EOF
import json
import os
import sys

input_file = "$INPUT_FILE"
output_dir = "$OUTPUT_DIR"
chunk_size = $CHUNK_SIZE

# Read the input JSON file
with open(input_file, 'r') as f:
    data = json.load(f)

if not isinstance(data, list):
    print(f"Error: Input file does not contain a JSON array", file=sys.stderr)
    sys.exit(1)

total_items = len(data)
print(f"Total items in JSON array: {total_items}")

# Calculate number of chunks
num_chunks = (total_items + chunk_size - 1) // chunk_size
print(f"Splitting into {num_chunks} chunks of up to {chunk_size} items each...")

# Split the array
for i in range(num_chunks):
    start = i * chunk_size
    end = min(start + chunk_size, total_items)
    output_file = os.path.join(output_dir, f"inferred-mappings-part-{i:04d}.json")
    
    print(f"Creating chunk {i+1}/{num_chunks}: {output_file} (items {start} to {end-1})")
    
    chunk = data[start:end]
    with open(output_file, 'w') as f:
        json.dump(chunk, f)

print(f"Successfully split JSON file into {num_chunks} chunks")
EOF

else
  echo "Error: Neither 'jq' nor 'python3' is available. Please install one of them."
  exit 1
fi  