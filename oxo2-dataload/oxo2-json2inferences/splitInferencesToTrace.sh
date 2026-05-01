#!/bin/bash
set -e

# Split a ;-separated trace input file (as produced by inferences2trace) into chunks of
# at most CHUNK_SIZE mappings each. Each chunk is written as its own ;-separated single-line
# file, format-identical to the input, so nmo --trace-input-file consumes it unchanged.
#
# Usage: splitInferencesToTrace.sh <input_file> <chunk_size> <output_prefix>
# Output files: <output_prefix>-chunk0000.txt, <output_prefix>-chunk0001.txt, ...

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <input_file> <chunk_size> <output_prefix>"
  exit 1
fi

INPUT_FILE=$1
CHUNK_SIZE=$2
OUTPUT_PREFIX=$3

if [ ! -s "$INPUT_FILE" ]; then
  echo "Input file empty or missing: $INPUT_FILE"
  exit 0
fi

# tr converts the single ;-separated line into one mapping per line; split partitions into
# chunks of CHUNK_SIZE lines; per-chunk re-join restores the ;-separated form.
TMP_PREFIX="${OUTPUT_PREFIX}-chunk-tmp"
tr ';' '\n' < "$INPUT_FILE" | split -l "$CHUNK_SIZE" -d -a 4 - "$TMP_PREFIX"

for f in "$TMP_PREFIX"*; do
    suffix="${f##"$TMP_PREFIX"}"
    out="${OUTPUT_PREFIX}-chunk${suffix}.txt"
    tr '\n' ';' < "$f" | sed 's/;$//' > "$out"
    rm "$f"
    if [ ! -s "$out" ]; then
        rm -f "$out"
    fi
done
